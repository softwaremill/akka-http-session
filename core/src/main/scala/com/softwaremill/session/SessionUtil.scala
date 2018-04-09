package com.softwaremill.session

import java.math.BigInteger
import java.util.Base64
import java.util.concurrent.ThreadLocalRandom

object SessionUtil {
  def randomString(length: Int): String = {
    // http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
    val random = ThreadLocalRandom.current()
    new BigInteger(length * 5, random).toString(32) // because 2^5 = 32
  }

  /**
   * Utility method for generating a good server secret.
   */
  def randomServerSecret(): String = randomString(128)

  // Do not change this unless you understand the security issues behind timing attacks.
  // This method intentionally runs in constant time if the two strings have the same length.
  // If it didn't, it would be vulnerable to a timing attack.
  def constantTimeEquals(a: String, b: String): Boolean = {
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

  private val HexArray = "0123456789ABCDEF".toCharArray

  def toHexString(bytes: Array[Byte]): String = {
    // from https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    val hexChars = new Array[Char](bytes.length * 2)
    var j = 0
    while (j < bytes.length) {
      val v = bytes(j) & 0xFF
      hexChars(j * 2) = HexArray(v >>> 4)
      hexChars(j * 2 + 1) = HexArray(v & 0x0F)
      j += 1
    }
    new String(hexChars)
  }

  def hexStringToByte(hexString: String): Array[Byte] = {
    // https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
    val len = hexString.length
    val data = new Array[Byte](len / 2)
    var i = 0
    while (i < len) {
      data(i / 2) = ((Character.digit(hexString.charAt(i), 16) << 4) +
        Character.digit(hexString.charAt(i + 1), 16)).toByte
      i += 2
    }

    data
  }

  def toBase64_v0_5_2(bytes: Array[Byte]): String = {
    Base64.getUrlEncoder.encodeToString(bytes)
  }

  def parseBase64_v0_5_2(s: String): Array[Byte] = {
    Base64.getUrlDecoder.decode(s)
  }
}