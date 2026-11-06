package com.forxd.treegrowth

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Opt-in end-to-end: the old signed app itself downloads and launches Android's installer. */
class PublishedUpgradeDeviceTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pkg = "com.forxd.treegrowth"
    private fun click(selector: BySelector, timeout: Long = 20000) {
        val node = device.wait(Until.findObject(selector), timeout) ?: error("UI target unavailable: $selector")
        node.click(); device.waitForIdle()
    }
    private fun packageDump() = device.executeShellCommand("dumpsys package $pkg")
    private fun firstInstall(dump: String) = Regex("firstInstallTime=([^\\n]+)").find(dump)?.groupValues?.get(1)
    @Test fun publishedUpgradePreservesCustomChallenge() {
        if (InstrumentationRegistry.getArguments().getString("publishedUpgrade") != "true") return
        val from = InstrumentationRegistry.getArguments().getString("fromVersion") ?: "0.9.0"
        val to = InstrumentationRegistry.getArguments().getString("toVersion") ?: "1.0.0"
        val before = packageDump()
        assertTrue("Baseline not installed", before.contains("versionName=$from"))
        val installedAt = firstInstall(before); assertNotNull(installedAt)
        device.executeShellCommand("am start -n $pkg/$pkg.MainActivity")
        click(By.text("百次"))
        if (device.wait(Until.hasObject(By.text("开启我的 100 次")), 2000)) {
            click(By.text("开启我的 100 次"))
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000) ?: error("No challenge input")
            input.text = "升级保留的跳绳"
            click(By.text("开启百次挑战"))
            click(By.text("完成一次 · 点亮第 1 格"))
        }
        assertTrue(device.wait(Until.hasObject(By.desc("第1格，已完成，查看记录")), 10000))
        click(By.text("我的"))
        var found = false
        repeat(7) {
            if (device.hasObject(By.textContains("应用更新"))) found = true
            if (!found) { device.swipe(device.displayWidth/2, device.displayHeight*8/10, device.displayWidth/2, device.displayHeight*3/10, 20); device.waitForIdle() }
        }
        device.swipe(device.displayWidth/2, device.displayHeight*7/10, device.displayWidth/2, device.displayHeight*3/10, 30)
        device.waitForIdle()
        click(By.textContains("应用更新"))
        if (!device.wait(Until.hasObject(By.text("青芽在慢慢变好")), 3000)) click(By.textContains("应用更新"))
        assertTrue(device.wait(Until.hasObject(By.text("青芽在慢慢变好")), 10000))
        click(By.text("下载更新"), 60000)
        click(By.text("安装更新"), 180000)
        // First-time source permission is part of the path, not pre-granted by adb.
        val toggle = device.wait(Until.findObject(By.res("android:id/switch_widget")), 5000)
            ?: device.findObject(By.clazz("android.widget.Switch")) ?: device.findObject(By.checkable(true))
        if (toggle != null) { if (!toggle.isChecked) toggle.click(); device.pressBack(); device.waitForIdle() }
        val install = device.wait(Until.findObject(By.text("Update")), 15000)
            ?: device.findObject(By.text("更新")) ?: device.findObject(By.text("Install")) ?: device.findObject(By.text("安装"))
            ?: error("Android installer did not appear")
        install.click()
        val deadline = System.currentTimeMillis() + 90000
        while (!packageDump().contains("versionName=$to") && System.currentTimeMillis() < deadline) Thread.sleep(500)
        val after = packageDump()
        assertTrue("Published target not installed", after.contains("versionName=$to"))
        assertEquals("App was reinstalled instead of updated", installedAt, firstInstall(after))
        device.executeShellCommand("am force-stop $pkg")
        device.executeShellCommand("am start -n $pkg/$pkg.MainActivity")
        click(By.text("百次"))
        assertTrue(device.wait(Until.hasObject(By.textContains("升级保留的跳绳")), 15000))
        assertTrue(device.wait(Until.hasObject(By.desc("第1格，已完成，查看记录")), 10000))
        device.takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "published-upgrade.png"))
    }
}
