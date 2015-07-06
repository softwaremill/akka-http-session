# Client-side HTTP sessions for akka-http

[akka-http](http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0-M4/scala/http/) is an experimental Akka 
module, originating from [spray.io](http://spray.io), for building *reactive* REST services with an elegant DSL.

It has almost all of the required functionalities to serve as a backend for e.g. a single-page-application, with one
exception: session handling. This project aims to fill that gap.

## Client-side sessions

### Basic usage

Session data is stored as a cookie on the client. The content of the cookie is signed using a server secret, so that
it is not possible to alter the session data on the client side.
 
All directives require an implicit instance of a `SessionManager`, which can be created by providing a server secret.
The secret should be a long, random string. You can generate one by running `SessionUtil.randomServerSecret()`. Note
that when you change the secret, all sessions will become invalid.

````scala
val sessionConfig = SessionConfig.default("some_very_long_secret_and_random_string")
implicit val sessionManager = new SessionManager(sessionConfig)
````

The basic directives enable you to set, read and invalidate the session. To create a new client-side session, that is,
send the cookie:

````scala
path("login") {
  post {
    entity(as[String]) { body =>
      setSession(Map("key1" -> "value1", "key2" -> "value2")) { ctx =>
        ctx.complete("ok")
      }
    }
  }
}
````

Note that the size of the cookie is limited to 4KB, so you shouldn't put too much data in there (the signature takes
about 50 characters). Typically the session will contain the user id, username or a token, and the rest will be read
on the server (from a database, memcache server etc.)

You can require a session to be present or optionally require a session:

````scala
path("secret") {
  get {
    requiredSession() { session => // type: Map[String, String]
      complete { "treasure" }
    }
  }
} ~
path("open") {
  get {
    optionalSession() { session => // type: Option[Map[String, String]]
      complete { "small treasure" }
    }
  }
}
````

If a required session is not present, by default a 403 is returned. Finally, a session can be invalidated:

````scala
path("logout") {
  get {
    requiredSession() { session => 
      invalidateSession() {
        complete { "logged out" }
      }
    }
  }
}
````

You can try out a running example by running `com.softwaremill.example.Example` in the `example` project.

### Encrypting the session

It is possible to encrypt the session data by modifying the config:

````scala
val sessionConfig = SessionConfig
    .default("some_very_long_secret_and_random_string")
    .withEncryptSessionData(true)
````

The key used for encrypting will be calculated basing on the server secret.

### Session timeout

By default the cookie sent to the user will be a session cookie, however it is possible that the client will have the
browser open for a very long time, or if an attacker steals the cookie, it can be re-used. It is possible to include
an expiry date checked on the server-side by setting the session timeout:

````scala
val sessionConfig = SessionConfig
    .default("some_very_long_secret_and_random_string")
    .withSessionMaxAgeSeconds(Some(3600)) // 1 hour
````

The expiry will be appended to the session data (and taken into account in the signature as well).

## CSRF protection

CSRF is an attack where an attacker can construct issue `GET` or `POST` requests on behalf of a user, if the user e.g.
clicks on a specially constructed link. See the [OWASP page](https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)_Prevention_Cheat_Sheet)
or the [Play! docs](https://www.playframework.com/documentation/2.2.x/JavaCsrf) for a thorough introduction.

All web apps should be protected against CSRF attacks. This implementation:
* assumes that `GET` requests are non-mutating (have no side effects)
* uses double-submit cookies to verify requests
* requires the token to be set in a custom header or (optionally) in a form field
* generates a new token on the first `GET` request that doesn't have the token cookie set

Note that if the token is passed in a form field, the website isn't protected by HTTPS or you don't control all 
subdomains, this scheme [can be broken](http://security.stackexchange.com/questions/59470/double-submit-cookies-vulnerabilities/61039#61039).
Currently, setting a custom header seems to be a secure solution, and is what a number of projects do (

It is recommended that a new CSRF token is generated after logging in, see [this SO question](http://security.stackexchange.com/questions/22903/why-refresh-csrf-token-per-form-request).
A new token can be generated using the `setNewCsrfToken` directive.

By default the name of the CSRF cookie and the custom header matches what [AngularJS expects and sets](https://docs.angularjs.org/api/ng/service/$http).
These can be customized in the config, see below.

Example usage:

````scala
randomTokenCsrfProtection() {
  get("site") {
    // read from disk
  } ~
  post("transfer_money") {
    // token already checked
  }
}
````             

## Customizing cookie parameters

The default config has reasonable defaults concerning the cookie that is generated, you can however customize it by
calling appropriate methods ont the `SessionConfig`.

By default the `secure` attribute of cookies is not set (for development), however it is recommended that all sites 
use `https` and all cookies have this attribute set. 

## Creating a `SessionConfig` using Typesafe config

It is possible to create a `SessionConfig` from a `Config` object (coming from 
[Typesafe config](https://github.com/typesafehub/config)). Just use the `SessionConfig.fromConfig` method. The config
keys are:

````
akka.http.session {
  serverSecret = "some_very_long_secret_and_random_string" // only required config key
  clientSessionCookie {
    name = "_sessiondata"
    domain = "" 
    path = "" 
    maxAge = 0 
    secure = false 
    httpOnly = true 
  }
  clientSessionMaxAgeSeconds = 0 
  encryptSessionData = false   
  
  csrfCookie {
    name = "XSRF-TOKEN" 
    domain = "" 
    path = "/" 
    maxAge = 0 
    secure = false 
    httpOnly = false 
  }
  
  csrfSubmittedName = "X-XSRF-TOKEN"
}
````

## Custom cryptography

If you'd like to change the signing algorithm, or the session data encryption/decryption code, you can provide a custom
`Crypto` implementation when creating a `SessionManager`.

## Future development

* remember me

## Links

* [Spray session](https://github.com/gnieh/spray-session), similar project for spray.io
* [Spray SPA](https://github.com/enetsee/Spray-SPA), a single-page-application demo built using spray.io, also
containing an implementation of client-side sessions
* [Play framework](https://playframework.com), a full web framework, from which parts of the session encoding/decoding
code was taken
* [Rails security guide](http://guides.rubyonrails.org/security.html#session-storage), a description of how sessions are
stored in Rails
* [Akka issue 16855](https://github.com/akka/akka/issues/16855) for implementing similar functionality straight in Akka
