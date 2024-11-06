package com.hhvvg.ecm.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hhvvg.ecm.configuration.Configuration
import com.hhvvg.ecm.ui.adapter.WorkModeListAdapter
import com.hhvvg.ecm.ui.base.BaseAppListFragment
import com.hhvvg.ecm.ui.data.AppItem
import com.hhvvg.ecm.util.getSystemExtClipboardService

class AccessStrategyFragment : BaseAppListFragment<WorkListItem>() {

    private val service by lazy {
        requireContext().getSystemExtClipboardService()
    }
    private lateinit var selectedStrategy:String
    private val listPackage: MutableSet<String> by lazy {

        Log.d("AccessStrategyFragment", "$selectedStrategy listPackage")

        val list = when(selectedStrategy) {
            Configuration.READ_MODE -> {
                service?.appReadWhitelist
            }
            Configuration.WRITE_MODE -> {
                service?.appWriteWhitelist
            }
            else -> mutableListOf()
        }?: mutableListOf()
        list.toMutableSet()
    }
    override fun onCreateAppListAdapter(items: MutableList<WorkListItem>): RecyclerView.Adapter<*> {
        return WorkModeListAdapter(items, listPackage)
    }
    override fun onCreateAppItem(appItem: AppItem): WorkListItem {
        return WorkListItem(appItem, listPackage.contains(appItem.packageName))
    }

    override fun onAppListSort(items: List<WorkListItem>): List<WorkListItem> {
        return items.sorted()
    }

    override fun onDestroy() {
        Log.d("AccessStrategyFragment", "onDestroy")

        // Save on exit
        when(selectedStrategy) {
            Configuration.READ_MODE -> {
                service?.appReadWhitelist = listPackage.toList()
            }
            Configuration.WRITE_MODE -> {
                service?.appWriteWhitelist = listPackage.toList()
            }
        }
        Log.d("AccessStrategyFragment", "$selectedStrategy onDestroy finish")
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        selectedStrategy= arguments?.getString("selectedStrategy").toString()
        super.onCreate(savedInstanceState)
        selectedStrategy= arguments?.getString("selectedStrategy").toString()

    }
}

