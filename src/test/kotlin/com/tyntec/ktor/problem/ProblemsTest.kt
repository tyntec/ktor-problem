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
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test

class RFC7807ProblemsShould {

    val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    internal fun `respond with internal server error by default`() = testApplication {
        install(RFC7807Problems) {
            jackson {  }
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        val response = client.get("/error")
        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("/error")
        assertThat(content.get("status").intValue()).isEqualTo(500)
        assertThat(content.get("title").textValue()).isEqualTo("Internal Server Error")
    }

    @Test
    internal fun `respond with configured error object`() = testApplication {
        install(RFC7807Problems) {
            exception<IllegalAccessError> { ctx ->
                statusCode = HttpStatusCode.MethodNotAllowed
                detail = "You're not allowed to trigger this action"
                instance = "bad resource"
                type = "Test-DefaultProblem"
            }
            jackson {  }
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
            get("/definedError") {
                throw IllegalAccessError()
            }
        }

        val response = client.get("/definedError")
        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("bad resource")
        assertThat(content.get("status").intValue()).isEqualTo(405)
        assertThat(content.get("title").textValue()).isEqualTo("Method Not Allowed")
        assertThat(content.get("type").textValue()).isEqualTo("Test-DefaultProblem")
        assertThat(content.get("detail").textValue()).isEqualTo("You're not allowed to trigger this action")
    }

    @Test
    internal fun `respond with customized default`() = testApplication {
        install(RFC7807Problems) {
            default {ctx ->
                statusCode = HttpStatusCode.PaymentRequired
                instance = ctx.call.request.path()
            }
            jackson {  }
        }

        routing {
            get("/customizedError") {
                throw IllegalStateException()
            }
        }

        val response = client.get("/customizedError")

        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.PaymentRequired)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("/customizedError")
        assertThat(content.get("status").intValue()).isEqualTo(402)
        assertThat(content.get("title").textValue()).isEqualTo("Payment Required")
    }

    @Test
    internal fun `respond with throwable problem implementation`() = testApplication {
        install(RFC7807Problems) {
            jackson {  }
        }

        routing {
            get("/customizedError") {
                throw TestBusinessException(businessDetail = "a test detail")
            }
        }

        val response = client.get("/customizedError")

        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("status").intValue()).isEqualTo(400)
        assertThat(content.get("title").textValue()).isEqualTo("Awesome title")
        assertThat(content.get("businessDetail").textValue()).isEqualTo("a test detail")
    }

    @Test
    internal fun `use jackson override`() = testApplication {
        install(RFC7807Problems) {
            jackson {
                propertyNamingStrategy = PropertyNamingStrategy.UPPER_CAMEL_CASE
            }
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        val response = client.get("/error")

        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("Instance").textValue()).isEqualTo("/error")
        assertThat(content.get("Status").intValue()).isEqualTo(500)
        assertThat(content.get("Title").textValue()).isEqualTo("Internal Server Error")
    }

    @Test
    internal fun `respond with a 404 problem on unknown paths`() = testApplication {
        install(RFC7807Problems) {
            jackson {}
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        val response = client.get("/i-do-no-exist")

        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("/i-do-no-exist")
        assertThat(content.get("status").intValue()).isEqualTo(404)
        assertThat(content.get("title").textValue()).isEqualTo("Not Found")
    }

    @Test
    internal fun `respond with a problem matching the error response status`() = testApplication {
        install(RFC7807Problems) {
            jackson {}
        }

        routing {
            get("/error") {
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }

        val response = client.get("/error")

        val content = objectMapper.readTree(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("/error")
        assertThat(content.get("status").intValue()).isEqualTo(405)
        assertThat(content.get("title").textValue()).isEqualTo("Method Not Allowed")
    }

    @Test
    internal fun `respond with unmodified response when enableAutomaticResponseConversion is disabled`() = testApplication {
        install(RFC7807Problems) {
            jackson {}
            enableAutomaticResponseConversion = false
        }

        routing {
            get("/error") {
                call.respond(TextContent("test", ContentType.parse("application/my-problem") , HttpStatusCode.MethodNotAllowed))
            }
        }

        val response = client.get("/error")
        assertThat(response.body<String>()).isEqualTo("test")
        assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "my-problem"))

    }

    @Test
    internal fun `ignore all non error response codes`() = testApplication {
        install(RFC7807Problems) {
            jackson {}
        }

        routing {
            get("/redirect") {
                call.respond(HttpStatusCode.PermanentRedirect, "Hello world")
            }
        }

        val client = createClient {
            followRedirects = false
        }

        val response = client.get("/redirect")
        val content = response.body<String>()

        assertThat(response.status).isEqualTo(HttpStatusCode.PermanentRedirect)
        assertThat(content).isEqualTo("Hello world")
    }

    @Test
    internal fun `use gson`() = testApplication {
        install(RFC7807Problems) {
            gson{}
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        val response = client.get("/error")

        val content = objectMapper.readTree(response.body<String>())
        println(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("instance").textValue()).isEqualTo("/error")
        assertThat(content.get("status").intValue()).isEqualTo(500)
        assertThat(content.get("title").textValue()).isEqualTo("Internal Server Error")
    }

    @Test
    internal fun `use custom problem converter`() = testApplication {
        install(RFC7807Problems) {
            converter(TestProblemConverter())
        }

        routing {
            get("/error") {
                throw IllegalArgumentException()
            }
        }

        val response = client.get("/error")

        val content = objectMapper.readTree(response.body<String>())
        println(response.body<String>())

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.contentType()).isEqualTo(ContentType("application", "problem+json"))
        assertThat(content.get("my_title").textValue()).isEqualTo("is testie")
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

class TestBusinessException(
    var businessDetail : String
) : ThrowableProblem(
    type = "Any type",
    statusCode = HttpStatusCode.BadRequest,
    detail = "DefaultProblem to be thrown",
    title = "Awesome title"
)