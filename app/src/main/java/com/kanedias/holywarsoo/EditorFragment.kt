package com.kanedias.holywarsoo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Helper fragment representing edit dialog that pops from the bottom of the screen.
 *
 * @see AddMessageFragment
 *
 * @author Kanedias
 *
 * Created on 2019-12-29
 */
open class EditorFragment: BottomSheetDialogFragment() {

    protected lateinit var editor: EditorViews

    /**
     * Called when activity called to select image/file to upload has finished executing
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        when (requestCode) {
            EditorViews.ACTIVITY_REQUEST_IMAGE_UPLOAD -> editor.requestImageUpload(data)
        }
    }

    override fun onStart() {
        super.onStart()

        view?.apply {
            val parent = parent as? View ?: return@apply
            val params = parent.layoutParams as? CoordinatorLayout.LayoutParams ?: return@apply
            val bottomSheetBehavior = params.behavior as? BottomSheetBehavior ?: return@apply
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}