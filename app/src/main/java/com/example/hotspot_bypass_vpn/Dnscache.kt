package com.example.hotspot_bypass_vpn

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS Cache to avoid repeated slow lookups
 * This can reduce DNS query time from 500ms+ to <1ms for cached entries
 */
class DnsCache {
    private data class CacheEntry(
        val address: ByteArray,
        val timestamp: Long,
        val ttl: Long = TimeUnit.MINUTES.toMillis(5) // 5 minute default TTL
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // Pre-populate with common domains
    init {
        // These will be resolved on first use, then cached
        preWarm(listOf(
            "www.google.com",
            "www.facebook.com",
            "www.instagram.com",
            "www.youtube.com",
            "api.instagram.com",
            "i.instagram.com"
        ))
    }

    private fun preWarm(domains: List<String>) {
        Thread {
            domains.forEach { domain ->
                try {
                    val addr = InetAddress.getByName(domain)
                    put(domain, addr.address)
                } catch (e: Exception) {
                    // Ignore pre-warm failures
                }
            }
        }.start()
    }

    fun get(domain: String): ByteArray? {
        val entry = cache[domain]
        return if (entry != null && !entry.isExpired()) {
            entry.address
        } else {
            cache.remove(domain)
            null
        }
    }

    fun put(domain: String, address: ByteArray, ttl: Long = TimeUnit.MINUTES.toMillis(5)) {
        cache[domain] = CacheEntry(address, System.currentTimeMillis(), ttl)
    }

    fun clear() {
        cache.clear()
    }

    fun size() = cache.size
}