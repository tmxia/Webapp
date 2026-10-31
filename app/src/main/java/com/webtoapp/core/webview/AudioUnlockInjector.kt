package com.webtoapp.core.webview

import android.webkit.WebView




















object AudioUnlockInjector {

    internal const val UNLOCK_JS = """
        (function() {
            'use strict';
            if (window.__wta_audio_unlocked__) return;
            window.__wta_audio_unlocked__ = true;

            // 0.1s of silence, 8-bit mono PCM @ 8kHz, Base64. Tiny but valid so <audio>.play()
            // counts as real media playback and lifts the gesture gate for the session.
            var SILENCE_WAV = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';

            function unlock() {
                try {
                    var AudioCtx = window.AudioContext || window.webkitAudioContext;
                    if (AudioCtx) {
                        var ctx = new AudioCtx();
                        var osc = ctx.createOscillator();
                        var gain = ctx.createGain();
                        gain.gain.value = 0;
                        osc.connect(gain);
                        gain.connect(ctx.destination);
                        osc.start();
                        osc.stop(ctx.currentTime + 0.001);
                        if (ctx.state === 'suspended') { ctx.resume(); }
                    }
                } catch (e) {}
                try {
                    var a = new Audio(SILENCE_WAV);
                    a.muted = false;
                    a.volume = 0.0001;
                    var p = a.play();
                    if (p && typeof p.then === 'function') { p.catch(function () {}); }
                } catch (e) {}
                try {
                    window.removeEventListener('pointerdown', unlock, true);
                    window.removeEventListener('keydown', unlock, true);
                } catch (e) {}
            }

            try {
                window.addEventListener('pointerdown', unlock, true);
                window.addEventListener('keydown', unlock, true);
            } catch (e) {}
        })();
    """

    
    fun inject(webView: WebView) {
        webView.evaluateJavascript(UNLOCK_JS, null)
    }
}
