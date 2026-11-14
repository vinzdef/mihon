package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class PanelModelDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val model = PanelModel.entries.find { it.name == inputData.getString(KEY_MODEL) } ?: return Result.failure()
        val file = model.getFile(context)
        val tempFile = File(file.parentFile, file.name + ".tmp")
        return try {
            showProgress(0)
            withContext(Dispatchers.IO) {
                download(model, tempFile)
                check(tempFile.renameTo(file)) { "Cannot save the model" }
            }
            Result.success()
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to download the panel detection model" }
            tempFile.delete()
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

    private suspend fun download(model: PanelModel, target: File) {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        context.appGraph.networkHelper.client.newCall(GET(model.downloadUrl)).awaitSuccess().use { response ->
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: model.downloadSize
            val input = CountingInputStream(body.byteStream())
            val content = if (model.isArchive) {
                ZipInputStream(input).also { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && !entry.name.endsWith(MODEL_EXTENSION)) {
                        entry = zip.nextEntry
                    }
                    checkNotNull(entry) { "There is no model in the archive" }
                }
            } else {
                input
            }
            content.use {
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastPercent = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = content.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val percent = (input.count * 100 / total).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            showProgress(percent)
                        }
                    }
                }
            }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        check(checksum == model.sha256) { "The model is corrupted: $checksum" }
    }

    private suspend fun showProgress(percent: Int) {
        setProgress(workDataOf(KEY_PROGRESS to percent))
        try {
            setForeground(createForegroundInfo(percent))
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }
    }

    private fun createForegroundInfo(percent: Int): ForegroundInfo {
        val notification = context.notificationBuilder(Notifications.CHANNEL_COMMON) {
            setContentTitle(context.stringResource(MR.strings.pref_panel_model))
            setContentText(context.stringResource(MR.strings.pref_panel_model_downloading, percent))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(100, percent, false)
            addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.stringResource(MR.strings.action_cancel),
                context.workManager.createCancelPendingIntent(id),
            )
        }.build()
        return ForegroundInfo(
            Notifications.ID_PANEL_MODEL_PROGRESS,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {

        var count = 0L
            private set

        override fun read(): Int = super.read().also {
            if (it >= 0) count++
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it > 0) count += it
        }
    }

    companion object {

        const val KEY_PROGRESS = "progress"

        fun start(context: Context, model: PanelModel) {
            val request = OneTimeWorkRequestBuilder<PanelModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL to model.name))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            context.workManager.enqueueUniqueWork(NAME + model.name, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, model: PanelModel) {
            context.workManager.cancelUniqueWork(NAME + model.name)
        }

        fun observe(context: Context, model: PanelModel): Flow<WorkInfo?> {
            return context.workManager.getWorkInfosForUniqueWorkFlow(NAME + model.name)
                .map { it.lastOrNull() }
        }
    }
}

private const val KEY_MODEL = "model"
private const val NAME = "panel_model_"
private const val MODEL_EXTENSION = ".onnx"
private const val BUFFER_SIZE = 64 * 1024
