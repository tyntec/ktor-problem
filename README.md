# ktor-problem
Feature for [Ktor](https://ktor.io) implementing [Rfc7807](https://tools.ietf.org/html/rfc7807)

It is inspired by Zalando's [Problem](https://github.com/zalando/problem) library.

## Usage

### Dependency

TODO 

### Installation 

In your ktor application simply add

    install(Problems)
    
Now the application will catch all exceptions and wrap them in a Problems object, like this

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