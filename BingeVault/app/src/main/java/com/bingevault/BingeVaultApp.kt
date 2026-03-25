package com.bingevault

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.ads.MobileAds
import okhttp3.OkHttpClient

class BingeVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        configureCoil()
        MobileAds.initialize(this) {
            // Preload the first rewarded ad once the SDK is ready
            AdManager.preloadRewarded(this)
        }
    }

    private fun configureCoil() {
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this).maxSizePercent(0.15).build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_posters"))
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .okHttpClient {
                    OkHttpClient.Builder().retryOnConnectionFailure(false).build()
                }
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
        )
    }
}
