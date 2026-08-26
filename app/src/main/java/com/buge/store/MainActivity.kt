package com.buge.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.buge.store.ui.BugeStoreApp
import com.buge.store.ui.StoreViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: StoreViewModel by viewModels {
        val container = (application as BugeStoreApplication).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StoreViewModel(container.repository, container.preferences, container.platform) as T
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInstallPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BugeStoreApplication).container
        val platform = container.platform

        lifecycleScope.launch {
            applyAppLocale(container.preferences.preferences.first().selectedLanguage)
        }

        setContent {
            BugeStoreApp(
                viewModel = viewModel,
                onApplyLanguage = ::applyAppLocale,
                onOpenUnknownSources = platform::openUnknownSourcesSettings,
                onOpenAppDetails = { packageName -> openAppDetails(packageName) },
                onOpenCategory = { category -> openCategory(category) },
            )
        }
    }

    private fun applyAppLocale(tag: String) {
        val normalizedTag = when (tag) {
            "zh" -> "zh-CN"
            "pt-rBR" -> "pt-BR"
            "zh-rTW" -> "zh-TW"
            else -> tag
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalizedTag))
    }

    private fun openAppDetails(packageName: String) {
        startActivity(Intent(this, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, packageName))
    }

    private fun openCategory(category: String) {
        startActivity(Intent(this, CategoryAppsActivity::class.java).putExtra(CategoryAppsActivity.EXTRA_CATEGORY_NAME, category))
    }
}
