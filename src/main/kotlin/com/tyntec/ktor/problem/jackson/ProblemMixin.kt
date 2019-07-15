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

package com.tyntec.ktor.problem.jackson

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import io.ktor.http.HttpStatusCode

abstract class ProblemMixin(
    @get:JsonIgnore var statusCode: HttpStatusCode,
    @get:JsonAnyGetter var additionalDetails: Map<String, Any>,
    @get:JsonIgnore var cause: Throwable?,
    @get:JsonIgnore var message: String?,
    @get:JsonIgnore var stackTrace: Any?,
    @get:JsonIgnore var localizedMessage: String?,
    @get:JsonIgnore var suppressed: String?
) {
    var title: String? = null
        get() {
            if (field.isNullOrEmpty())
                return statusCode.description
            return field
        }
}