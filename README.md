# ktor-problem
[![Build Status](https://travis-ci.org/tyntec/ktor-problem.svg?branch=master)](https://travis-ci.org/tyntec/ktor-problem)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/zalando/problem/master/LICENSE)

Feature for [Ktor](https://ktor.io) implementing [Rfc7807](https://tools.ietf.org/html/rfc7807)

It is inspired by Zalando's [Problem](https://github.com/zalando/problem) library.

## Usage

### Dependency

    com.tyntec:ktor-problem:0.7

### Installation 

In your ktor application simply add

    install(RFC7807Problems) {
      
    }

Ktor Problem comes along with support of Jackson and Gson for serialization.

The configuration is similar to the [Content Negotation](https://ktor.io/servers/features/content-negotiation.html) feature of Ktor.

*Jackson*

    install(Problems) {
      jackson {}      
    }
    
*Gson*

    install(Problems) {
      gson {}
    }
    
*custom one*

Ktor Problem allows you to customize the serialization to json completely. This is covered in the section below

    
The application will catch all exceptions and wrap them in a Problems object, like this

    {
      "status" : 500,
      "title" : Internal Server Error,
      "instance" : "<URI path>",
      "detail" : "<Exception message>"
    }  

### Configuration

Problems allows you to configure the default behavior as well a exception specific.

#### Exception specific

Exception specific configurations are done via the configuration method

    exception<Throwable> { context -> ... }
    
The context provides access to the ``ApplicationCall`` and the original exception

Like in this example

    exception<IllegalArgumentException> {ctx ->
      instance = ctx.call.request.path()
      statusCode = HttpStatusCode.BadRequest
      detail = ctx.throwable.message
      type = "https://api.tyntec.com/error_codes/BadRequest.html"
    }
    
#### default

The default behavior is configured by the ``default`` configuration method.
It has the access to the same ``context`` object as the exception specific handling.

#### Control logging

Logging of caught exceptions can be controlled via ``exceptionLogging``. It supports at the moment

- OFF - logging is completly turned off
- SHORT - Only the http method, path and exception message is logged
- FULL - Http method, path and the whole stack trace is logged

#### Control automated response mapping

The library intercepts in the default setting non successful http status codes and
put's them into the problem structure.

This behavior can be turned of by the toggle ``enableAutomaticResponseConversion``. 

## Throwing business exceptions

Provide configurations that handle all exceptions can be cumbersome and decrease 
the clarity of the configuration.

Ktor Problem admits this by defining the interface `Problem` and the super class `ThrowableProblem`.

The latter is a convenience implementation of `Problem` interface.

When throwing an exception that implements the interface or extends the `ThrowableProblem` class, Ktor Problem detects 
this and takes the properties from the implementing class to generate the error response.

**Note** : Jackson is currently the only converter which suppress the publication of Throwable details, like stacktrace etc. pp. .
Support for GSon is planned but not finished.

### Example

This business exception

    class TestBusinessException(
        var businessDetail : String
    ) : ThrowableProblem(
        type = "Any type",
        statusCode = HttpStatusCode.BadRequest,
        detail = "DefaultProblem to be thrown",
        title = "Awesome title"
    )

is translated to 

    { 
      "businessDetail":"a test detail",
      "type":"Any type",
      "detail":"DefaultProblem to be thrown",
      "instance":null,
      "title":"Awesome title",
      "status":400
    }

## Advanced usage

### Custom json problemConverter

In order to provider your custom json problemConverter you need to implement the ``ProblemConverter`` interface.

Afterwards enable it via 

    install(RC7807Problems) {
      converter{MyConverter()}      
    }
 
