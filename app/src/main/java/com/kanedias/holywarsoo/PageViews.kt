package com.kanedias.holywarsoo

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kanedias.holywarsoo.databinding.ViewPageNavigationBinding
import com.kanedias.holywarsoo.misc.visibilityBool
import com.kanedias.holywarsoo.model.PageableModel
import kotlinx.coroutines.delay

/**
 * Helper class to hold all paging-related functions in all paged views where possible.
 *
 * @author Kanedias
 *
 * Created on 2020-01-02
 */
class PageViews(parent: ContentFragment, model: PageableModel, iv: View) {

    init {
        val binding = ViewPageNavigationBinding.bind(iv)

        // remember to first set pageCount, then currentPage in parent fragment
        model.currentPage.observe(parent) { binding.pageNavigationCurrentPage.text = it.toString() }
        model.currentPage.observe(parent) { binding.pageNavigationToFirstPage.visibilityBool = it > 1 }
        model.currentPage.observe(parent) { binding.pageNavigationToPreviousPage.visibilityBool = it > 1 }
        model.currentPage.observe(parent) { binding.pageNavigationToNextPage.visibilityBool = it < model.pageCount.value!! }
        model.currentPage.observe(parent) { binding.pageNavigationToLastPage.visibilityBool = it < model.pageCount.value!! }

        binding.pageNavigationToFirstPage.setOnClickListener { model.currentPage.value = 1; parent.refreshContent() }
        binding.pageNavigationToPreviousPage.setOnClickListener { model.currentPage.value = model.currentPage.value!! - 1; parent.refreshContent() }
        binding.pageNavigationToNextPage.setOnClickListener { model.currentPage.value = model.currentPage.value!! + 1; parent.refreshContent() }
        binding.pageNavigationToLastPage.setOnClickListener { model.currentPage.value = model.pageCount.value!!; parent.refreshContent() }

        // jump to arbitrary page on click
        binding.pageNavigationCurrentPage.setOnClickListener {
            val jumpToPageView = parent.layoutInflater.inflate(R.layout.view_jump_to_page, null) as TextInputEditText
            jumpToPageView.hint = "1 .. ${model.pageCount.value}"
            jumpToPageView.setText(model.currentPage.value!!.toString())

            MaterialAlertDialogBuilder(parent.requireContext())
                .setTitle(R.string.jump_to_page)
                .setView(jumpToPageView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) {_, _ ->
                    val number = jumpToPageView.text.toString().toIntOrNull()
                    if (number != null && number > 0 && number <= model.pageCount.value!!) {
                        model.currentPage.value = number; parent.refreshContent()
                    }
                }
                .show()

            parent.lifecycleScope.launchWhenResumed {
                delay(100) // wait for dialog to be shown
                jumpToPageView.requestFocus()
                val imm = parent.requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(jumpToPageView, 0)
            }
        }
    }
}