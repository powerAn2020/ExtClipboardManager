package com.hhvvg.ecm.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hhvvg.ecm.R
import com.hhvvg.ecm.databinding.FragmentClipboardTestBinding
import com.hhvvg.ecm.ui.adapter.ClipboardReadLogAdapter
import com.hhvvg.ecm.util.getSystemExtClipboardService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardTestFragment : Fragment() {
    private var binding: FragmentClipboardTestBinding? = null
    private val service by lazy { requireContext().getSystemExtClipboardService() }
    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false
    private val logAdapter = ClipboardReadLogAdapter()
    
    companion object {
        private const val TEST_TEXT = "ExtClipboardManager Test Content"
        private const val POLL_INTERVAL = 1000L // 1 second
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentClipboardTestBinding.inflate(layoutInflater, container, false)
        this.binding = binding
        
        setupRecyclerView()
        setupButtons()
        updateHookStatus()
        
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setupRecyclerView() {
        binding?.rvReadLog?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
            // Disable nested scrolling since we're inside a ScrollView with fixed height
            isNestedScrollingEnabled = false
        }
    }

    private fun setupButtons() {
        binding?.apply {
            btnWriteTest.setOnClickListener {
                writeTestText()
            }
            
            btnReadClipboard.setOnClickListener {
                readClipboard()
            }
            
            btnResetCounters.setOnClickListener {
                resetCounters()
            }
        }
    }

    private fun writeTestText() {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("test", TEST_TEXT)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(requireContext(), getString(R.string.test_write_success), Toast.LENGTH_SHORT).show()
    }

    private fun readClipboard() {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        val text = clipData?.getItemAt(0)?.text?.toString()
        
        if (text != null) {
            Toast.makeText(requireContext(), getString(R.string.test_read_result, text), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.test_read_empty), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetCounters() {
        service?.resetTestCounters()
        updateReadInfo()
        Toast.makeText(requireContext(), getString(R.string.test_reset_success), Toast.LENGTH_SHORT).show()
    }

    private fun updateHookStatus() {
        val isEnabled = service?.isEnable() ?: false
        binding?.hookStatusText?.apply {
            if (service != null) {
                text = if (isEnabled) {
                    getString(R.string.test_hook_active)
                } else {
                    getString(R.string.test_hook_inactive)
                }
                setTextColor(resources.getColor(
                    if (isEnabled) android.R.color.holo_green_dark else android.R.color.holo_orange_dark,
                    null
                ))
            } else {
                text = getString(R.string.test_hook_not_found)
                setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            }
        }
    }

    private fun startPolling() {
        if (!isPolling) {
            isPolling = true
            handler.post(pollRunnable)
        }
    }

    private fun stopPolling() {
        isPolling = false
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isPolling) {
                updateHookStatus()
                updateReadInfo()
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    private fun updateReadInfo() {
        binding?.apply {
            val totalCount = service?.totalReadCount ?: 0
            val lastPackage = service?.lastReadPackageName ?: ""
            val lastTime = service?.lastReadTimestamp ?: 0L
            
            totalReadCount.text = totalCount.toString()
            lastReadPackage.text = if (lastPackage.isNotEmpty()) lastPackage else getString(R.string.test_none)
            lastReadTime.text = if (lastTime > 0) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                sdf.format(Date(lastTime))
            } else {
                getString(R.string.test_none)
            }

            // Update log list
            val log = service?.readLog ?: emptyList()
            logAdapter.updateData(log)
            tvEmptyLog.visibility = if (log.isEmpty()) View.VISIBLE else View.GONE
            rvReadLog.visibility = if (log.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}
