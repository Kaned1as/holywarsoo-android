package com.kanedias.holywarsoo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kanedias.holywarsoo.databinding.FragmentMainContentsBinding
import com.kanedias.holywarsoo.dto.ForumDesc
import com.kanedias.holywarsoo.misc.resolveAttr
import com.kanedias.holywarsoo.model.MainPageModel
import com.kanedias.holywarsoo.service.Network

/**
 * Fragment showing main page, i.e. list of forums with optional categories.
 *
 * @author Kanedias
 *
 * Created on 2019-12-19
 */
class MainPageContentFragment: ContentFragment() {

    private lateinit var binding: FragmentMainContentsBinding
    private lateinit var contents: MainPageModel

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        binding = FragmentMainContentsBinding.inflate(inflater, parent, false)

        binding.mainForumList.layoutManager = LinearLayoutManager(context)

        binding.mainForumListScrollArea.setOnRefreshListener { refreshContent() }
        binding.mainForumListScrollArea.setColorSchemeColors(requireContext().resolveAttr(R.attr.colorSecondary))
        binding.mainForumListScrollArea.setProgressBackgroundColorSchemeColor(requireContext().resolveAttr(R.attr.colorPrimary))

        contents = ViewModelProvider(requireActivity()).get(MainPageModel::class.java)
        contents.forums.observe(viewLifecycleOwner) { binding.mainForumList.adapter = ForumListAdapter(it) }
        contents.forums.observe(viewLifecycleOwner) { refreshViews() }

        refreshContent()

        return binding.root
    }

    override fun refreshViews() {
        val activity = activity as? MainActivity ?: return

        activity.binding.mainToolbar.apply {
            title = getString(R.string.app_name)
            subtitle = ""
        }
    }

    override fun refreshContent() {
        lifecycleScope.launchWhenResumed {
            binding.mainForumListScrollArea.isRefreshing = true

            Network.perform(
                networkAction = { Network.loadForumList() },
                uiAction = { loaded -> contents.forums.value = loaded }
            )

            binding.mainForumListScrollArea.isRefreshing = false
        }
    }

    /**
     * Adapter for presenting forum descriptions and links in a material card view.
     * Main page only contains top-level forums, which always are part of some category.
     */
    class ForumListAdapter(private val forumDescList: List<ForumDesc>) : RecyclerView.Adapter<ForumViewHolder>() {

        override fun getItemCount() = forumDescList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForumViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val holder = inflater.inflate(R.layout.fragment_forum_list_item, parent, false)
            return ForumViewHolder(holder)
        }

        override fun onBindViewHolder(holder: ForumViewHolder, position: Int) {
            val forum = forumDescList[position]
            holder.setup(forum)

            // show category if it's changed
            val categoryChanged = position > 0 && forum.category != forumDescList[position - 1].category
            if (forum.category != null && (position == 0 || categoryChanged)) {
                holder.binding.forumListItemSeparator.visibility = View.VISIBLE
            } else {
                holder.binding.forumListItemSeparator.visibility = View.GONE
            }
        }

    }
}