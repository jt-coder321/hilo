package com.hilotrainer.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.hilotrainer.R

enum class Fx { DEAL, FLIP, SHUFFLE, CHIP, WIN, BLACKJACK, LOSE, MISTAKE, CORRECT, CHATTER, BUZZ, RG_TICK, RG_CHIME, RG_SLAM, RG_COIN, RG_FANFARE, RG_BUST, RG_BUY }

/** Plays the synthesized table sounds and matching vibration. */
class SoundFx(context: Context) {
    private val vibrator: android.os.Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
    } catch (e: Exception) { null }

    /**
     * Richer haptics: each table event gets its own short waveform (timings in ms, amplitudes 0 to 255).
     * A dealt card is one light tap, chips are a double click, a shuffle is a riffle of ticks,
     * a win is a rising pulse and a blackjack a longer celebration.
     */
    internal fun waveform(fx: Fx): Pair<LongArray, IntArray>? = when (fx) {
        Fx.DEAL -> longArrayOf(0, 12) to intArrayOf(0, 70)
        Fx.FLIP -> longArrayOf(0, 8, 30, 10) to intArrayOf(0, 60, 0, 90)
        Fx.CHIP -> longArrayOf(0, 10, 35, 10) to intArrayOf(0, 120, 0, 80)
        Fx.SHUFFLE -> LongArray(16) { if (it % 2 == 0) 22 else 6 } to IntArray(16) { if (it % 2 == 0) 0 else 40 + it * 4 }
        Fx.WIN -> longArrayOf(0, 30, 40, 40, 40, 60) to intArrayOf(0, 80, 0, 140, 0, 200)
        Fx.BLACKJACK -> longArrayOf(0, 40, 30, 40, 30, 40, 50, 140) to intArrayOf(0, 120, 0, 160, 0, 200, 0, 255)
        Fx.LOSE -> longArrayOf(0, 90) to intArrayOf(0, 60)
        Fx.MISTAKE -> longArrayOf(0, 60, 60, 60) to intArrayOf(0, 200, 0, 200)
        Fx.CORRECT -> longArrayOf(0, 25) to intArrayOf(0, 150)
        Fx.CHATTER -> null
        // A phone buzzing in your pocket.
        Fx.BUZZ -> longArrayOf(0, 180, 90, 180) to intArrayOf(0, 255, 0, 255)
        Fx.RG_TICK -> longArrayOf(0, 8) to intArrayOf(0, 60)
        Fx.RG_CHIME -> longArrayOf(0, 14, 20, 10) to intArrayOf(0, 110, 0, 70)
        Fx.RG_SLAM -> longArrayOf(0, 30, 20, 70) to intArrayOf(0, 255, 0, 160)
        Fx.RG_COIN -> longArrayOf(0, 12, 30, 12) to intArrayOf(0, 120, 0, 100)
        Fx.RG_FANFARE -> longArrayOf(0, 25, 40, 25, 40, 25, 40, 120) to intArrayOf(0, 90, 0, 130, 0, 170, 0, 255)
        Fx.RG_BUST -> longArrayOf(0, 120) to intArrayOf(0, 90)
        Fx.RG_BUY -> longArrayOf(0, 20, 30, 30) to intArrayOf(0, 120, 0, 180)
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Fx, Int> = mapOf(
        Fx.DEAL to R.raw.card_deal,
        Fx.FLIP to R.raw.card_flip,
        Fx.SHUFFLE to R.raw.shuffle,
        Fx.CHIP to R.raw.chip,
        Fx.WIN to R.raw.win,
        Fx.BLACKJACK to R.raw.blackjack,
        Fx.LOSE to R.raw.lose,
        Fx.MISTAKE to R.raw.mistake,
        Fx.CORRECT to R.raw.correct,
        Fx.CHATTER to R.raw.chatter,
        Fx.RG_TICK to R.raw.rg_tick,
        Fx.RG_CHIME to R.raw.rg_chime,
        Fx.RG_SLAM to R.raw.rg_slam,
        Fx.RG_COIN to R.raw.rg_coin,
        Fx.RG_FANFARE to R.raw.rg_fanfare,
        Fx.RG_BUST to R.raw.rg_bust,
        Fx.RG_BUY to R.raw.rg_buy,
    ).mapValues { (_, res) -> pool.load(context, res, 1) }

    var soundOn = true
    var hapticsOn = true
    var view: View? = null

    fun play(fx: Fx, pitch: Float = 1f) {
        if (soundOn) {
            val rate = pitch * when (fx) {
                Fx.DEAL -> 0.94f + Math.random().toFloat() * 0.12f
                // Chips and voices vary so table noise never sounds the same twice.
                Fx.CHIP, Fx.CHATTER -> 0.8f + Math.random().toFloat() * 0.45f
                else -> 1f
            }
            ids[fx]?.let {
                val pan = if (fx == Fx.CHIP || fx == Fx.CHATTER) Math.random().toFloat() else 0.5f
                pool.play(it, 1f - pan * 0.6f, 0.4f + pan * 0.6f, 1, 0, rate)
            }
        }
        if (hapticsOn) {
            val vib = vibrator
            val wf = waveform(fx)
            if (vib != null && wf != null && vib.hasVibrator() && vib.hasAmplitudeControl()) {
                try { vib.vibrate(android.os.VibrationEffect.createWaveform(wf.first, wf.second, -1)); return } catch (e: Exception) { }
            }
            val v = view ?: return
            val c = when (fx) {
                Fx.DEAL, Fx.FLIP, Fx.CHIP, Fx.RG_TICK, Fx.RG_CHIME, Fx.RG_COIN, Fx.RG_BUY -> HapticFeedbackConstants.CLOCK_TICK
                Fx.MISTAKE -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                Fx.BUZZ -> HapticFeedbackConstants.LONG_PRESS
                Fx.WIN, Fx.BLACKJACK, Fx.CORRECT, Fx.RG_FANFARE, Fx.RG_SLAM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                else -> return
            }
            v.performHapticFeedback(c)
        }
    }

    fun release() = pool.release()
}
