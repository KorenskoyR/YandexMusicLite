package com.korenskoy.yandexmusic.utils

import java.math.BigInteger
import java.security.MessageDigest

/** Where the developer can be supported, by the user's own free will. Checked by a test, so a typo never ships. */
object Donations {
    /** The fundraising page on ЮMoney (a card, СБП or a ЮMoney wallet). */
    const val YOOMONEY_URL = "https://yoomoney.ru/fundraise/1LGNB9FPDB3.261112"

    /** A USDT wallet on the TRON network (TRC20). */
    const val USDT_TRC20 = "TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxYTE"

    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val TRON_VERSION = 0x41
    private const val ADDRESS_BYTES = 25
    private const val CHECKSUM_BYTES = 4

    /**
     * A TRON address: Base58Check of 0x41 and 20 bytes, its last 4 bytes the start of a double SHA-256 of the rest.
     * A letter lost or changed breaks the checksum.
     */
    fun isTronAddress(address: String): Boolean {
        if (address.isEmpty() || address.any { it !in BASE58 }) return false
        var value = BigInteger.ZERO
        for (c in address) value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(BASE58.indexOf(c).toLong()))
        val raw = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val bytes = ByteArray(address.takeWhile { it == '1' }.length) + raw
        if (bytes.size != ADDRESS_BYTES || bytes[0] != TRON_VERSION.toByte()) return false
        val payload = bytes.copyOfRange(0, ADDRESS_BYTES - CHECKSUM_BYTES)
        val sha = MessageDigest.getInstance("SHA-256")
        val check = sha.digest(sha.digest(payload)).copyOfRange(0, CHECKSUM_BYTES)
        return check.contentEquals(bytes.copyOfRange(ADDRESS_BYTES - CHECKSUM_BYTES, ADDRESS_BYTES))
    }
}
