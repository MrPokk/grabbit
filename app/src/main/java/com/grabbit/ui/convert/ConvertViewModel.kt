package com.grabbit.ui.convert

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Defaults
import com.grabbit.core.Formatting
import com.grabbit.core.MimeTypes
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.convert.FfmpegRunner
import com.grabbit.data.download.ConvertFormat
import com.grabbit.data.download.MediaType
import com.grabbit.data.media.MediaSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "ConvertViewModel"
private const val CONVERT_KEY = "convert-screen"

/** Owns file picking, FFmpeg conversion and saving the result for the convert screen. */
@HiltViewModel
class ConvertViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ffmpeg: FfmpegRunner,
        private val saver: MediaSaver,
    ) : ViewModel() {
        /** A user-picked file copied into the app cache. */
        data class PickedFile(
            val uri: Uri,
            val file: File,
            val name: String,
            val size: Long,
            val mimeType: String,
        )

        /** Everything the convert screen needs to render its current state. */
        data class UiState(
            val picked: PickedFile? = null,
            val target: ConvertFormat = ConvertFormat.MP3,
            val bitrate: Int = Defaults.AUDIO_BITRATE_KBPS,
            val running: Boolean = false,
            val progress: Int = 0,
            val resultUri: String? = null,
            val resultName: String? = null,
            val errorRes: Int? = null,
            val errorDetail: String? = null,
        )

        private val _state = MutableStateFlow(UiState())
        val state: StateFlow<UiState> = _state.asStateFlow()

        /** Copies the picked file into the app cache and guesses a conversion target. */
        fun pick(uri: Uri) {
            viewModelScope.launch {
                val picked =
                    withContext(Dispatchers.IO) {
                        suspendRunCatching {
                            val name = displayName(uri) ?: "input.bin"
                            val dir = File(context.cacheDir, "convert").apply { mkdirs() }
                            val file = File(dir, name)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("cannot open the selected file")
                            val mime = context.contentResolver.getType(uri) ?: MimeTypes.forExt(file.extension)
                            PickedFile(uri, file, name, file.length(), mime)
                        }.onFailure { e -> AppLog.e(TAG, "could not copy the picked file", e) }
                            .getOrNull()
                    }
                if (picked == null) {
                    _state.update { it.copy(errorRes = R.string.convert_copy_failed, errorDetail = null) }
                } else {
                    val audio = MimeTypes.isAudio(picked.mimeType)
                    _state.update {
                        it.copy(
                            picked = picked,
                            target = if (audio) ConvertFormat.MP3 else ConvertFormat.MP4,
                            errorRes = null,
                            errorDetail = null,
                            resultUri = null,
                            resultName = null,
                            progress = 0,
                        )
                    }
                }
            }
        }

        /** Selects the target format and discards a previous result. */
        fun setTarget(target: ConvertFormat) {
            _state.update { it.copy(target = target, resultUri = null, resultName = null) }
        }

        /** Selects the audio bitrate for the conversion. */
        fun setBitrate(bitrate: Int) {
            _state.update { it.copy(bitrate = bitrate) }
        }

        /** Stops a conversion in progress. */
        fun cancel() {
            ffmpeg.cancel(CONVERT_KEY)
            _state.update { it.copy(running = false) }
        }

        /** Converts the picked file into the selected target and saves it to storage. */
        fun convert() {
            val state = _state.value
            val picked = state.picked ?: return
            if (state.running) return
            _state.update {
                it.copy(
                    running = true,
                    progress = 0,
                    errorRes = null,
                    errorDetail = null,
                    resultUri = null,
                    resultName = null,
                )
            }

            viewModelScope.launch {
                val baseName = picked.name.substringBeforeLast('.', picked.name)
                val output = File(picked.file.parentFile, "$baseName.${state.target.ext}")
                output.delete()
                val result =
                    ffmpeg.convert(
                        input = picked.file,
                        output = output,
                        target = state.target,
                        bitrate = state.bitrate,
                        key = CONVERT_KEY,
                    ) { percent ->
                        _state.update { it.copy(progress = percent) }
                    }

                result.fold(
                    onSuccess = {
                        val saved =
                            suspendRunCatching {
                                saver.save(
                                    source = output,
                                    mediaType = if (state.target.audioOnly) MediaType.AUDIO else MediaType.VIDEO,
                                    mimeType = state.target.mimeType,
                                    displayName = output.name,
                                    customFolder = null,
                                )
                            }
                        output.delete()
                        saved.fold(
                            onSuccess = { file ->
                                _state.update {
                                    it.copy(
                                        running = false,
                                        progress = Formatting.PROGRESS_MAX_PERCENT,
                                        resultUri = file.uri.toString(),
                                        resultName = file.displayName,
                                    )
                                }
                            },
                            onFailure = { e ->
                                AppLog.e(TAG, "could not save the converted file", e)
                                _state.update {
                                    it.copy(
                                        running = false,
                                        errorRes = R.string.convert_failed,
                                        errorDetail = e.message,
                                    )
                                }
                            },
                        )
                    },
                    onFailure = { e ->
                        AppLog.e(TAG, "conversion failed", e)
                        _state.update {
                            it.copy(
                                running = false,
                                errorRes = R.string.convert_failed,
                                errorDetail = e.message,
                            )
                        }
                    },
                )
            }
        }

        private fun displayName(uri: Uri): String? =
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not read the display name of a picked file", e)
                null
            } catch (e: IllegalArgumentException) {
                AppLog.e(TAG, "could not read the display name of a picked file", e)
                null
            }
    }
