package com.elsewhere.app.sonar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sonar ping, rendered sample by sample from the same recipe as the web's `playSonar`: two sines with a
 * 3 % downward glide, a 6 kHz low-pass, one feedback echo and a hard fade by 1.2 s. Pure maths, no Android.
 */
object SonarTone {
    const val SAMPLE_RATE = 48_000
    const val FRAMES = 57_600
    /** −6 dBFS: noticeable, never startling. */
    private const val PEAK = 0.5

    /** Mono float samples in −1..1, [FRAMES] long. */
    fun render(): FloatArray {
        val dry = DoubleArray(FRAMES)
        tone(dry, 1046.0, 0.0, 0.09, 0.70, 1.0)
        tone(dry, 1568.0, 0.11, 0.06, 0.90, 0.7)
        val filtered = lowpass(dry, 6000.0, 0.7)
        val out = DoubleArray(FRAMES)
        val delayN = (0.18 * SAMPLE_RATE).toInt()
        val buf = DoubleArray(delayN)
        var idx = 0
        for (i in 0 until FRAMES) {
            val d = buf[idx]
            buf[idx] = filtered[i] + d * 0.32
            idx = (idx + 1) % delayN
            val t = i / SAMPLE_RATE.toDouble()
            val gain = when {
                t < 1.1 -> 0.5
                t < 1.2 -> 0.5 * (1.2 - t) / 0.1
                else -> 0.0
            }
            out[i] = (filtered[i] + d * 0.25) * gain
        }
        var peak = 0.0
        for (v in out) if (Math.abs(v) > peak) peak = Math.abs(v)
        val norm = if (peak > 0) PEAK / peak else 1.0
        return FloatArray(FRAMES) { (out[it] * norm).toFloat() }
    }

    /** Little-endian signed 16-bit PCM of [render]. */
    fun pcm16(): ShortArray {
        val f = render()
        return ShortArray(f.size) { (f[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    }

    private fun tone(into: DoubleArray, f: Double, t0: Double, hold: Double, decay: Double, level: Double) {
        val start = (t0 * SAMPLE_RATE).toInt()
        val end = minOf(FRAMES, ((t0 + hold + decay + 0.05) * SAMPLE_RATE).toInt())
        val glideEnd = hold + decay
        val k = ln(0.001) / decay
        var phase = 0.0
        for (i in start until end) {
            val t = (i - start) / SAMPLE_RATE.toDouble()
            val frac = (t / glideEnd).coerceIn(0.0, 1.0)
            val freq = f * Math.pow(0.97, frac)
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = when {
                t < 0.004 -> level * t / 0.004
                t < hold -> level
                else -> level * exp(k * (t - hold))
            }
            into[i] += sin(phase) * env
        }
    }

    /** RBJ biquad low-pass. */
    private fun lowpass(x: DoubleArray, fc: Double, q: Double): DoubleArray {
        val w0 = 2 * PI * fc / SAMPLE_RATE
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val b0 = (1 - c) / 2; val b1 = 1 - c; val b2 = (1 - c) / 2
        val a0 = 1 + alpha; val a1 = -2 * c; val a2 = 1 - alpha
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = (b0 / a0) * x[i] + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v
            y[i] = v
        }
        return y
    }

    /** RIFF/WAVE bytes of [pcm16], handy for producing the bundled asset. */
    fun wav(): ByteArray {
        val pcm = pcm16()
        val data = pcm.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (s in pcm) b.putShort(s)
        return b.array()
    }

    fun peakDbfs(samples: FloatArray): Double {
        var peak = 0.0
        for (s in samples) if (Math.abs(s.toDouble()) > peak) peak = Math.abs(s.toDouble())
        return 20 * Math.log10(peak.coerceAtLeast(1e-9))
    }

    fun rmsDbfs(samples: FloatArray): Double {
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return 20 * Math.log10(sqrt(sum / samples.size).coerceAtLeast(1e-9))
    }
}
