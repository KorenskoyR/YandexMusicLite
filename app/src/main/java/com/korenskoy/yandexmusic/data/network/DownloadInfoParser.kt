package com.korenskoy.yandexmusic.data.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object DownloadInfoParser {

    data class DownloadInfoXml(
        val host: String,
        val path: String,
        val ts: String,
        val s: String
    )

    fun parseXml(xml: String): DownloadInfoXml? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xml))

            var eventType = xpp.eventType
            var currentTag = ""
            var host = ""
            var path = ""
            var ts = ""
            var s = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = xpp.name
                } else if (eventType == XmlPullParser.TEXT) {
                    val text = xpp.text.trim()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "host" -> host = text
                            "path" -> path = text
                            "ts" -> ts = text
                            "s" -> s = text
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    currentTag = ""
                }
                eventType = xpp.next()
            }
            if (host.isNotEmpty() && path.isNotEmpty() && ts.isNotEmpty() && s.isNotEmpty()) {
                return DownloadInfoXml(host, path, ts, s)
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadInfoParser", "Unexpected error", e)
        }
        return null
    }
}
