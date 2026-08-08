package com.zlight106.nvvocab.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

class WebPronunciationPlayer internal constructor(
    internal val webView: WebView,
) {
    private var ready = false
    private var pendingWord: String? = null

    internal fun onReady() {
        ready = true
        pendingWord?.let {
            pendingWord = null
            speak(it)
        }
    }

    fun speak(word: String) {
        val normalized = word.trim()
        if (normalized.isEmpty()) return
        Log.i("WebPronunciation", "Pronunciation requested: $normalized (ready=$ready)")
        webView.post {
            if (!ready) {
                pendingWord = normalized
            } else {
                webView.evaluateJavascript("window.playWord(${JSONObject.quote(normalized)});", null)
            }
        }
    }

    internal fun release() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun rememberWebPronunciationPlayer(
    onFailure: (String) -> Unit,
): WebPronunciationPlayer {
    val context = LocalContext.current
    val currentFailure = rememberUpdatedState(onFailure)
    val player = remember(context) {
        lateinit var createdPlayer: WebPronunciationPlayer
        val webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun failed(word: String) {
                        Log.w("WebPronunciation", "Online pronunciation failed: $word")
                        Handler(Looper.getMainLooper()).post { currentFailure.value(word) }
                    }

                    @JavascriptInterface
                    fun started(word: String) {
                        Log.i("WebPronunciation", "Online pronunciation started: $word")
                    }

                },
                "AndroidSpeech",
            )
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    createdPlayer.onReady()
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            }
        }
        createdPlayer = WebPronunciationPlayer(webView)
        webView.loadDataWithBaseURL(
            "https://api.dictionaryapi.dev/",
            PRONUNCIATION_PAGE,
            "text/html",
            "UTF-8",
            null,
        )
        createdPlayer
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
fun WebPronunciationHost(
    player: WebPronunciationPlayer,
    modifier: Modifier = Modifier,
) {
    // Audio-capable WebViews must remain attached to the view hierarchy. The 1dp host is
    // intentionally transparent and does not take meaningful layout space.
    AndroidView(
        factory = { player.webView },
        modifier = modifier.size(1.dp).alpha(0.01f),
    )
}

private const val PRONUNCIATION_PAGE = """
<!doctype html>
<html><head><meta charset="utf-8"></head><body>
<script>
let currentAudio = null;
function playAudio(url, word, timeoutMs) {
  return new Promise((resolve, reject) => {
    currentAudio = new Audio(url);
    currentAudio.playbackRate = 0.9;
    let settled = false;
    const finish = (callback) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      callback();
    };
    const timer = setTimeout(() => {
      currentAudio.pause();
      finish(() => reject(new Error('audio timeout')));
    }, timeoutMs);
    currentAudio.onplaying = () => finish(resolve);
    currentAudio.onerror = () => finish(() => reject(new Error('audio unavailable')));
    const playAttempt = currentAudio.play();
    if (playAttempt) playAttempt.catch(error => finish(() => reject(error)));
  });
}

async function playWord(word) {
  try {
    if (currentAudio) { currentAudio.pause(); currentAudio = null; }
    const domesticAudio = 'https://dict.youdao.com/dictvoice?audio=' +
      encodeURIComponent(word) + '&type=2';
    await playAudio(domesticAudio, word, 8000);
    AndroidSpeech.started(word);
    return;
  } catch (domesticError) {
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch('/api/v2/entries/en/' + encodeURIComponent(word), {
      signal: controller.signal
    });
    clearTimeout(timeout);
    if (!response.ok) throw new Error('lookup failed');
    const entries = await response.json();
    let audioUrl = '';
    for (const entry of entries) {
      for (const phonetic of (entry.phonetics || [])) {
        if (phonetic.audio) { audioUrl = phonetic.audio; break; }
      }
      if (audioUrl) break;
    }
    if (!audioUrl) throw new Error('no pronunciation');
    if (audioUrl.startsWith('//')) audioUrl = 'https:' + audioUrl;
    await playAudio(audioUrl, word, 8000);
    AndroidSpeech.started(word);
  } catch (error) {
    clearTimeout(timeout);
    AndroidSpeech.failed(word);
  }
}
</script>
</body></html>
"""
