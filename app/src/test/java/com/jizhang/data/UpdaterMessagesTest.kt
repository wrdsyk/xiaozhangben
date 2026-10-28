package com.jizhang.data

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/** The words the owner sees when an update check or install goes wrong. */
class UpdaterMessagesTest {
    @Test
    fun `no network reads as offline`() {
        val offline = "连不上网络，请检查网络后再试"
        assertEquals(offline, Updater.describe(UnknownHostException("api.github.com")))
        assertEquals(offline, Updater.describe(SocketTimeoutException()))
        assertEquals(offline, Updater.describe(ConnectException()))
    }

    @Test
    fun `other network trouble is told apart from offline`() {
        assertEquals("安全连接失败，请检查手机的日期和网络", Updater.describe(SSLHandshakeException("bad cert")))
        assertEquals("网络出错了，请稍后再试", Updater.describe(IOException("reset")))
    }

    @Test
    fun `update exceptions carry their own message and retry flag`() {
        val e = UpdateException("还没有发布版本")
        assertEquals("还没有发布版本", Updater.describe(e))
        assertTrue(e.retryable)
        assertFalse(UpdateException("签名不一致", retryable = false).retryable)
    }

    @Test
    fun `a refused install says why`() {
        assertTrue(Updater.installFailure(PackageInstaller.STATUS_FAILURE_STORAGE, null).startsWith("手机存储空间不足"))
        assertTrue(Updater.installFailure(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null).contains("签名"))
        assertEquals("安装没有成功", Updater.installFailure(PackageInstaller.STATUS_FAILURE, null))
        assertEquals("安装没有成功：INSTALL_FAILED_X", Updater.installFailure(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_X"))
        assertEquals("安装没有成功", Updater.installFailure(PackageInstaller.STATUS_FAILURE, "  "))
    }
}
