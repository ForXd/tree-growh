package com.forxd.treegrowth

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UpdatePolicyTest {
    private val release = Release("1.1.0", 2, "$RELEASE_ROOT/download/v1.1.0/qingya-1.1.0.apk", 3, "a".repeat(64), "notes")
    private val installed = PackageIdentity("com.forxd.treegrowth", "1.0.0", 1, setOf("trusted"))
    @Test fun acceptOnlyExactProjectVersionedAsset() {
        UpdatePolicy.validate(release)
        listOf(release.copy(url = "https://evil.com/a.apk"), release.copy(url = release.url + "?redirect=evil"), release.copy(bytes = UpdatePolicy.MAX_APK + 1), release.copy(sha256 = "wrong"), release.copy(version = "../foo")).forEach { assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.validate(it) } }
    }
    @Test fun rejectsWrongSignerPackageVersionAndDowngrade() {
        val good = PackageIdentity(installed.name, "1.1.0", 2, installed.signers)
        UpdatePolicy.identity(good, installed, release)
        listOf(good.copy(name = "other"), good.copy(signers = setOf("evil")), good.copy(signers = emptySet()), good.copy(code = 1), good.copy(version = "1.0.0")).forEach { assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.identity(it, installed, release) } }
    }
    @Test fun downloadRejectsTruncationAndOverflowAndHashesExactly() {
        val bytes = "abc".toByteArray()
        val output = ByteArrayOutputStream()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdatePolicy.copy(ByteArrayInputStream(bytes), output, 3))
        assertArrayEquals(bytes, output.toByteArray())
        for (size in listOf(2L, 4L)) assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), size) }
    }
    @Test fun manifestParserValidatesTrustedSource() {
        val json = """{"version":"1.1.0","versionCode":2,"url":"${release.url}","bytes":3,"sha256":"${release.sha256}","notes":"notes"}"""
        assertEquals(release, UpdatePolicy.parse(json))
        assertThrows(IllegalArgumentException::class.java) { UpdatePolicy.parse(json.replace("github.com", "example.com")) }
    }
}
