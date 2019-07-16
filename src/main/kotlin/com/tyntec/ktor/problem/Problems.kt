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

import com.fasterxml.jackson.databind.ObjectMapper
import com.tyntec.ktor.problem.jackson.ProblemMixin
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.util.AttributeKey

private val problemContentType = ContentType("application", "problem+json")

class Problems(configuration: Configuration) {
    private val exceptions = configuration.exceptions
    private val default = configuration.default
    private val objectMapper = configuration.mapper

    class Configuration {

        internal var mapper = ObjectMapper().findAndRegisterModules().addMixIn(Problem::class.java, ProblemMixin::class.java)

        internal val exceptions = mutableMapOf<Class<*>, Problem.(ProblemContext<Throwable>) -> Unit>()

        internal var default: Problem.(ProblemContext<Throwable>) -> Unit = { ctx ->
            instance = ctx.call.request.path()
            statusCode = HttpStatusCode.InternalServerError
        }

        fun default(handler: Problem.(ProblemContext<Throwable>) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            default = handler
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

        fun jackson(block: ObjectMapper.() -> Unit) {
            mapper.apply(block)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Problems> {
        override val key = AttributeKey<Problems>("Problems")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Problems {

            val configuration = Configuration().apply(configure)

            val feature = Problems(configuration)

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.intercept(call, e)
                }
            }
            return feature
        }
    }

    private suspend fun intercept(call: ApplicationCall, throwable: Throwable) {
        val problem = when (throwable) {
            is Problem -> throwable
            else -> {
                DefaultProblem().apply {
                    (findExceptionByClass(throwable::class.java))(ProblemContext(call, throwable))
                }
            }
        }
        val content = objectMapper.writeValueAsString(problem)
        call.respond(problem.statusCode, TextContent(content, problemContentType))
    }

    private fun findExceptionByClass(clazz: Class<out Throwable>): (Problem.(ProblemContext<Throwable>) -> Unit) {
        exceptions[clazz]?.let { return it }
        return default
    }

}

interface Problem {
    var type: String?
    var statusCode: HttpStatusCode
    var detail: String?
    var instance: String?
    var additionalDetails: Map<String, Any>
    val status: Int
        get() = statusCode.value

    var title: String?
}

class DefaultProblem(
    override var type: String? = null,
    override var statusCode: HttpStatusCode = HttpStatusCode.InternalServerError,
    override var detail: String? = null,
    override var instance: String? = null,
    override var additionalDetails: Map<String, Any> = emptyMap()
) : Problem {
    override var title: String? = null
        get() = if (field.isNullOrEmpty()) statusCode.description else field

}

