# Client-side HTTP sessions for akka-http

[akka-http](http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0-M4/scala/http/) is an experimental Akka 
module, originating from [spray.io](http://spray.io), for building *reactive* REST services with an elegant DSL.

It has almost all of the required functionalities to serve as a backend for e.g. a single-page-application, with one
exception: session handling. This project aims to fill that gap.

## Basic usage

Session data is stored as a cookie on the client. The content of the cookie is signed using a server secret, so that
it is not possible to alter the session data on the client side.
 
All directives require an implicit instance of a `SessionManager`, which can be created by providing a server secret.
The secret should be a long, random string. You can generate one by running `SessionConfig.randomServerSecret()`. Note
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

## Encrypting the session

It is possible to encrypt the session data by modifying the config:

````scala
val sessionConfig = SessionConfig
    .default("some_very_long_secret_and_random_string")
    .withEncryptSessionData(true)
````

The key used for encrypting will be calculated basing on the server secret.

## Session timeout

By default the cookie sent to the user will be a session cookie, however it is possible that the client will have the
browser open for a very long time, or if an attacker steals the cookie, it can be re-used. It is possible to include
an expiry date checked on the server-side by setting the session timeout:

````scala
val sessionConfig = SessionConfig
    .default("some_very_long_secret_and_random_string")
    .withSessionMaxAgeSeconds(Some(3600)) // 1 hour
````

The expiry will be appended to the session data (and taken into account in the signature as well).

## Customizing cookie parameters

The default config has reasonable defaults concerning the cookie that is generated, you can however customize it by
calling appropriate methods ont the `SessionConfig`.

## Creating a `SessionConfig` using Typesafe config

It is possible to create a `SessionConfig` from a `Config` object (coming from 
[Typesafe config](https://github.com/typesafehub/config)). Just use the `SessionConfig.fromConfig` method. The config
keys are:

````
akka.http.session {
  serverSecret = "some_very_long_secret_and_random_string"
  sessionCookie {
    name = "_sessiondata"
    domain = "" // optional
    path = "" // optional
    maxAge = 0 // optional
    secure = false // optional
    httpOnly = true // optional
  }
  sessionMaxAgeSeconds = 0 // optional
  encryptSessionData = false // optional
}
````

## Custom cryptography

If you'd like to change the signing algorithm, or the session data encryption/decryption code, you can provide a custom
`Crypto` implementation when creating a `SessionManager`.

## Future development

* remember me
* CSRF

## Links

* [Spray session](https://github.com/gnieh/spray-session), similar project for spray.io
* [Spray SPA](https://github.com/enetsee/Spray-SPA), a single-page-application demo built using spray.io, also
containing an implementation of client-side sessions
* [Play framework](https://playframework.com), a full web framework, from which parts of the session encoding/decoding
code was taken
* [Rails security guide](http://guides.rubyonrails.org/security.html#session-storage), a description of how sessions are
stored in Rails
* [Akka issue 16855](https://github.com/akka/akka/issues/16855) for implementing similar functionality straight in Akka
