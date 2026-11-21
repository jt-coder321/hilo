package com.hilotrainer.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.hilotrainer.R

enum class Fx { DEAL, FLIP, SHUFFLE, CHIP, WIN, BLACKJACK, LOSE, MISTAKE, CORRECT, CHATTER, BUZZ }

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
    ).mapValues { (_, res) -> pool.load(context, res, 1) }

    var soundOn = true
    var hapticsOn = true
    var view: View? = null

    fun play(fx: Fx) {
        if (soundOn) {
            val rate = when (fx) {
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
                Fx.DEAL, Fx.FLIP, Fx.CHIP -> HapticFeedbackConstants.CLOCK_TICK
                Fx.MISTAKE -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                Fx.BUZZ -> HapticFeedbackConstants.LONG_PRESS
                Fx.WIN, Fx.BLACKJACK, Fx.CORRECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                else -> return
            }
            v.performHapticFeedback(c)
        }
    }

    fun release() = pool.release()
}
