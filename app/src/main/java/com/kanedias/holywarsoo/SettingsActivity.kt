package com.kanedias.holywarsoo

import android.os.Bundle
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import com.kanedias.holywarsoo.databinding.ActivityPreferencesBinding

/**
 * Activity for holding and showing preference fragments
 *
 * @author Kanedias
 *
 * Created on 2018-04-26
 */
class SettingsActivity: ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.prefToolbar.title = getString(R.string.menu_settings)
        binding.prefToolbar.navigationIcon = DrawerArrowDrawable(this).apply { progress = 1.0f }
        binding.prefToolbar.setNavigationOnClickListener { finish() }
    }

}