package com.grabbit.ui.convert

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.MimeTypes
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

@HiltViewModel
class ConvertViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ffmpeg: FfmpegRunner,
    private val saver: MediaSaver,
) : ViewModel() {

    data class PickedFile(
        val uri: Uri,
        val file: File,
        val name: String,
        val size: Long,
        val mimeType: String,
    )

    data class UiState(
        val picked: PickedFile? = null,
        val target: ConvertFormat = ConvertFormat.MP3,
        val bitrate: Int = 192,
        val running: Boolean = false,
        val progress: Int = 0,
        val resultUri: String? = null,
        val resultName: String? = null,
        val errorRes: Int? = null,
        val errorDetail: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun pick(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(uri) ?: "input.bin"
                    val dir = File(context.cacheDir, "convert").apply { mkdirs() }
                    val file = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("cannot open the selected file")
                    val mime = context.contentResolver.getType(uri) ?: MimeTypes.forExt(file.extension)
                    PickedFile(uri, file, name, file.length(), mime)
                }.getOrNull()
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

    fun setTarget(target: ConvertFormat) {
        _state.update { it.copy(target = target, resultUri = null, resultName = null) }
    }

    fun setBitrate(bitrate: Int) {
        _state.update { it.copy(bitrate = bitrate) }
    }

    fun cancel() {
        ffmpeg.cancel(CONVERT_KEY)
        _state.update { it.copy(running = false) }
    }

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
            val result = ffmpeg.convert(
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
                    val saved = runCatching {
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
                                    progress = 100,
                                    resultUri = file.uri.toString(),
                                    resultName = file.displayName,
                                )
                            }
                        },
                        onFailure = { e ->
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

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private companion object {
        const val CONVERT_KEY = "convert-screen"
    }
}
