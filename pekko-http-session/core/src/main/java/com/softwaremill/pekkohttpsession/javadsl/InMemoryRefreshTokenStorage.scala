package com.softwaremill.pekkohttpsession.javadsl

/**
 * Can't use the trait com.softwaremill.pekkohttpsession.InMemoryRefreshTokenStorage in Java code, hence this wrapper
 * http://stackoverflow.com/questions/7637752/using-scala-traits-with-implemented-methods-in-java
 */
abstract class InMemoryRefreshTokenStorage[T]() extends com.softwaremill.pekkohttpsession.InMemoryRefreshTokenStorage[T]
