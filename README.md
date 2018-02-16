# Web & mobile sessions for akka-http

[![Build Status](https://travis-ci.org/softwaremill/akka-http-session.svg?branch=master)](https://travis-ci.org/softwaremill/akka-http-session)
[![Join the chat at https://gitter.im/softwaremill/akka-http-session](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/akka-http-session?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.akka-http-session/core_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.akka-http-session/core_2.11)
[![Dependencies](https://app.updateimpact.com/badge/634276070333485056/akka-http-session.svg?config=compile)](https://app.updateimpact.com/latest/634276070333485056/akka-http-session)

[`akka-http`](http://doc.akka.io/docs/akka/2.4.2/scala/http/index.html) is an Akka 
module, originating from [spray.io](http://spray.io), for building *reactive* REST services with an elegant DSL.

`akka-http` is a great toolkit for building backends for single-page or mobile applications. In almost all apps there 
is a need to maintain user sessions, make sure session data is secure and cannot be tampered with.

`akka-http-session` provides directives for client-side session management in web and mobile applications, using cookies
or custom headers + local storage, with optional [Json Web Tokens](http://jwt.io/) format support. 

A [comprehensive FAQ](https://github.com/softwaremill/akka-http-session-faq) is available, along with code examples (in Java, but easy to translate to Scala) which answers many common questions on how sessions work, how to secure them and implement using akka-http.

## What is a session?

Session data typically contains at least the `id` or `username` of the logged in user. This id must be secured so that a 
session cannot be "stolen" or forged easily.

Sessions can be stored on the server, either in-memory or in a database, with the session `id` sent to the client,
or entirely on the client in a serialized format. The former approach requires sticky sessions or additional shared
storage, while using the latter (which is supported by this library) sessions can be easily deserialized on any server.
  
A session is a string token which is sent to the client and should be sent back to the server on every request.

To prevent forging, serialized session data is **signed** using a server secret. The signature is appended to the
session data that is sent to the client, and verified when the session token is received back.

## `akka-http-session` features

* type-safe client-side sessions
* sessions can be encrypted
* sessions contain an expiry date
* cookie or custom header transport
* support for [JWT](http://jwt.io/)
* refresh token support (e.g. to implement "remember me")
* CSRF tokens support
* Java & Scala APIs

## Example

You can try out a simple example by running [`com.softwaremill.example.ScalaExample`](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/ScalaExample.scala) or [`com.softwaremill.example.JavaExample`](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/JavaExample.java) and opening [http://localhost:8080](http://localhost:8080).

### Starting the server (especially in a Java 9+ environment)

`sbt` Command line usage:
1. Start `sbt` on the command line.
2. Enter the `example/reStart` command.
3. Choose one of the server applications, eg. ScalaExample by its number.
4. Don't forget to kill the forked services by issuing a `reStop` command before an abnormal stop of the parent `sbt` process.
(A normal sbt command will automatically issuing a `reStop`.)

Discussion:

In order to run in a Java 9+ environment, the `java.xml.bind` module must be added to the runtime. The only way to do so, is to fork a new JVM with the `--add-modules=java.xml.bind` option.

With the `reStart` / `reStop` commands the forked server process can independently be controlled without disturbing the parent sbt process.   

## `SessionManager` & configuration

All directives require an (implicit for scala) instance of a `SessionManager[T]` (or `SessionManager<T>`), which can be created by providing a server 
secret (via a `SessionConfig`). The secret should be a long, random string unique to each environment your app is
running in. You can generate one with `SessionUtil.randomServerSecret()`. Note that when you change the secret, 
all sessions will become invalid.

A `SessionConfig` instance can be created using [Typesafe config](https://github.com/typesafehub/config).
The only value that you need to provide is `akka.http.session.server-secret`,
preferably via `application.conf` (then you can safely call `SessionConfig.fromConfig`) or by using 
`SessionConfig.default()`.

You can customize any of the [default config options](https://github.com/softwaremill/akka-http-session/blob/master/core/src/main/resources/reference.conf) 
either by modifying them through `application.conf` or by modifying the `SessionConfig` case class. If a value has
type `Option[]`, you can set it to `None` by using a `none` value in the config file (for both java and scala).

When using cookies, by default the `secure` attribute of cookies is not set (for development), however it is 
recommended that all sites use `https` and all cookies have this attribute set. 

## Client-side sessions

All session-related directives take at least two parameters:
 
* session continuity: `oneOff` vs `refreshable`; specifies what should happen when the session expires. If `refreshable`
and a refresh token is present, the session will be re-created. See below for details.
* session transport: `usingCookies` vs `usingHeaders`

Typically, you would create aliases for the session-related directives which use the right parameters basing on the
current request and logic specific to your application.

### Cookies vs header

Session data can be sent to the client using cookies or custom headers. The first approach is the simplest to use,
as cookies are automatically sent to the server on each request. 

However, cookies have some security vulnerabilities, and are typically not used in mobile applications. For these
scenarios, session data can be transported using custom headers (the names of the headers are configurable in 
the config).

When using headers, you need to store the session (and, if used, refresh-) tokens yourself. These tokens can be 
stored in-memory, or persistently e.g. using the browser's local storage.

You can dynamically decide which transport to use, basing e.g. on the user-agent or other request properties.

### Basic usage

Sessions are typed. The `T` type parameter in `SessionManager[T]` (or `SessionManager<T>`) determines what data is stored in the session. 
Basic types like `String`, `Int`, `Long`, `Float`, `Double` and `Map[String, String]` (`Map<String, String>`) are supported out-of-the box. 
Support for other types can be added by providing a (an implicit for scala) `SessionSerializer[T, String]` (`SessionSerializer<T, String>`). For case classes, it's most 
convenient to use a `MultiValueSessionSerializer[T]` or (`MultiValueSessionSerializer<T>`) which should convert the instance into a `String -> String` map 
(nested types are not supported on purpose, as session data should be small & simple). Examples of `SessionSerializer` and `MultiValueSessionSerializer` 
usage can be found [here](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/serializers) for scala and [here](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/serializers) for java. 

Here are code samples in [scala](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/session/manager/MyScalaSessionManager.scala) and [java](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/session/manager/MyJavaSessionManager.java) illustrating how to create a session manager where the session content will be a single `Long` number.

The basic directives enable you to set, read and invalidate the session. To create a new client-side session (create
and set a new session cookie), you need to use the `setSession` directive. See how it's done in [java](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/session/SetSessionJava.java) and [scala](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/session/SetSessionScala.scala).

Note that when using cookies, their size is limited to 4KB, so you shouldn't put too much data in there (the signature 
takes about 50 characters). 

You can require a session to be present, optionally require a session or get a full description of possible session decode outcomes. 
Check [java](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/session/VariousSessionsJava.java) and [scala](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/session/VariousSessionsScala.scala) examples for details.

If a required session is not present, by default a `403` HTTP status code is returned. Finally, a session can be invalidated. See how it's done in examples for [java](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/session/SessionInvalidationJava.java) and [scala](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/session/SessionInvalidationScala.scala).

### Encrypting the session

It is possible to encrypt the session data by modifying the `akka.http.session.encrypt-data` config option. When 
sessions are encrypted, it's not possible to read their content on the client side.

The key used for encrypting will be calculated basing on the server secret.

### Session expiry/timeout

By default, sessions expire after a week. This can be disabled or changed with the `akka.http.session.max-age` config
option.

Note that when using cookies, even though the cookie sent will be a session cookie, it is possible that the client 
will have the browser open for a very long time, [uses Chrome or FF](http://stackoverflow.com/questions/10617954/chrome-doesnt-delete-session-cookies), 
or if an attacker steals the cookie, it can be re-used. Hence having an expiry date for sessions is highly recommended.

## JWT: encoding sessions

By default, sessions are encoded into a string using a custom format, where expiry/data/signature parts are separated using `-`, and data fields are separated using `=` and url-encoded.

You can also encode sessions in the [Json Web Tokens](http://jwt.io) format, by adding the additional `jwt` dependency, which makes use of [`json4s`](http://json4s.org).

When using JWT, you need to provide a serializer which serializes session data to a `JValue` instead of a `String`. 
A number of serializers for the basic types are present in `JValueSessionSerializer`, as well as a generic serializer for case classes (used above).

You may also find it helpful to include the json4s-ext library which provides serializers for common Java types such as  `java.util.UUID`, `org.joda.time._` and Java enumerations.

Grab some [java](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/java/com/softwaremill/example/jwt/JavaJwtExample.java) and [scala](https://github.com/softwaremill/akka-http-session/blob/master/example/src/main/scala/com/softwaremill/example/serializers/JWTSerializersScala.scala) examples.

There are many tools available to read JWT session data using various platforms, e.g. 
[for Angular](https://github.com/auth0/angular-jwt).

It is also possible to customize the session data content generated by overriding appropriate methods in 
`JwtSessionEncoder` (e.g. provide additional claims in the payload).

## CSRF protection (cookie transport only)

CSRF is a kind of an attack where an attacker issues a `GET` or `POST` request on behalf of a user, if the user e.g.
clicks on a specially constructed link. See the [OWASP page](https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)_Prevention_Cheat_Sheet)
or the [Play! docs](https://www.playframework.com/documentation/2.2.x/JavaCsrf) for a thorough introduction.

Web apps which use cookies for session management should be protected against CSRF attacks. This implementation:

* assumes that `GET` requests are non-mutating (have no side effects)
* uses double-submit cookies to verify requests
* requires the token to be set in a custom header or (optionally) in a form field
* generates a new token on the first `GET` request that doesn't have the token cookie set

Note that if the token is passed in a form field, the website isn't protected by HTTPS or you don't control all 
subdomains, this scheme [can be broken](http://security.stackexchange.com/questions/59470/double-submit-cookies-vulnerabilities/61039#61039).
Currently, setting a custom header seems to be a secure solution, and is what a number of projects do (that's why, when
using custom headers to send session data, no additional protection is needed).

It is recommended to generate a new CSRF token after logging in, see [this SO question](http://security.stackexchange.com/questions/22903/why-refresh-csrf-token-per-form-request).
A new token can be generated using the `setNewCsrfToken` directive.

By default the name of the CSRF cookie and the custom header matches what [AngularJS expects and sets](https://docs.angularjs.org/api/ng/service/$http).
These can be customized in the config.
     
## Refresh tokens (a.k.a "remember me")

If you'd like to implement persistent, "remember-me" sessions, you should use `refreshable` instead of `oneOff`
sessions. This is especially useful in mobile applications, where you log in once, and the session is remembered for
a long time. Make sure to adjust the `akka.http.session.refresh-token.max-age` config option appropriately 
(defaults to 1 month)!

You can dynamically decide, basing on the request properties (e.g. a query parameter), if a session should be
refreshable or not. Just pass the right parameter to `setSession`.

When using refreshable sessions, in addition to an (implicit) `SessionManager` instance, you need to provide an 
implementation of the `RefreshTokenStorage` trait. This trait has methods to lookup, store and delete refresh tokens. 
Typically it would use some persistent storage.

The tokens are never stored directly, instead only token hashes are passed to the storage. That way even if the token
database is leaked, it won't be possible to forge sessions using the hashes. Moreover, in addition to the token hash,
a selector value is stored. That value is used to lookup stored hashes; tokens are compared using a special
constant-time comparison method, to prevent timing attacks.

When a session expires or is not present, but the refresh token is (sent from the client using either a cookie,
or a custom header), a new session will be created (using the `RefreshTokenLookupResult.createSession` function), 
and a new refresh token will be created.

Note that you can differentiate between sessions created from refresh tokens and from regular authentication
by storing appropriate information in the session data. That way, you can force the user to re-authenticate 
if the session was created by a refresh token before crucial operations.

It is of course possible to read `oneOff`-session using `requiredSession(refreshable, ...)`. If a session was created
as `oneOff`, using `refreshable` has no additional effect.

### Touching sessions

The semantics of `touch[Required|Optional]Session()` are a bit subtle. You can still use expiring client
sessions when using refresh tokens. You will then have 2 stages of expiration: expiration of the client session
(should be shorter), and expiry of the refresh token. That way you can have strongly-authenticated sessions
which expire fast, and weaker-authenticated re-creatable sessions (as described in the paragraph above).

When touching an existing session, the refresh token will not be re-generated and extended, only the session
cookie.

## Links

* [Bootzooka](https://github.com/softwaremill/bootzooka), a web application template project using `akka-http` and `akka-http-session`
* [Spray session](https://github.com/gnieh/spray-session), similar project for spray.io
* [Spray SPA](https://github.com/enetsee/Spray-SPA), a single-page-application demo built using spray.io, also
containing an implementation of client-side sessions
* [Play framework](https://playframework.com), a full web framework, from which parts of the session encoding/decoding
code was taken
* [Rails security guide](http://guides.rubyonrails.org/security.html#session-storage), a description of how sessions are
stored in Rails
* [Akka issue 16855](https://github.com/akka/akka/issues/16855) for implementing similar functionality straight in Akka
* [Implementing remember me](https://paragonie.com/blog/2015/04/secure-authentication-php-with-long-term-persistence#title.2)
* [The definitive guide to form-based website authorization](http://stackoverflow.com/questions/549/the-definitive-guide-to-form-based-website-authentication)
* [The Anatomy of a JSON Web Token](https://scotch.io/tutorials/the-anatomy-of-a-json-web-token)
* [Cookies vs tokens](https://auth0.com/blog/2014/01/07/angularjs-authentication-with-cookies-vs-token/)

## Using from SBT

For `akka-http` version `10.0.3`:

````scala
libraryDependencies += "com.softwaremill.akka-http-session" %% "core" % "0.5.x"
libraryDependencies += "com.softwaremill.akka-http-session" %% "jwt"  % "0.5.x" // optional
````

## Updating

Certain releases changed the client token encoding/serialization. In those cases, it's important to enable the appropriate
token migrations, otherwise existing client sessions will be invalid (and your users will be logged out).

When updating from a version before 0.5.3, set `akka.http.session.token-migration.v0-5-3.enabled = true`.

When updating from a version before 0.5.2, set `akka.http.session.token-migration.v0-5-2.enabled = true`.

Note that when updating through multiple releases, be sure to enable all the appropriate migrations.

For versions prior to 0.5.0, no migration path is provided. However, you can implement your own encoders/serializers
to support migrating from whatever version you are using.

Since token changes may be security related, migrations should be enabled for the shortest period of time
after which the vast majority of client tokens have been migrated.