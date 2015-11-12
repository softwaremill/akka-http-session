package com.softwaremill.session

import java.security.MessageDigest
import javax.crypto.{Cipher, Mac}
import javax.crypto.spec.SecretKeySpec
import com.softwaremill.session.SessionUtil._

// Based on the implementation from Play! [[https://github.com/playframework]]
object Crypto {
  def signHmacSHA1(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    toHexString(mac.doFinal(message.getBytes("utf-8")))
  }

  def encryptAES(value: String, secret: String): String = {
    val aesSecret = secret.substring(0, 16)
    val raw = aesSecret.getBytes("utf-8")
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
    toHexString(cipher.doFinal(value.getBytes("utf-8")))
  }

  def decryptAES(value: String, secret: String): String = {
    val aesSecret = secret.substring(0, 16)
    val raw = aesSecret.getBytes("utf-8")
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, skeySpec)
    new String(cipher.doFinal(hexStringToByte(value)))
  }

  def hashSHA256(value: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    toHexString(digest.digest(value.getBytes("UTF-8")))
  }
}
