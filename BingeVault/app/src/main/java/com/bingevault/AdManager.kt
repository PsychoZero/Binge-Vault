package com.bingevault

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Ad unit IDs below are Google's official TEST IDs.
 * Replace them with your real IDs from admob.google.com before publishing.
 *
 * Test banner ID  : ca-app-pub-3940256099942544/6300978111
 * Test rewarded ID: ca-app-pub-3940256099942544/5224354917
 */
object AdManager {

    // ── Replace these with your real Ad Unit IDs ──────────────────────────────
    const val BANNER_ID   = "ca-app-pub-3940256099942544/6300978111"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null
    private var isLoading  = false

    // ── Preload a rewarded ad so it's ready when user taps the button ─────────
    fun preloadRewarded(context: Context) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        RewardedAd.load(
            context,
            REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading  = false
                    Log.d("AdManager", "Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading  = false
                    Log.w("AdManager", "Rewarded ad failed: ${error.message}")
                }
            }
        )
    }

    // ── Show rewarded ad; onRewarded called only if user watches to completion ─
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewarded(activity)   // preload next one immediately
                onDismissed()
            }
        }
        ad.show(activity) { onRewarded() }
    }

    fun isRewardedReady() = rewardedAd != null
}
