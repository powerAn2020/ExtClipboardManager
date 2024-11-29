package com.hhvvg.ecm.util

import cn.hutool.crypto.Mode
import cn.hutool.crypto.Padding
import cn.hutool.crypto.symmetric.AES
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val ENCODE_KEY: String = "ABCDEFGHIJKLMNOP"
private const val IV_KEY: String = "1234567890123456"

class Auth{
    companion object {
        fun getSecret(): String {
            val random = SecureRandom()
            val dictionary = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return (1..16).map { dictionary[random.nextInt(dictionary.length)] }.joinToString("")
        }

        fun verifyCode(secret: String, code: Int): Boolean {
            return (getCode(secret, 0) == code ||
                    getCode(secret, -30) == code ||
                    getCode(secret, 30) == code)
        }

        fun getCode(secret: String, offset: Long): Int {
            val key = Base64.getDecoder().decode(secret.toByteArray(StandardCharsets.UTF_8))
            val epochSeconds = System.currentTimeMillis() / 1000 + offset
            val timeSlot = epochSeconds / 30 // 30秒一个时间槽

            val code = oneTimePassword(key, toBytes(timeSlot))
            println("当前 Google Code: $code")
            return code
        }

        fun toBytes(value: Long): ByteArray {
            val result = ByteArray(8)
            val mask: Long = 0xFF
            val shifts = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)
            for (shift in shifts) {
                result[shifts.indexOf(shift)] = ((value shr shift) and mask).toByte()
            }
            return result
        }

        fun oneTimePassword(key: ByteArray, value: ByteArray): Int {
            val hmacSha1 = Mac.getInstance("HmacSHA1").apply {
                init(SecretKeySpec(key, "HmacSHA1"))
            }
            val hash = hmacSha1.doFinal(value)

            val offset = hash.last().toInt() and 0x0F
            val number = (hash[offset].toInt() and 0x7F) shl 24 or
                    (hash[offset + 1].toInt() and 0xFF shl 16) or
                    (hash[offset + 2].toInt() and 0xFF shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            return number % 1000000000 // 返回 9 位数字
        }

        /**
         * AES加密
         * @param aesContent    被Base64加密过的字符串
         * @param key            秘钥
         * @param ivParameter    偏移量
         * @return
         */
        fun encryptFromString(data: String, mode: Mode, padding: Padding?): String {
            return encryptFromString(data,ENCODE_KEY,IV_KEY,mode,padding)
        }

        fun encryptFromString(data: String,key:String,iv:String, mode: Mode, padding: Padding?): String {
            val aes = if (Mode.CBC === mode) {
                AES(
                    mode, padding,
                    SecretKeySpec(key.toByteArray(), "AES"),
                    IvParameterSpec(iv.toByteArray())
                )
            } else {
                AES(
                    mode, padding,
                    SecretKeySpec(key.toByteArray(), "AES")
                )
            }
            return aes.encryptBase64(data, StandardCharsets.UTF_8)
        }
        /**
         * AES解密
         * @param aesContent    被Base64加密过的字符串
         * @param key            秘钥
         * @param ivParameter    偏移量
         * @param mode    Mode
         * @param padding    Padding
         * @return
         */
        fun decryptFromString(data: String?,key:String,iv:String, mode: Mode, padding: Padding?): String {
            val aes = if (Mode.CBC === mode) {
                AES(
                    mode, padding,
                    SecretKeySpec(key.toByteArray(), "AES"),
                    IvParameterSpec(iv.toByteArray())
                )
            } else {
                AES(
                    mode, padding,
                    SecretKeySpec(key.toByteArray(), "AES")
                )
            }
            val decryptDataBase64 = aes.decrypt(data)
            return String(decryptDataBase64, StandardCharsets.UTF_8)
        }

        fun decryptFromString(data: String?, mode: Mode, padding: Padding?): String {
            return decryptFromString(data,ENCODE_KEY,IV_KEY,mode,padding)
        }
    }

}
