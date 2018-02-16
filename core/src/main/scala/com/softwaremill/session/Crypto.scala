package com.softwaremill.session

import java.security.MessageDigest
import java.util
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}
import javax.xml.bind.DatatypeConverter

import com.softwaremill.session.SessionUtil._

object Crypto {
  def sign_HmacSHA1_hex(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    toHexString(mac.doFinal(message.getBytes("utf-8")))
  }

  def sign_HmacSHA256_base64(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    Base64.getUrlEncoder.withoutPadding().encodeToString(mac.doFinal(message.getBytes("utf-8")))
  }

  def sign_HmacSHA256_base64_v0_5_2(message: String, secret: String): String = {
    val key = secret.getBytes("UTF-8")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    DatatypeConverter.printBase64Binary(mac.doFinal(message.getBytes("utf-8")))
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