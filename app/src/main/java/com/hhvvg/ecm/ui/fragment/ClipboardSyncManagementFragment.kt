package com.hhvvg.ecm.ui.fragment

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.hhvvg.ecm.R
import com.hhvvg.ecm.receiver.ServiceStateReceiver
import com.hhvvg.ecm.ui.fragment.MainFragment.Companion.MAIN_EVENT_SOURCE
import com.hhvvg.ecm.ui.view.InputBottomSheetDialog
import com.hhvvg.ecm.util.getSystemExtClipboardService
import kotlinx.coroutines.launch

class ClipboardSyncManagementFragment : PreferenceFragmentCompat(),Preference.OnPreferenceChangeListener {
    private val enablePullOnlyPref by lazy {
        findPreference<Preference>("sync_enable_pull_only")!!
    }
    private val encryptionPref by lazy {
        findPreference<Preference>("sync_enable_message_encryption")!!
    }
    private val serverPref by lazy {
        findPreference<Preference>("sync_ws_server")!!
    }
    private val tokenPref by lazy {
        findPreference<Preference>("sync_auth_token")!!
    }
    private val keyPref by lazy {
        findPreference<Preference>("sync_encryption_key")!!
    }
    private val ivPref by lazy {
        findPreference<Preference>("sync_encryption_iv")!!
    }

    private val service by lazy {
        requireContext().getSystemExtClipboardService()
    }
    private val navController by lazy {
        findNavController()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.clipboard_sync_strategy_pref, rootKey)
        if (service == null) {
            findPreference<PreferenceScreen>("root_key")?.isEnabled = false
            return
        }
        setupServerPref()
    }

    private fun setupServerPref() {
        if (service?.syncWsServer?.isNotBlank()==true){
            serverPref.summary = "${service?.syncWsServer?.trim()}"
        }
        serverPref.setOnPreferenceClickListener {
            val dialog = InputBottomSheetDialog.Builder(requireContext())
                .setMaxLines(1)
                .setTitle(getString(R.string.sync_ws_server_title))
                .setInputType(InputType.TYPE_CLASS_TEXT)
                .setHint(getString(R.string.sync_ws_server_summary))
                .setText(service?.syncWsServer?.trim().toString())
                .build()
            lifecycleScope.launch {
                when(val result = dialog.showDialog()) {
                    is InputBottomSheetDialog.ActionResult.ConfirmResult -> {
                        val count = result.result.toString()
                        if (count != null) {
                            service?.syncWsServer = count
                            if (service?.syncWsServer?.isNotBlank()==true){
                                serverPref.summary = "${service?.syncWsServer?.trim()}"
                            }
                        }
                    }
                    else -> {
                        // Do nothing
                    }
                }
            }
            true
        }
    }
    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when(preference.key) {
            "sync_enable_pull_only" -> {
                service?.isSyncPullOnlyEnable= newValue as Boolean
                true
            }
            "sync_enable_message_encryption" -> {
                service?.isSyncEncryptionEnable = newValue as Boolean
                true
            }
            "sync_ws_server" -> {
                service?.syncWsServer=newValue as String
//                ServiceStateReceiver.sendStateChangedBroadcast(requireContext(), newValue, MAIN_EVENT_SOURCE)
                true
            }
            "sync_auth_token" -> {
                service?.syncAuthToken=newValue as String
                true
            }
            "sync_encryption_key" -> {
                service?.syncEncryptionKey=newValue as String
                true
            }
            "sync_encryption_iv" -> {
                service?.syncEncryptionIV=newValue as String
                true
            }
            else -> {
                false
            }
        }
    }
}