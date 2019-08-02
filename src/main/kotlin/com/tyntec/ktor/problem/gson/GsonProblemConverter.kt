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

package com.tyntec.ktor.problem.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tyntec.ktor.problem.ProblemConverter
import com.tyntec.ktor.problem.RFC7807Problems

class GsonProblemConverter(private val gson: Gson) :ProblemConverter {
    override fun convert(problem: Any): String = gson.toJson(problem)
}

fun RFC7807Problems.Configuration.gson(block: GsonBuilder.() -> Unit = {}) {
    val builder = GsonBuilder()
    builder.apply(block)
    builder.setExclusionStrategies(GsonExclusionStrategy())
    problemConverter = GsonProblemConverter(builder.create())
}