package com.korenskoy.yandexmusic.data.download

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.EOFException
import java.io.FileInputStream
import java.io.RandomAccessFile

/** Bytes that can be read from any position: a file, or a document opened through the system (a flash drive). */
interface SeekableBytes : Closeable {
    val length: Long
    fun seek(position: Long)

    /** Up to [count] bytes into [target]; -1 at the end. */
    fun read(target: ByteArray, offset: Int, count: Int): Int

    fun readFully(target: ByteArray) {
        var done = 0
        while (done < target.size) {
            val n = read(target, done, target.size - done)
            if (n < 0) throw EOFException()
            done += n
        }
    }

    companion object {
        fun of(file: RandomAccessFile): SeekableBytes = object : SeekableBytes {
            override val length: Long get() = file.length()
            override fun seek(position: Long) = file.seek(position)
            override fun read(target: ByteArray, offset: Int, count: Int): Int = file.read(target, offset, count)
            override fun close() = file.close()
        }

        fun of(descriptor: ParcelFileDescriptor): SeekableBytes = object : SeekableBytes {
            private val input = FileInputStream(descriptor.fileDescriptor)
            private val channel = input.channel
            override val length: Long get() = descriptor.statSize.takeIf { it >= 0 } ?: channel.size()
            override fun seek(position: Long) {
                channel.position(position)
            }

            override fun read(target: ByteArray, offset: Int, count: Int): Int = input.read(target, offset, count)
            override fun close() {
                try {
                    input.close()
                } finally {
                    descriptor.close()
                }
            }
        }
    }
}
