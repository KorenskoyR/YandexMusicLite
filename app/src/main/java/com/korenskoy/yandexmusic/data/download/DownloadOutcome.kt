package com.korenskoy.yandexmusic.data.download

/** What a finished «Скачать всю коллекцию» tells the listener: downloaded, and those that cannot be. Pure. */
data class DownloadOutcome(val done: Int, val failed: Int) {
    companion object {
        /** Again later only for want of a network: the tracks that failed on a connection cannot be had now. */
        fun shouldRetry(failed: Int, online: Boolean): Boolean = failed > 0 && !online
    }
}
