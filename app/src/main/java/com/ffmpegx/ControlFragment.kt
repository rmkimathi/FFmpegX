package com.ffmpegx

import android.R
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ffmpegx.databinding.FragmentControlBinding
import kotlinx.coroutines.launch

class ControlFragment : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val vBinding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val input1Launcher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setInput1(it) }
    }

    private val input2Launcher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setInput2(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            viewModel.exportTemplates(it)
            Toast.makeText(requireContext(), "Templates Exported", Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.importTemplates(it)
            Toast.makeText(requireContext(), "Templates Imported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlBinding.inflate(inflater, container, false)
        return vBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        vBinding.btnSelectInput1.setOnClickListener { input1Launcher.launch("*/*") }
        vBinding.btnSelectInput2.setOnClickListener { input2Launcher.launch("*/*") }

        vBinding.btnMediaCodecDiagnostics.setOnClickListener {
            val intent = Intent(requireContext(), CodecDiagnosticActivity::class.java)
            startActivity(intent)
        }

        vBinding.spinnerTemplates.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateCurrentTemplate(position)
        }

        vBinding.btnSaveTemplate.setOnClickListener {
            viewModel.saveCurrentTemplate()
            Toast.makeText(requireContext(), "Template Saved", Toast.LENGTH_SHORT).show()
        }

        vBinding.btnDeleteTemplate.setOnClickListener {
            viewModel.deleteSelectedTemplate()
        }

        vBinding.btnExportTemplates.setOnClickListener {
            exportLauncher.launch("ffmpeg_templates.txt")
        }

        vBinding.btnImportTemplates.setOnClickListener {
            importLauncher.launch("text/plain")
        }

        vBinding.etTemplateName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.editTemplateName(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vBinding.etTemplatePattern.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.editTemplatePattern(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vBinding.etReviewCommand.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setReviewCommand(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vBinding.btnReview.setOnClickListener { viewModel.generateReviewCommand() }
        vBinding.btnClear.setOnClickListener { viewModel.clearCache() }
        vBinding.btnRun.setOnClickListener { viewModel.runCommand() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    vBinding.tvInput1.text = state.input1.takeLast(40).ifEmpty { "None" }
                    vBinding.tvInput2.text = state.input2.takeLast(40).ifEmpty { "None" }
                    vBinding.tvBinaryVersion.text = state.binaryVersion

                    val templateNames = state.templates.map { it.name }
                    val adapter = ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, templateNames)
                    vBinding.spinnerTemplates.setAdapter(adapter)

                    if (vBinding.etTemplateName.text.toString() != state.currentTemplateName) {
                        vBinding.etTemplateName.setText(state.currentTemplateName)
                    }
                    if (vBinding.etTemplatePattern.text.toString() != state.currentTemplatePattern) {
                        vBinding.etTemplatePattern.setText(state.currentTemplatePattern)
                    }
                    if (vBinding.etReviewCommand.text.toString() != state.reviewCommand) {
                        vBinding.etReviewCommand.setText(state.reviewCommand)
                    }

                    vBinding.btnRun.isEnabled = !state.isExecuting
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
