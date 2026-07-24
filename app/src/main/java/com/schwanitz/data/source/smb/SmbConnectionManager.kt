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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbConnectionManager @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, Session>()
    private val clients = ConcurrentHashMap<String, SMBClient>()
    private val shareCache = ConcurrentHashMap<String, DiskShare>()

    private fun sessionKey(host: String, username: String, domain: String): String {
        return "$host|$username|$domain"
    }

    fun connect(host: String, username: String, password: String, domain: String = ""): Session {
        val key = sessionKey(host, username, domain)
        val existing = sessions[key]
        if (existing != null) {
            Timber.d("SMB reusing session for %s", host)
            return existing
        }
        return createSession(host, username, password, domain, key)
    }

    fun invalidateSession(host: String, username: String, domain: String = "") {
        val key = sessionKey(host, username, domain)
        Timber.w("SMB invalidating session for %s", host)
        shareCache.keys.filter { it.startsWith("$key|") }.forEach {
            try { shareCache.remove(it)?.close() } catch (_: Exception) {}
        }
        try { sessions.remove(key)?.close() } catch (_: Exception) {}
        try { clients.remove(key)?.close() } catch (_: Exception) {}
    }

    fun reconnect(host: String, username: String, password: String, domain: String = ""): Session {
        invalidateSession(host, username, domain)
        val key = sessionKey(host, username, domain)
        return createSession(host, username, password, domain, key)
    }

    private fun createSession(host: String, username: String, password: String, domain: String, key: String): Session {
        Timber.d("SMB new session for %s (user=%s)", host, username)
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

        val session = connection.authenticate(ac)
        sessions[key] = session
        clients[key] = client
        return session
    }

    fun getShare(session: Session, shareName: String): DiskShare {
        val sessionKey = sessions.entries.find { it.value === session }?.key ?: ""
        val shareKey = "$sessionKey|$shareName"
        val existing = shareCache[shareKey]
        if (existing != null) {
            return existing
        }
        val share = session.connectShare(shareName) as DiskShare
        shareCache[shareKey] = share
        return share
    }

    fun openFile(
        session: Session,
        shareName: String,
        filePath: String
    ): File {
        val share = getShare(session, shareName)
        return share.openFile(
            filePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    fun closeAll() {
        try {
            shareCache.values.forEach { try { it.close() } catch (_: Exception) {} }
            shareCache.clear()
            sessions.values.forEach { try { it.close() } catch (_: Exception) {} }
            sessions.clear()
            clients.values.forEach { try { it.close() } catch (_: Exception) {} }
            clients.clear()
        } catch (_: Exception) {}
    }
}
