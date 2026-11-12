package com.korenskoy.yandexmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The support details are exactly the owner's: a typo in a wallet address would send money nowhere. */
class DonationsTest {

    @Test
    fun theWalletIsAValidTronAddress() {
        assertTrue(Donations.isTronAddress(Donations.USDT_TRC20))
        assertEquals("TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxYTE", Donations.USDT_TRC20)
    }

    @Test
    fun aTypoBreaksTheAddress() {
        // As first sent: the leading "T" lost
        assertFalse(Donations.isTronAddress("MAnQX1jC4rz9Mmu8yYonukwcJ72oFxYTE"))
        // One letter changed, one swapped, one cut
        assertFalse(Donations.isTronAddress("TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxYTF"))
        assertFalse(Donations.isTronAddress("TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxTYE"))
        assertFalse(Donations.isTronAddress("TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxYT"))
        // Not Base58 at all, and empty
        assertFalse(Donations.isTronAddress("TMAnQX1jC4rz9Mmu8yYonukwcJ72oFxY0E"))
        assertFalse(Donations.isTronAddress(""))
    }

    @Test
    fun theFundraiserIsTheOwnersPage() {
        assertEquals("https://yoomoney.ru/fundraise/1LGNB9FPDB3.261112", Donations.YOOMONEY_URL)
    }
}
