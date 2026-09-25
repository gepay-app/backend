package com.gepe.starter.identity.internal.cache;

import com.gepe.starter.identity.internal.config.IdentityCacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evict targeted principal cache by Firebase UID. Cache manager-nya
 * transaction-aware (`platform/config`), jadi kalau dipanggil dari dalam
 * transaksi, eviksi baru dieksekusi setelah commit — dan karena Redis dipakai
 * bersama, semua instance langsung melihatnya.
 *
 * <p>Setiap mutasi identity (grant/revoke role, disable/activate, ubah
 * email/nama nanti) WAJIB memanggil ini supaya perilaku user berubah seketika,
 * tanpa menunggu TTL.
 */
@Component
@RequiredArgsConstructor
public class PrincipalCache {

    private final CacheManager cacheManager;

    public void evict(String authId) {
        if (authId == null) {
            return; // user belum pernah login → tidak ada entry
        }
        Cache cache = cacheManager.getCache(IdentityCacheConfig.PRINCIPAL_BY_AUTH_ID);
        if (cache != null) {
            cache.evict(authId);
        }
    }
}
