package com.tyntec.ktor.problem

import io.ktor.application.ApplicationCall

data class ProblemContext<T : Throwable>(
    val call: ApplicationCall,
    val throwable: T
)
