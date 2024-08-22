@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package com.soywiz.ktcc.internal

import kotlinx.cinterop.*
import platform.posix.*

actual fun readFile(name: String): ByteArray? {
    val fd = fopen(name, "rb")
    return if (fd != null) {
        fseek(fd, 0L.convert(), SEEK_END)
        val length = ftell(fd)
        fseek(fd, 0L.convert(), SEEK_SET)
        val out = ByteArray(length.toInt())
        val read = if (out.isNotEmpty()) {
            out.usePinned { ptr ->
                fread(ptr.addressOf(0), 1.convert(), length.convert(), fd).toInt()
            }
        } else {
            0
        }
        fclose(fd)
        out.copyOf(read)
    } else {
        null
    }
}

actual fun writeFile(name: String, content: ByteArray) {
    val fd = fopen(name, "wb")
    if (fd != null) {
        content.usePinned { ptr ->
            fwrite(ptr.addressOf(0), 1.convert(), content.size.convert(), fd)
        }
        fclose(fd)
    } else {
        error("Couldn't open for write '$name'")
    }
}
