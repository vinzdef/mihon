package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import java.io.File

/**
 * RF-DETR ONNX models for [MlPanelDetector], downloaded on demand as they're too large to bundle.
 */
enum class PanelModel(
    val title: String,
    val credits: String,
    val downloadUrl: String,
    val downloadSize: Long,
    val sha256: String,
    val isArchive: Boolean,
    val minScore: Float,
    /**
     * Null if the model isn't known to detect text.
     */
    val minBalloonScore: Float?,
) {

    MANGA109(
        title = "Manga Panel Detector",
        credits = "Panel detection by Manga Panel Detector (forkger), trained on Manga109-s",
        downloadUrl = "https://huggingface.co/forkger/manga-panel-rfdetr/resolve/main/manga-panel-rfdetr-med-576.onnx",
        downloadSize = 120_592_688L,
        sha256 = "38d42cd9717359914aa40376ec554c7741e4abf37c989ce8a16e6c7c9bc37a29",
        isArchive = false,
        minScore = 0.4f,
        minBalloonScore = 0.5f,
    ),
    KOMELIA_MEDIUM(
        title = "Komelia Medium",
        credits = "Panel detection model by the Komelia project",
        downloadUrl = "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/rf-detr-med.onnx.zip",
        downloadSize = 111_639_310L,
        sha256 = "e959252609ac05ef58de685df8eee639eda6ef65131559e8bb27c981d2463f52",
        isArchive = true,
        minScore = 0.5f,
        minBalloonScore = null,
    ),
    KOMELIA_NANO(
        title = "Komelia Nano",
        credits = "Panel detection model by the Komelia project",
        downloadUrl = "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/rf-detr-nano.onnx.zip",
        downloadSize = 99_691_757L,
        sha256 = "232551517f1ee9f06da018edcab986686a4756c3a23f2aa9d25fc7feb8637e74",
        isArchive = true,
        minScore = 0.5f,
        minBalloonScore = null,
    ),
    ;

    fun getFile(context: Context): File {
        return File(context.getExternalFilesDir(null) ?: context.filesDir, "models/${name.lowercase()}.onnx")
    }

    fun isDownloaded(context: Context) = getFile(context).isFile
}
