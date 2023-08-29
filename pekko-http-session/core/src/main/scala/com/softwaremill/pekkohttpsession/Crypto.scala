package com.softwaremill.pekkohttpsession

import java.security.MessageDigest
import java.util

import SessionUtil._
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}

object Crypto {
  def sign_HmacSHA1_hex(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    toHexString(mac.doFinal(message.getBytes("utf-8")))
  }

  def sign_HmacSHA256_base64_v0_5_2(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    SessionUtil.toBase64_v0_5_2(mac.doFinal(message.getBytes("utf-8")))
  }

  def encrypt_AES(value: String, secret: String): String = {
    val raw = util.Arrays.copyOf(secret.getBytes("utf-8"), 16)
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
    toHexString(cipher.doFinal(value.getBytes("utf-8")))
  }

  def decrypt_AES(value: String, secret: String): String = {
    val raw = util.Arrays.copyOf(secret.getBytes("utf-8"), 16)
    val skeySpec = new SecretKeySpec(raw, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, skeySpec)
    new String(cipher.doFinal(hexStringToByte(value)))
  }

  def hash_SHA256(value: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    toHexString(digest.digest(value.getBytes("UTF-8")))
  }
}
