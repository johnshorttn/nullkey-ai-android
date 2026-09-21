package com.nullverse.nullkeyai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nullverse.nullkeyai.BuildConfig
import com.nullverse.nullkeyai.R

class AboutSupportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_support)
        SystemBarInsets.applyToActivity(this)

        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        bind(R.id.btn_privacy, PRIVACY_URL)
        bind(R.id.btn_support, SUPPORT_URL)
        bind(R.id.btn_beta, BETA_URL)
        bind(R.id.btn_bug, BUG_URL)
        bind(R.id.btn_feature, FEATURE_URL)
        bind(R.id.btn_security, SECURITY_URL)
        bind(R.id.btn_sponsor, SPONSOR_URL)
    }

    private fun bind(id: Int, url: String) {
        findViewById<Button>(id).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    companion object {
        const val PRIVACY_URL = "https://johnshorttn.github.io/nullkey-ai-android/privacy.html"
        const val SUPPORT_URL = "https://johnshorttn.github.io/nullkey-ai-android/support.html"
        const val BETA_URL = "https://johnshorttn.github.io/nullkey-ai-android/beta.html"
        const val BUG_URL = "https://github.com/johnshorttn/nullkey-ai-android/issues/new?template=bug_report.yml"
        const val FEATURE_URL = "https://github.com/johnshorttn/nullkey-ai-android/issues/new?template=feature_request.yml"
        const val SECURITY_URL = "https://github.com/johnshorttn/nullkey-ai-android/security/advisories/new"
        const val SPONSOR_URL = "https://github.com/sponsors/johnshorttn"
    }
}
