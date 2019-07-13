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

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
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

class Problems(configuration: Configuration) {
    private val exceptions = configuration.exceptions
    private val default = configuration.default
    private val objectMapper = ObjectMapper().findAndRegisterModules().apply { configuration.jacksonConfig }

    class Configuration {

        internal var jacksonConfig: ObjectMapper.() -> Unit = {}

        internal val exceptions = mutableMapOf<Class<*>, ThrowableProblem.(ProblemContext<Throwable>) -> Unit>()

        internal var default: ThrowableProblem.(ProblemContext<Throwable>) -> Unit = { ctx ->
            instance = ctx.call.request.path()
            statusCode = HttpStatusCode.InternalServerError
        }

        fun default(handler: ThrowableProblem.(ProblemContext<Throwable>) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            default = handler
        }

        inline fun <reified T : Throwable> exception(
            noinline handler: ThrowableProblem.(ProblemContext<T>) -> Unit
        ) = exception(T::class.java, handler)

        fun <T : Throwable> exception(
            klass: Class<T>,
            handler: ThrowableProblem.(ProblemContext<T>) -> Unit
        ) {
            @Suppress("UNCHECKED_CAST")
            exceptions.put(klass, handler as ThrowableProblem.(ProblemContext<Throwable>) -> Unit)
        }

        fun jackson(block: ObjectMapper.() -> Unit) {
            jacksonConfig = block
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

    private suspend fun intercept(call: ApplicationCall, e: Throwable) {
        val problem: ThrowableProblem = Problem()
        findExceptionByClass(e::class.java).invoke(problem, ProblemContext(call, e))
        val message = TextContent(objectMapper.writeValueAsString(problem), ContentType("application", "problem+json"))
        call.respond(problem.statusCode, message)
    }

    private fun findExceptionByClass(clazz: Class<out Throwable>): (ThrowableProblem.(ProblemContext<Throwable>) -> Unit) {
        exceptions[clazz]?.let { return it }
        if (clazz is ThrowableProblem)
            return { _ ->
                statusCode = clazz.statusCode
                additionalDetails = clazz.additionalDetails
                instance = clazz.instance
                title = clazz.title
                type = clazz.type
            }
        return default
    }

}

interface ThrowableProblem {
    var type: String?
    @get:JsonIgnore
    var statusCode: HttpStatusCode
    var detail: String?
    var instance: String?
    @get:JsonAnyGetter
    var additionalDetails: Map<String, Any>
    val status: Int
        get() = statusCode.value

    var title: String?
}

class Problem(
    override var type: String? = null,
    override var statusCode: HttpStatusCode = HttpStatusCode.InternalServerError,
    override var detail: String? = null,
    override var instance: String? = null,
    override var additionalDetails: Map<String, Any> = emptyMap()
) : ThrowableProblem {
    override var title: String? = null
        get() {
            if (field.isNullOrEmpty())
                return statusCode.description
            return field
        }
}

