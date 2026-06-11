package com.henrydavl.apilogkit.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.henrydavl.apilogkit.sample.databinding.ActivityDevOptionsBinding

/**
 * The host app's own Developer Options screen — deliberately built with XML
 * (ViewBinding), reached from the inspector menu via
 * [com.henrydavl.apilogkit.ApiLogKitConfig.developerOptions]. Proves the dev-options
 * hook does not require Compose.
 */
class DevOptionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDevOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }
    }
}
