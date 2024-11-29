package com.hhvvg.ecm.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.hhvvg.ecm.R
import com.hhvvg.ecm.configuration.Configuration
import com.hhvvg.ecm.receiver.ServiceStateReceiver
import com.hhvvg.ecm.ui.view.ExtSwitchPreference
import com.hhvvg.ecm.ui.view.InputBottomSheetDialog
import com.hhvvg.ecm.util.getSystemExtClipboardService
import com.hhvvg.ecm.util.themeColor
import kotlinx.coroutines.launch

/**
 * @author hhvvg
 */
class MainFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    companion object {
        const val MAIN_EVENT_SOURCE = "MainFragmentEventSource"
    }
    private lateinit var enableSwitchPreference: ExtSwitchPreference
    private lateinit var autoClearSwitchPreference: ExtSwitchPreference
    private lateinit var timeoutClearSwitchPref: ExtSwitchPreference
    private lateinit var autoClearStrategyPreference: Preference
    private lateinit var readStrategyPreference: Preference
    private lateinit var writeStrategyPreference: Preference
    private lateinit var readSwitchPreference: Preference
    private lateinit var writeSwitchPreference: Preference
    private lateinit var syncPreference: Preference
    private lateinit var syncStrategyPreference: Preference

    private val extService by lazy {
        requireContext().getSystemExtClipboardService()
    }
    private val navController by lazy {
        findNavController()
    }
    private val stateReceiver = object : ServiceStateReceiver() {
        override fun onServiceStateChanged(enable: Boolean, source: String) {
            if (source != MAIN_EVENT_SOURCE) {
                enableSwitchPreference.isChecked = enable
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().invalidateOptionsMenu()
        //Disable scrollbar
        (view.findViewById(android.R.id.list) as ListView?)?.isVerticalScrollBarEnabled = false
        ServiceStateReceiver.registerStateChangedReceiver(requireContext(), stateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(stateReceiver)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_prefs, rootKey)
        setupEnablePref()
        setupReadStrategyPref()
//        setupWriteStrategyPref()
        setupAutoClearPref()
        setupAutoClearStrategyPref()
        setupTimeoutClearPref()
        setupSyncPref()
    }

    private fun setupSyncPref() {
        syncPreference = findPreference("sync_clipboard")!!
        syncPreference.dependency = "enable_management"
        syncPreference.apply {
            onPreferenceChangeListener = this@MainFragment
        }
        syncStrategyPreference = findPreference("sync_manage_strategy")!!
        syncStrategyPreference.setOnPreferenceClickListener {
            val action = MainFragmentDirections.actionMainFragmentToSyncStrategyFragment()
            navController.navigate(action)
            true
        }
    }

    private fun setupTimeoutClearPref() {
        timeoutClearSwitchPref = findPreference("auto_clear_timeout")!!
        val timeout = extService?.autoClearTimeout ?: -1
        timeoutClearSwitchPref.isChecked = timeout > 0
        timeoutClearSwitchPref.summary = getTimeoutSummary()
        timeoutClearSwitchPref.onPreferenceChangeListener = this
        timeoutClearSwitchPref.dependency = "enable_management"
    }

    private fun getTimeoutSummary(): CharSequence {
        val timeout = extService?.autoClearTimeout ?: -1L
        return if (timeout > 0) {
            getString(R.string.auto_clear_timeout_summary, "$timeout")
        } else {
            getString(R.string.auto_clear_timeout_summary_off)
        }
    }

    private fun showInputDialogForTimeout() {
        val dialog = InputBottomSheetDialog.Builder(requireContext())
            .setMaxLines(1)
            .setTitle(requireContext().getString(R.string.timeout_input_title))
            .setHint(requireContext().getString(R.string.timeout_input_hint))
            .setInputType(InputType.TYPE_CLASS_NUMBER)
            .build()
        lifecycleScope.launch {
            when(val result = dialog.showDialog()) {
                is InputBottomSheetDialog.ActionResult.CancelAction -> {
                    timeoutClearSwitchPref.onPreferenceChangeListener = null
                    timeoutClearSwitchPref.isChecked = false
                    timeoutClearSwitchPref.onPreferenceChangeListener = this@MainFragment
                }
                is InputBottomSheetDialog.ActionResult.ConfirmResult -> {
                    val timeout = result.result.toString().toLongOrNull()
                    if (timeout != null && timeout > 0) {
                        extService?.autoClearTimeout = timeout
                        timeoutClearSwitchPref.summary = getString(R.string.auto_clear_timeout_summary, "$timeout")
                    } else {
                        timeoutClearSwitchPref.onPreferenceChangeListener = null
                        timeoutClearSwitchPref.isChecked = false
                        timeoutClearSwitchPref.onPreferenceChangeListener = this@MainFragment
                    }
                }
            }
        }
    }

    private fun setupAutoClearStrategyPref() {
        autoClearStrategyPreference = findPreference("auto_clear_strategy_key")!!
        autoClearStrategyPreference.setOnPreferenceClickListener {
            val action = MainFragmentDirections.actionMainFragmentToAutoClearStrategyFragment()
            navController.navigate(action)
            true
        }
    }

    private fun setupAutoClearPref() {
        autoClearSwitchPreference = findPreference("auto_clear_key")!!
        autoClearSwitchPreference.isChecked = extService?.isAutoClearEnable ?: false
        autoClearSwitchPreference.apply {
            isChecked = extService?.isAutoClearEnable ?: false
            onPreferenceChangeListener = this@MainFragment
        }
        autoClearSwitchPreference.dependency = "enable_management"
    }

    private fun setupReadStrategyPref() {
        readSwitchPreference = findPreference("filter_app_read")!!
        readSwitchPreference.dependency = "enable_management"
        readSwitchPreference.apply {
            onPreferenceChangeListener = this@MainFragment
        }
        readStrategyPreference = findPreference("filter_app_read_strategy")!!
        readStrategyPreference.setOnPreferenceClickListener {
            val action = MainFragmentDirections.actionMainFragmentToReadStrategyFragment(Configuration.READ_MODE)
            navController.navigate(action)
            true
        }
    }

//    private fun setupWriteStrategyPref() {
//        writeSwitchPreference = findPreference("filter_app_write")!!
//        writeSwitchPreference.dependency = "enable_management"
//        writeStrategyPreference = findPreference("filter_app_write_strategy")!!
//        writeStrategyPreference.setOnPreferenceClickListener {
//            val action = MainFragmentDirections.actionMainFragmentToWriteStrategyFragment(Configuration.WRITE_MODE)
//            navController.navigate(action)
//            true
//        }
//    }

    private fun setupEnablePref() {
        enableSwitchPreference = findPreference("enable_management")!!
        enableSwitchPreference.apply {
            isEnabled = extService != null
            summary = getStatusString()
            isChecked = extService?.isEnable ?: false
            onPreferenceChangeListener = this@MainFragment
        }
    }

    private fun getStatusString(): SpannableString {
        val string =  if (extService == null) {
            requireContext().getString(R.string.unavailable)
        } else {
            requireContext().getString(R.string.available)
        }
        val statusString = requireContext().getString(R.string.current_service_status, string)
        val startSpan = statusString.length - string.length
        val endSpan = statusString.length
        val flag = Spanned.SPAN_INCLUSIVE_INCLUSIVE
        val sp = SpannableString(statusString)
        val colorSpan = ForegroundColorSpan(requireContext().themeColor(androidx.appcompat.R.attr.colorPrimary))
        val sizeSpan = RelativeSizeSpan(1.1F)
        sp.setSpan(colorSpan, startSpan, endSpan, flag)
        sp.setSpan(sizeSpan, startSpan, endSpan, flag)
        return sp
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://github.com/powerAn2020/ExtClipboardManager")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when(preference.key) {
            "auto_clear_timeout" -> {
                val isOn = newValue as Boolean
                if (isOn) {
                    showInputDialogForTimeout()
                } else {
                    extService?.autoClearTimeout = -1
                    timeoutClearSwitchPref.summary = getTimeoutSummary()
                }
                true
            }
            "auto_clear_key" -> {
                extService?.isAutoClearEnable = newValue as Boolean
                true
            }
            "enable_management" -> {
                extService?.isEnable = newValue as Boolean
                ServiceStateReceiver.sendStateChangedBroadcast(requireContext(), newValue, MAIN_EVENT_SOURCE)
                true
            }
            "filter_app_read" -> {
                extService?.isReadWhiteEnable = newValue as Boolean
                true
            }
            "sync_clipboard" -> {
                extService?.isSyncEnable = newValue as Boolean
                true
            }
            else -> {
                false
            }
        }
    }
}
