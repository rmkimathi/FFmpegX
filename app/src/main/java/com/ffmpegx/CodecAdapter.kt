package com.ffmpegx

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ffmpegx.databinding.ItemCodecInfoBinding
import com.ffmpegx.databinding.ViewCodecInfoRowBinding

class CodecAdapter(private val codecs: List<CodecDiagnosticInfo>) :
    RecyclerView.Adapter<CodecAdapter.CodecViewHolder>() {

    class CodecViewHolder(val binding: ItemCodecInfoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodecViewHolder {
        val binding = ItemCodecInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CodecViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CodecViewHolder, position: Int) {
        val info = codecs[position]
        val context = holder.itemView.context
        with(holder.binding) {
            tvCodecName.text = info.name
            tvHardwareTag.visibility = if (info.isHardware) View.VISIBLE else View.GONE
            tvCodecMime.text = "MIME: ${info.mimeType}"
            tvVendorSoftware.text = "Vendor: ${if (info.isVendor) context.getString(R.string.text_yes) else context.getString(R.string.text_no)} | Software Only: ${if (info.isSoftwareOnly) context.getString(R.string.text_yes) else context.getString(R.string.text_no)}"

            setupRow(rowProfiles.root, context.getString(R.string.label_profiles), info.profiles.joinToString(", "))
            setupRow(rowLevels.root, context.getString(R.string.label_levels), info.levels.joinToString(", "))
            setupRow(rowMaxRes.root, context.getString(R.string.label_max_res), info.maxResolution)
            setupRow(rowMaxFps.root, context.getString(R.string.label_max_fps), info.maxFps)
            setupRow(rowBitrate.root, context.getString(R.string.label_bitrate), info.bitrateRange)
            setupRow(rowBitrateModes.root, context.getString(R.string.label_bitrate_modes), info.bitrateModes.joinToString(", "))
            setupRow(row10Bit.root, context.getString(R.string.label_10bit), if (info.is10BitSupported) context.getString(R.string.text_yes) else context.getString(R.string.text_no))
            setupRow(rowHdr.root, context.getString(R.string.label_hdr10_hdr10plus), "${if (info.isHDR10Supported) context.getString(R.string.text_yes) else context.getString(R.string.text_no)} / ${if (info.isHDR10PlusSupported) context.getString(R.string.text_yes) else context.getString(R.string.text_no)}")
            setupRow(rowColorFormats.root, context.getString(R.string.label_color_formats), info.colorFormats.joinToString(", "))
        }
    }

    private fun setupRow(view: View, label: String, value: String) {
        val rowBinding = ViewCodecInfoRowBinding.bind(view)
        rowBinding.tvLabel.text = "$label: "
        rowBinding.tvValue.text = value
    }

    override fun getItemCount(): Int = codecs.size
}
