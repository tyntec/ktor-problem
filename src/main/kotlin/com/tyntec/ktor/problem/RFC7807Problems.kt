/*
 *    Copyright 2019 tyntec
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.tyntec.ktor.problem

import com.tyntec.ktor.problem.ExceptionLogLevel.*

import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext

private val problemContentType = ContentType("application", "problem+json")

enum class ExceptionLogLevel {
    OFF,
    SHORT,
    FULL
}

class RFC7807Problems(configuration: Configuration) {
    private val exceptions = configuration.exceptions
    private val default = configuration.default
    private val converter = configuration.problemConverter
    private val notFound = configuration.notFound
    private val enableAutomaticResponseConversion = configuration.enableAutomaticResponseConversion
    private val exceptionLogging = configuration.exceptionLogging

    class Configuration {

        private class DefaultProblemConverter : ProblemConverter {
            override fun convert(problem: Any): String {
                throw NoConverterConfiguredException()
            }
        }

        internal var problemConverter: ProblemConverter = DefaultProblemConverter()

        internal val exceptions = mutableMapOf<Class<*>, Problem.(ProblemContext<Throwable>) -> Unit>()


        var exceptionLogging = FULL
        var enableAutomaticResponseConversion = true

        internal var default: Problem.(ProblemContext<Throwable>) -> Unit = { ctx ->
            instance = ctx.call.request.path()
            statusCode = HttpStatusCode.InternalServerError
        }

        internal var notFound: Problem.(ProblemContext<Throwable>) -> Unit = { ctx ->
            instance = ctx.call.request.path()
            statusCode = HttpStatusCode.NotFound
        }


        fun default(handler: Problem.(ProblemContext<Throwable>) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            default = handler
        }

        fun notFound(handler: Problem.(ProblemContext<Throwable>) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            notFound = handler
        }

        inline fun <reified T : Throwable> exception(
            noinline handler: Problem.(ProblemContext<T>) -> Unit
        ) = exception(T::class.java, handler)

        fun <T : Throwable> exception(
            klass: Class<T>,
            handler: Problem.(ProblemContext<T>) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            exceptions.put(klass, handler as Problem.(ProblemContext<Throwable>) -> Unit)
        }

        fun converter(problemConverter: ProblemConverter) {
            this.problemConverter = problemConverter
        }
    }

    companion object Feature : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, RFC7807Problems> {
        override val key = AttributeKey<RFC7807Problems>("Problems")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): RFC7807Problems {

            val configuration = Configuration().apply(configure)

            val plugin = RFC7807Problems(configuration)

            if (plugin.enableAutomaticResponseConversion)
                pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
                    plugin.interceptResponse(this, message)
                }

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    plugin.interceptExceptions(this, call, e)
                }
            }
            pipeline.intercept(ApplicationCallPipeline.Fallback) {
                plugin.notFound(call)
            }
            return plugin
        }
    }

    private suspend fun interceptResponse(context: PipelineContext<*, ApplicationCall>, message: Any) {
        val call = context.call
        if (call.attributes.contains(RFC7807Problems.key)) return

        val status = when (message) {
            is OutgoingContent -> message.status
            is HttpStatusCode -> message
            else -> null
        }

        if (status.isSetAndError()) {
            val problem = DefaultProblem(statusCode = status!!, instance = call.request.path())
            finalizeProblem(problem)
            call.application.attributes.put(key, this@RFC7807Problems)
            call.respond(problem.statusCode, TextContent(converter.convert(problem), problemContentType))
            finishIfResponseSent(context)
        }
    }

    private suspend fun interceptExceptions(
        context: PipelineContext<*, ApplicationCall>,
        call: ApplicationCall,
        throwable: Throwable
    ) {
        val logger = call.application.log
        when (exceptionLogging) {
            FULL -> logger.warn(
                "While executing {} {} this exception occurred",
                call.request.httpMethod.value,
                call.request.path(),
                throwable
            )
            SHORT -> logger.warn(
                "While executing {} {} this exception occurred",
                call.request.httpMethod.value,
                call.request.path(),
                throwable.message
            )
            OFF -> {
            }
        }

        val problem = when (throwable) {
            is Problem -> throwable
            else -> {
                DefaultProblem().apply {
                    (findExceptionByClass(throwable::class.java))(ProblemContext(call, throwable))
                }
            }
        }
        finalizeProblem(problem)
        call.application.attributes.put(key, this@RFC7807Problems)
        call.respond(problem.statusCode, TextContent(converter.convert(problem), problemContentType))
        finishIfResponseSent(context)
    }

    private fun finishIfResponseSent(context: PipelineContext<*, ApplicationCall>) {
        if (context.call.response.status() != null) {
            context.finish()
        }
    }

    private fun finalizeProblem(problem: Problem) {
        with(problem) {
            if (status == null) {
                status = statusCode.value
            }
            if (title.isNullOrEmpty()) {
                title = statusCode.description
            }
        }
    }

    private suspend fun notFound(call: ApplicationCall) {
        if (!call.application.attributes.contains(key) && call.response.status() == null) {
            val problem = DefaultProblem().apply { notFound(ProblemContext(call, Throwable())) }
            finalizeProblem(problem)
            call.respond(
                HttpStatusCode.NotFound,
                TextContent(converter.convert(problem), ContentType.parse("application/problem+json"))
            )
        }
    }

    private fun findExceptionByClass(clazz: Class<out Throwable>): (Problem.(ProblemContext<Throwable>) -> Unit) {
        exceptions[clazz]?.let { return it }
        return default
    }

}

fun HttpStatusCode?.isSetAndError(): Boolean {
    return !(this == null || this.value < 400)
}

class NoConverterConfiguredException : Throwable()

interface ProblemConverter {
    fun convert(problem: Any): String
}

interface Problem {
    var type: String?
    var statusCode: HttpStatusCode
    var detail: String?
    var instance: String?
    var additionalDetails: Map<String, Any>
    var status: Int?
    var title: String?
}

class DefaultProblem(
    override var type: String? = null,
    override var statusCode: HttpStatusCode = HttpStatusCode.InternalServerError,
    override var detail: String? = null,
    override var instance: String? = null,
    override var additionalDetails: Map<String, Any> = emptyMap(),
    override var status: Int? = null,
    override var title: String? = null
) : Problem {
    override fun toString(): String {
        return "DefaultProblem(type=$type, statusCode=$statusCode, detail=$detail, instance=$instance, additionalDetails=$additionalDetails, status=$status, title=$title)"
    }
}

open class ThrowableProblem(
    override var type: String? = null,
    override var statusCode: HttpStatusCode = HttpStatusCode.InternalServerError,
    override var detail: String? = null,
    override var instance: String? = null,
    override var additionalDetails: Map<String, Any> = emptyMap(),
    override var title: String? = null,
    override var status: Int? = null
) : Problem, Throwable()
