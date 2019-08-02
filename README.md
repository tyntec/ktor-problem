# ktor-problem
[![Build Status](https://travis-ci.org/tyntec/ktor-problem.svg?branch=master)](https://travis-ci.org/tyntec/ktor-problem)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/zalando/problem/master/LICENSE)

Feature for [Ktor](https://ktor.io) implementing [Rfc7807](https://tools.ietf.org/html/rfc7807)

It is inspired by Zalando's [Problem](https://github.com/zalando/problem) library.

## Usage

### Dependency

    com.tyntec:ktor-problem:0.5

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

## Advanced usage

### Custom json problemConverter

In order to provider your custom json problemConverter you need to implement the ``ProblemConverter`` interface.

Afterwards enable it via 

    install(Problems) {
      converter{MyConverter()}      
    }
 