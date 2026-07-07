package com.schwanitz.ui.screens.settings

data class WebDavProvider(
    val label: String,
    val url: String,
    val usernameHint: String = "Username",
    val path: String = "",
    val notes: String = ""
) {
    companion object {
        val PRESETS = listOf(
            WebDavProvider("pCloud (US)", "https://webdav.pcloud.com", "pCloud Email", "/", "WebDAV requires a paid plan; No 2FA possible"),
            WebDavProvider("pCloud (EU)", "https://ewebdav.pcloud.com", "pCloud Email", "/", "WebDAV requires a paid plan; No 2FA possible"),
            WebDavProvider("Koofr", "https://app.koofr.net/dav/Koofr", "Koofr Email", "/", "Separate app password required"),
            WebDavProvider("GMX MediaCenter", "https://webdav.mc.gmx.net", "GMX Email", "/", ""),
            WebDavProvider("WEB.DE", "https://webdav.smartdrive.web.de", "WEB.DE Email", "/", ""),
            WebDavProvider("Mailbox.org", "https://dav.mailbox.org/servlet/webdav.infostore/", "Mailbox.org Email", "/", "")
        )
    }
}
