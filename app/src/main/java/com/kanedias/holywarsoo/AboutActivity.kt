package com.kanedias.holywarsoo

import android.os.Bundle
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import com.kanedias.holywarsoo.databinding.ActivityAboutBinding

/**
 * Activity for showing "About this app" info
 *
 * @author Kanedias
 *
 * Created on 2020-01-23
 */
class AboutActivity: ThemedActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.aboutToolbar.title = getString(R.string.app_name)
        binding.aboutToolbar.navigationIcon = DrawerArrowDrawable(this).apply { progress = 1.0f }
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
    }

}