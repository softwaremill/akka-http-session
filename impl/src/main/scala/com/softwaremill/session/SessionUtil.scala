package com.softwaremill.session

import java.math.BigInteger
import java.util.concurrent.ThreadLocalRandom
import javax.xml.bind.DatatypeConverter

object SessionUtil {
  def randomString(length: Int) = {
    // http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
    val random = ThreadLocalRandom.current()
    new BigInteger(length * 5, random).toString(32) // because 2^5 = 32
  }

  /**
   * Utility method for generating a good server secret.
   */
  def randomServerSecret() = randomString(128)

  // Do not change this unless you understand the security issues behind timing attacks.
  // This method intentionally runs in constant time if the two strings have the same length.
  // If it didn't, it would be vulnerable to a timing attack.
  def constantTimeEquals(a: String, b: String) = {
    if (a.length != b.length) {
      false
    }
    else {
      var equal = 0
      for (i <- Array.range(0, a.length)) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }

  def toHexString(array: Array[Byte]): String = {
    DatatypeConverter.printHexBinary(array)
  }

  def hexStringToByte(hexString: String): Array[Byte] = {
    DatatypeConverter.parseHexBinary(hexString)
  }
}
