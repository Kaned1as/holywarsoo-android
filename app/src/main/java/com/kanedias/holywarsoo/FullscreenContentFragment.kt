package com.kanedias.holywarsoo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewbinding.ViewBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kanedias.holywarsoo.misc.resolveAttr
import com.kanedias.holywarsoo.model.PageableModel
import com.r0adkll.slidr.Slidr
import com.r0adkll.slidr.model.SlidrConfig
import com.r0adkll.slidr.model.SlidrInterface
import com.r0adkll.slidr.model.SlidrPosition

/**
 * @author Kanedias
 *
 * Created on 2020-01-14
 */
abstract class FullscreenContentFragment: ContentFragment() {

    lateinit var mainArea: CoordinatorLayout
    lateinit var toolbar: Toolbar
    lateinit var viewRefresher: SwipeRefreshLayout
    lateinit var pageNavigation: ViewGroup
    lateinit var contentView: RecyclerView
    lateinit var actionButton: FloatingActionButton

    private lateinit var binding: ViewBinding
    private lateinit var pageControls: PageViews

    abstract fun bindLayout(inflater: LayoutInflater, container: ViewGroup?): ViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = bindLayout(inflater, container)

        mainArea = binding.root.findViewById(R.id.main_fragment_content_area)
        toolbar = binding.root.findViewById(R.id.content_toolbar)
        viewRefresher = binding.root.findViewById(R.id.content_scroll_area)
        pageNavigation = binding.root.findViewById(R.id.content_bottom_navigation)
        contentView = binding.root.findViewById(R.id.content_list)
        actionButton = binding.root.findViewById(R.id.content_reply_button)

        return binding.root
    }

    open fun setupUI(model: PageableModel) {
        viewRefresher.setColorSchemeColors(requireContext().resolveAttr(R.attr.colorSecondary))
        viewRefresher.setProgressBackgroundColorSchemeColor(requireContext().resolveAttr(R.attr.colorPrimary))
        viewRefresher.setOnRefreshListener { refreshContent() }
        pageControls = PageViews(this, model, pageNavigation)
    }

    override fun refreshViews() {
        // setup toolbar
        toolbar.navigationIcon = DrawerArrowDrawable(activity).apply { progress = 1.0f }
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    /**
     * Slide right to go back helper
     */
    private var slidrInterface: SlidrInterface? = null

    override fun onResume() {
        super.onResume()
        if (slidrInterface == null) {
            slidrInterface = Slidr.replace(mainArea, SlidrConfig.Builder().position(SlidrPosition.LEFT).build())
        }
    }
}