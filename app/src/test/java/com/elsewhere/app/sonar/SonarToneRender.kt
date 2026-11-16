package com.elsewhere.app.sonar

import java.io.File

/** Writes the ping as WAV for whoever encodes the bundled asset; run with `-Dsonar.wav=path`. */
object SonarToneRender {
    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: System.getProperty("sonar.wav") ?: "build/sonar.wav")
        out.parentFile?.mkdirs()
        out.writeBytes(SonarTone.wav())
        println("wrote ${out.length()} bytes to $out")
    }
}
