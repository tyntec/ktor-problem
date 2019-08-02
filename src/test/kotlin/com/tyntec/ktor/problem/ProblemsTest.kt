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

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.tyntec.ktor.problem.gson.gson
import com.tyntec.ktor.problem.jackson.jackson
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test

class RFC7807ProblemsShould {

    val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    internal fun `respond with internal server error by default`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {  }
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        handleRequest(HttpMethod.Get, "/error") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("/error")
            assertThat(content.get("status").intValue()).isEqualTo(500)
            assertThat(content.get("title").textValue()).isEqualTo("Internal Server Error")
        }
    }

    @Test
    internal fun `respond with configured error object`() = withTestApplication{
        application.install(RFC7807Problems) {
            exception<IllegalAccessError> { ctx ->
                statusCode = HttpStatusCode.MethodNotAllowed
                detail = "You're not allowed to trigger this action"
                instance = "bad resource"
                type = "Test-DefaultProblem"
            }
            jackson {  }
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
            get("/definedError") {
                throw IllegalAccessError()
            }
        }

        handleRequest(HttpMethod.Get, "/definedError") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.MethodNotAllowed)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("bad resource")
            assertThat(content.get("status").intValue()).isEqualTo(405)
            assertThat(content.get("title").textValue()).isEqualTo("Method Not Allowed")
            assertThat(content.get("type").textValue()).isEqualTo("Test-DefaultProblem")
            assertThat(content.get("detail").textValue()).isEqualTo("You're not allowed to trigger this action")
        }
    }

    @Test
    internal fun `respond with customized default`() = withTestApplication{
        application.install(RFC7807Problems) {
            default {ctx ->
                statusCode = HttpStatusCode.PaymentRequired
                instance = ctx.call.request.path()
            }
            jackson {  }
        }

        application.routing {
            get("/customizedError") {
                throw IllegalStateException()
            }
        }

        handleRequest(HttpMethod.Get, "/customizedError") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.PaymentRequired)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("/customizedError")
            assertThat(content.get("status").intValue()).isEqualTo(402)
            assertThat(content.get("title").textValue()).isEqualTo("Payment Required")
        }
    }

    @Test
    internal fun `respond with throwable problem implementation`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {  }
        }

        application.routing {
            get("/customizedError") {
                throw ProblemToBeThrown()
            }
        }

        handleRequest(HttpMethod.Get, "/customizedError") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("status").intValue()).isEqualTo(400)
            assertThat(content.get("title").textValue()).isEqualTo("Awesome title")
        }
    }

    @Test
    internal fun `use jackson override`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {
                propertyNamingStrategy = PropertyNamingStrategy.UPPER_CAMEL_CASE
            }
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        handleRequest(HttpMethod.Get, "/error") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("Instance").textValue()).isEqualTo("/error")
            assertThat(content.get("Status").intValue()).isEqualTo(500)
            assertThat(content.get("Title").textValue()).isEqualTo("Internal Server Error")
        }
    }

    @Test
    internal fun `respond with a 404 problem on unknown paths`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {}
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        handleRequest(HttpMethod.Get, "/i-do-no-exist") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.NotFound)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("/i-do-no-exist")
            assertThat(content.get("status").intValue()).isEqualTo(404)
            assertThat(content.get("title").textValue()).isEqualTo("Not Found")
        }
    }

    @Test
    internal fun `respond with a problem matching the error response status`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {}
        }

        application.routing {
            get("/error") {
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }

        handleRequest(HttpMethod.Get, "/error") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.MethodNotAllowed)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("/error")
            assertThat(content.get("status").intValue()).isEqualTo(405)
            assertThat(content.get("title").textValue()).isEqualTo("Method Not Allowed")
        }
    }

    @Test
    internal fun `ignore all non error response codes`() = withTestApplication{
        application.install(RFC7807Problems) {
            jackson {}
        }

        application.routing {
            get("/redirect") {
                call.respond(HttpStatusCode.PermanentRedirect, "Hello world")
            }
        }

        handleRequest(HttpMethod.Get, "/redirect") {

        }.response.let {response ->

            val content = response.content!!

            assertThat(response.status()).isEqualTo(HttpStatusCode.PermanentRedirect)
            assertThat(content).isEqualTo("Hello world")
        }
    }

    @Test
    internal fun `use gson`() = withTestApplication{
        application.install(RFC7807Problems) {
            gson{}
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        handleRequest(HttpMethod.Get, "/error") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)
            println(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("instance").textValue()).isEqualTo("/error")
            assertThat(content.get("status").intValue()).isEqualTo(500)
            assertThat(content.get("title").textValue()).isEqualTo("Internal Server Error")
        }
    }

    @Test
    internal fun `use custom problem converter`() = withTestApplication{
        application.install(RFC7807Problems) {
            converter(TestProblemConverter())
        }

        application.routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        handleRequest(HttpMethod.Get, "/error") {

        }.response.let {response ->

            val content = objectMapper.readTree(response.content)
            println(response.content)

            assertThat(response.status()).isEqualTo(HttpStatusCode.InternalServerError)
            assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
            assertThat(content.get("my_title").textValue()).isEqualTo("is testie")
        }
    }

}

class TestProblemConverter : ProblemConverter {
    override fun convert(problem: Any): String {
        return """
            {
              "my_title" : "is testie"
            }
        """.trimIndent()
    }

}

class ProblemToBeThrown(
    override var type: String? = "Any type",
    override var statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
    override var detail: String? = "DefaultProblem to be thrown",
    override var instance: String? = null,
    override var additionalDetails: Map<String, Any> = emptyMap(),
    override var title: String? = "Awesome title",
    override var status: Int? = null
) : Problem, Throwable()