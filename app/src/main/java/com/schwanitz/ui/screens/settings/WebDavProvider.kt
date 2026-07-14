package com.schwanitz.ui.screens.settings

import androidx.annotation.StringRes
import com.schwanitz.R

data class WebDavProvider(
    val label: String,
    val url: String,
    @param:StringRes val usernameHintRes: Int = R.string.webdav_username_hint,
    val path: String = "",
    @param:StringRes val notesRes: Int = 0
) {
    companion object {
        val PRESETS = listOf(
            WebDavProvider("pCloud (US)", "https://webdav.pcloud.com", R.string.webdav_pcloud_hint, "/", R.string.webdav_pcloud_note),
            WebDavProvider("pCloud (EU)", "https://ewebdav.pcloud.com", R.string.webdav_pcloud_hint, "/", R.string.webdav_pcloud_note),
            WebDavProvider("Koofr", "https://app.koofr.net/dav/Koofr", R.string.webdav_koofr_hint, "/", R.string.webdav_koofr_note),
            WebDavProvider("GMX MediaCenter", "https://webdav.mc.gmx.net", R.string.webdav_gmx_hint, "/"),
            WebDavProvider("WEB.DE", "https://webdav.smartdrive.web.de", R.string.webdav_webde_hint, "/"),
            WebDavProvider("Mailbox.org", "https://dav.mailbox.org/servlet/webdav.infostore/", R.string.webdav_mailbox_hint, "/")
        )
    }
}
