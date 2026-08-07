package com.union_test.toutiao.mediation.kotlin

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.union_test.toutiao.R


class MediationKotlinSplashStartActivity : AppCompatActivity() {

    lateinit var mediaId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_splash_start)
        mediaId = resources.getString(R.string.splash_media_id)
        val tvMediationId = findViewById<TextView>(R.id.tv_media_id)
        tvMediationId.text = String.format(
            resources.getString(R.string.ad_mediation_id),
            mediaId
        )
        findViewById<Button>(R.id.bt_load_show).setOnClickListener {
            val intent = Intent(this, MediationKotlinSplashActivity::class.java)
            startActivity(intent)
        }
    }
}