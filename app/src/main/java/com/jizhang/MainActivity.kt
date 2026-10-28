package com.jizhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jizhang.ui.LedgerApp

class MainActivity : ComponentActivity() {
    private val vm: LedgerViewModel by viewModels {
        viewModelFactory { initializer { LedgerViewModel((application as JizhangApp).container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status bar and gesture bar; screens add insets where content needs them.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LedgerApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // Catch up on "定时记账" rules each time the app comes back (also covers the first start).
        vm.runRecurring()
        // Back from 允许安装未知应用: carry on with the update. Then the daily check for a new release.
        vm.updater.onResume()
        vm.updater.autoCheckIfDue()
    }
}
