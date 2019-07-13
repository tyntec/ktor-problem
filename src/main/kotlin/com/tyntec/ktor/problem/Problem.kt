package com.tyntec.ktor.problem

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.util.AttributeKey

class Problem (configuration: Configuration) {
    val prop = configuration.prop // Copies a snapshot of the mutable config into an immutable property.

    class Configuration {
        var prop = "value" // Mutable property.
    }

    // Implements ApplicationFeature as a companion object.
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Problem> {
        // Creates a unique key for the feature.
        override val key = AttributeKey<Problem>("Problem")

        // Code to execute when installing the feature.
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Problem {

            // It is responsibility of the install code to call the `configure` method with the mutable configuration.
            val configuration = Problem.Configuration().apply(configure)

            // Create the feature, providing the mutable configuration so the feature reads it keeping an immutable copy of the properties.
            val feature = Problem(configuration)

            // Intercept a pipeline.
            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                // Perform things in that interception point.
            }
            return feature
        }
    }
}