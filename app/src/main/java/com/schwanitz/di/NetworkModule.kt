package com.schwanitz.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val dnsExecutor = Executors.newCachedThreadPool { r ->
            Thread(r, "DnsLookup").also { it.isDaemon = true }
        }
        val timeoutDns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val future = dnsExecutor.submit(Callable {
                    Dns.SYSTEM.lookup(hostname)
                })
                try {
                    return future.get(10, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    throw UnknownHostException("DNS lookup timed out for $hostname")
                }
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(timeoutDns)
            .build()
    }
}
