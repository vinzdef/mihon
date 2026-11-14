package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import android.graphics.Rect
import android.util.LruCache
import com.davemorrissey.labs.subscaleview.decoder.Decoder
import com.davemorrissey.labs.subscaleview.provider.OpenStreamProvider
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

class PanelDetector(
    private val context: Context,
    preferences: ReaderPreferences,
) {

    private val mutex = Mutex()
    private val foregroundRequests = MutableStateFlow(0)
    private val cache = LruCache<Any, List<Panel>>(CACHE_SIZE)
    private val mlDetector = MlPanelDetector(context, preferences)

    @Volatile
    private var generation = 0

    /**
     * @param isForeground whether the page is on screen, which goes before the other requests
     * @return panels in the coordinates of the cropped image, empty if none were found
     */
    suspend fun getPanels(
        key: Any,
        isRtl: Boolean,
        isForeground: Boolean,
        isBordersCropped: Boolean,
        openStream: () -> InputStream,
    ): List<Panel> {
        val cacheKey = Triple(key, isRtl, isBordersCropped)
        cache[cacheKey]?.let { return it }
        if (isForeground) {
            foregroundRequests.update { it + 1 }
        }
        try {
            while (true) {
                if (!isForeground) {
                    foregroundRequests.first { it == 0 }
                }
                mutex.withLock {
                    cache[cacheKey]?.let { return it }
                    if (isForeground || foregroundRequests.value == 0) {
                        // A long detection is kept even if the page is left
                        return withContext(NonCancellable) {
                            val startGeneration = generation
                            detect(openStream, isBordersCropped, isRtl).also {
                                if (startGeneration == generation) {
                                    cache.put(cacheKey, it)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (isForeground) {
                foregroundRequests.update { it - 1 }
            }
        }
    }

    fun clear() {
        generation++
        cache.evictAll()
    }

    private suspend fun detect(
        openStream: () -> InputStream,
        isBordersCropped: Boolean,
        isRtl: Boolean,
    ): List<Panel> {
        var fullWidth = 0
        var fullHeight = 0
        val page = withContext(Dispatchers.IO) {
            // The page view's decoder, so the coordinates match the cropped image
            val decoder = Decoder(isBordersCropped)
            try {
                val size = decoder.init(context, OpenStreamProvider(openStream()))
                fullWidth = size.x
                fullHeight = size.y
                var sampleSize = 1
                while (maxOf(fullWidth, fullHeight) / sampleSize > MAX_SIZE) {
                    sampleSize *= 2
                }
                decoder.decodeRegion(Rect(0, 0, fullWidth, fullHeight), sampleSize)
            } finally {
                decoder.recycle()
            }
        }
        return try {
            withContext(Dispatchers.Default) {
                val width = page.width
                val height = page.height
                val pixels = IntArray(width * height)
                page.getPixels(pixels, 0, width, 0, 0, width, height)
                val scaleX = fullWidth / width.toFloat()
                val scaleY = fullHeight / height.toFloat()
                var panels = PanelSplitter.split(pixels, width, height, isRtl)
                var isFromModel = false
                val startTime = System.currentTimeMillis()
                if (panels.isEmpty() && mlDetector.isAvailable) {
                    panels = mlDetector.detect(page, isRtl)
                    isFromModel = true
                }
                logcat {
                    "page ${fullWidth}x$fullHeight sampled to ${width}x$height: ${panels.size} panels $panels" +
                        if (isFromModel) " by model in ${System.currentTimeMillis() - startTime}ms" else ""
                }
                panels.map {
                    Panel(
                        left = (it.left * scaleX).toInt(),
                        top = (it.top * scaleY).toInt(),
                        right = (it.right * scaleX).toInt().coerceAtMost(fullWidth),
                        bottom = (it.bottom * scaleY).toInt().coerceAtMost(fullHeight),
                    )
                }
            }
        } finally {
            page.recycle()
        }
    }

    fun close() = mlDetector.close()

    private companion object {

        const val MAX_SIZE = 1024
        const val CACHE_SIZE = 24
    }
}
