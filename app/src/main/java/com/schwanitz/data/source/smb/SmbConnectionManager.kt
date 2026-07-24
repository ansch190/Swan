package com.schwanitz.data.source.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import timber.log.Timber
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbConnectionManager @Inject constructor() {

    fun connect(host: String, username: String, password: String, domain: String = ""): Session {
        val config = SmbConfig.createDefaultConfig()
        val client = SMBClient(config)
        val connection = client.connect(host)

        val ac = if (username.isBlank() && password.isBlank()) {
            Timber.d("SMB anonymous auth to %s", host)
            AuthenticationContext("", charArrayOf(), "")
        } else {
            Timber.d("SMB auth to %s user=%s", host, username)
            AuthenticationContext(username, password.toCharArray(), domain)
        }

        return connection.authenticate(ac)
    }

    fun openFile(
        session: Session,
        shareName: String,
        filePath: String
    ): File {
        val share = session.connectShare(shareName) as DiskShare
        return share.openFile(
            filePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }
}
