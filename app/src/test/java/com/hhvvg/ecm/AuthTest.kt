package com.hhvvg.ecm

import cn.hutool.crypto.Mode
import cn.hutool.crypto.Padding
import com.hhvvg.ecm.util.Auth
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTest {
    @Test
    fun verifyCode() {
        println("----------------- 生成secret -------------------")
        val secret = Auth.getSecret()
//    val secret = "VAFPUELPKRJMPGYV"
        println("secret: $secret")

        println("----------------- 信息校验----------------------")
        val code: Int = 698380121
        val isVerified = Auth.verifyCode(secret, code)
        println(if (isVerified) "验证成功！" else "验证失败！")


        println("------------------ CBC模式 --------------------")
        val encryptData = Auth.encryptFromString("        val encryptData = Auth.encryptFromString(\"hello world\", Mode.CBC, Padding.PKCS5Padding)\n", Mode.CBC, Padding.PKCS5Padding)
        println("加密：$encryptData")
        val decryptData = Auth.decryptFromString(encryptData, Mode.CBC, Padding.PKCS5Padding)
        println("解密：$decryptData")
    }
}
