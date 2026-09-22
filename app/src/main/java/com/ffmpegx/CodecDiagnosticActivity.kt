package com.ffmpegx

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ffmpegx.databinding.ActivityCodecDiagnosticBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class CodecDiagnosticInfo(
    val name: String,
    val canonicalName: String,
    val isHardware: Boolean,
    val isVendor: Boolean,
    val isSoftwareOnly: Boolean,
    val mimeType: String,
    val profiles: List<String>,
    val levels: List<String>,
    val maxResolution: String,
    val maxFps: String,
    val bitrateRange: String,
    val bitrateModes: List<String>,
    val colorFormats: List<String>,
    val is10BitSupported: Boolean,
    val isHDR10Supported: Boolean,
    val isHDR10PlusSupported: Boolean
)

class CodecDiagnosticActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodecDiagnosticBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodecDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadDiagnostics()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvCodecs.layoutManager = LinearLayoutManager(this)
        binding.tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        binding.tvFFmpegSupport.text = getString(R.string.text_checking)
    }

    private fun loadDiagnostics() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = queryCodecs()
            val ffmpegSupport = checkFFmpegMediacodec(this@CodecDiagnosticActivity)
            withContext(Dispatchers.Main) {
                binding.tvFFmpegSupport.text = ffmpegSupport
                binding.rvCodecs.adapter = CodecAdapter(list)
            }
        }
    }

    private fun queryCodecs(): List<CodecDiagnosticInfo> {
        val list = mutableListOf<CodecDiagnosticInfo>()
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            
            val mimeTypes = info.supportedTypes
            val relevantMime = mimeTypes.find { it.equals("video/avc", true) || it.equals("video/hevc", true) }
                ?: continue
                
            val caps = info.getCapabilitiesForType(relevantMime)
            val videoCaps = caps.videoCapabilities ?: continue
            val encoderCaps = caps.encoderCapabilities
            
            val profiles = caps.profileLevels.map { getProfileName(relevantMime, it.profile) }.distinct()
            val levels = caps.profileLevels.map { getLevelName(relevantMime, it.level) }.distinct()
            
            val is10Bit = when {
                relevantMime.equals("video/hevc", true) -> {
                    caps.profileLevels.any { 
                        it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 || 
                        it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                        (Build.VERSION.SDK_INT >= 29 && it.profile == 0x1000)
                    }
                }
                relevantMime.equals("video/avc", true) -> {
                    caps.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 }
                }
                else -> false
            }
            
            val isHDR10 = caps.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 }
            val isHDR10Plus = Build.VERSION.SDK_INT >= 29 && caps.profileLevels.any { it.profile == 0x1000 }

            val bitrateModes = mutableListOf<String>()
            if (encoderCaps != null) {
                if (encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) bitrateModes.add("CBR")
                if (encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) bitrateModes.add("VBR")
                if (encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) bitrateModes.add("CQ")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD)) bitrateModes.add("CBR_FD")
            }

            val colorFormats = caps.colorFormats.map { getColorFormatName(it) }

            list.add(CodecDiagnosticInfo(
                name = info.name,
                canonicalName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.canonicalName else info.name,
                isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated else !info.name.lowercase().contains("google") && !info.name.lowercase().contains("android") && !info.name.lowercase().contains("soft"),
                isVendor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isVendor else false,
                isSoftwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isSoftwareOnly else info.name.lowercase().contains("google") || info.name.lowercase().contains("soft"),
                mimeType = relevantMime,
                profiles = profiles,
                levels = levels,
                maxResolution = "${videoCaps.supportedWidths.upper}x${videoCaps.supportedHeights.upper}",
                maxFps = "${videoCaps.supportedFrameRates.upper} fps",
                bitrateRange = "${videoCaps.bitrateRange.lower / 1000}k - ${videoCaps.bitrateRange.upper / 1000}k bps",
                bitrateModes = bitrateModes,
                colorFormats = colorFormats,
                is10BitSupported = is10Bit,
                isHDR10Supported = isHDR10,
                isHDR10PlusSupported = isHDR10Plus
            ))
        }
        return list.sortedByDescending { it.isHardware }
    }

    private fun checkFFmpegMediacodec(context: Context): String {
        val libraryDir = context.applicationInfo.nativeLibraryDir
        val binaryPath = "$libraryDir/libffmpeg.so"
        
        return try {
            val process = ProcessBuilder(binaryPath, "-encoders")
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.destroy()
            
            val h264 = output.contains("h264_mediacodec")
            val hevc = output.contains("hevc_mediacodec")
            
            val result = mutableListOf<String>()
            if (h264) result.add("h264_mediacodec (${getString(R.string.text_present)})") else result.add("h264_mediacodec (${getString(R.string.text_missing)})")
            if (hevc) result.add("hevc_mediacodec (${getString(R.string.text_present)})") else result.add("hevc_mediacodec (${getString(R.string.text_missing)})")
            
            result.joinToString("\n")
        } catch (e: Exception) {
            "Error checking FFmpeg: ${e.message}"
        }
    }

    private fun getProfileName(mime: String, profile: Int): String {
        return when {
            mime.equals("video/avc", true) -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "Extended"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High10"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "High422"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "High444"
                MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
                MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
                else -> "Unknown($profile)"
            }
            mime.equals("video/hevc", true) -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main10"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main10 HDR10"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main10 HDR10+"
                else -> "Unknown($profile)"
            }
            else -> "Unknown($profile)"
        }
    }

    private fun getLevelName(mime: String, level: Int): String {
        return when {
            mime.equals("video/avc", true) -> when (level) {
                MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> "1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel1b -> "1b"
                MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> "1.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> "1.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> "1.3"
                MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> "2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> "2.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> "2.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "3"
                MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> "3.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> "3.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "4"
                MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> "4.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> "4.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> "5"
                MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> "5.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> "5.2"
                else -> "Unknown($level)"
            }
            mime.equals("video/hevc", true) -> when (level) {
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1 -> "1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1 -> "1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2 -> "2 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2 -> "2 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21 -> "2.1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21 -> "2.1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3 -> "3 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3 -> "3 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31 -> "3.1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31 -> "3.1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4 -> "4 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4 -> "4 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41 -> "4.1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41 -> "4.1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5 -> "5 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5 -> "5 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51 -> "5.1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51 -> "5.1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52 -> "5.2 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52 -> "5.2 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6 -> "6 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6 -> "6 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61 -> "6.1 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61 -> "6.1 (High)"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62 -> "6.2 (Main)"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62 -> "6.2 (High)"
                else -> "Unknown($level)"
            }
            else -> "Unknown($level)"
        }
    }

    private fun getColorFormatName(format: Int): String {
        return when (format) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible -> "YUV420Flexible"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> "YUV420Planar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> "YUV420SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface -> "Surface"
            2130706688 -> "YUVP010" // COLOR_FormatYUVP010
            else -> "0x${Integer.toHexString(format)}"
        }
    }
}
