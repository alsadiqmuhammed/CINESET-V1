package com.madar.launcher.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.VibratorManager

/**
 * Tactile physics: light taps for light tasks, a heavy low thud for things that cost attention,
 * and a crisp double click when something is done.
 */
class Haptics(context: Context) {

    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    fun tick() = play(Composition.PRIMITIVE_TICK, 0.5f) { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }

    fun light() = play(Composition.PRIMITIVE_CLICK, 0.6f) { VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) }

    /** Heavy, resistant feedback for gated or high-cost actions. */
    fun heavy() {
        if (vibrator.areAllPrimitivesSupported(Composition.PRIMITIVE_THUD, Composition.PRIMITIVE_LOW_TICK)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(Composition.PRIMITIVE_THUD, 1f)
                    .addPrimitive(Composition.PRIMITIVE_LOW_TICK, 0.6f, 60)
                    .compose(),
            )
        } else {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        }
    }

    /** Sharp mechanical "done" click, like a physical switch. */
    fun completion() {
        if (vibrator.areAllPrimitivesSupported(Composition.PRIMITIVE_CLICK)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(Composition.PRIMITIVE_CLICK, 1f)
                    .addPrimitive(Composition.PRIMITIVE_CLICK, 0.8f, 70)
                    .compose(),
            )
        } else {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        }
    }

    /** Rising spin used when the home screen morphs between modes. */
    fun morph() = play(Composition.PRIMITIVE_QUICK_RISE, 0.7f) { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }

    private fun play(primitive: Int, scale: Float, fallback: () -> VibrationEffect) {
        if (vibrator.areAllPrimitivesSupported(primitive)) {
            vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
        } else {
            vibrator.vibrate(fallback())
        }
    }
}
