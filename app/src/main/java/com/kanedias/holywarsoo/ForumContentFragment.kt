package com.kanedias.holywarsoo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import butterknife.ButterKnife
import com.kanedias.holywarsoo.dto.Forum
import com.kanedias.holywarsoo.model.ForumContentsModel
import com.kanedias.holywarsoo.service.Network

/**
 * Fragment representing forum content.
 * Shows a list of topics/subforums this forum contains.
 * Can be navigated with paging controls.
 *
 * @author Kanedias
 *
 * Created on 2019-12-29
 */
class ForumContentFragment: FullscreenContentFragment() {

    companion object {
        const val URL_ARG = "URL_ARG"
    }

    lateinit var contents: ForumContentsModel

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_contents, parent, false)
        ButterKnife.bind(this, view)

        contents = ViewModelProvider(this).get(ForumContentsModel::class.java)
        contents.forum.observe(viewLifecycleOwner) { contentView.adapter = ForumContentsAdapter(it) }
        contents.forum.observe(viewLifecycleOwner) { refreshViews() }
        contents.refreshed.value = false

        setupUI(contents)
        refreshContent()

        return view
    }

    override fun refreshViews() {
        super.refreshViews()

        val forum = contents.forum.value ?: return

        toolbar.apply {
            title = forum.name
            subtitle = "${getString(R.string.page)} ${forum.currentPage}"
        }

        if (forum.isWritable) {
            actionButton.show()
            actionButton.setOnClickListener {
                val frag = AddTopicFragment().apply {
                    arguments = Bundle().apply { putSerializable(AddTopicFragment.FORUM_ID_ARG, forum.id) }
                }
                frag.show(parentFragmentManager, "topic create fragment")
            }
        } else {
            actionButton.visibility = View.GONE
        }

        when (forum.pageCount) {
            1 -> pageNavigation.visibility = View.GONE
            else -> pageNavigation.visibility = View.VISIBLE
        }
    }

    override fun refreshContent() {
        Log.d("ForumFrag", "Refreshing content, forum ${contents.forum.value?.name}, page ${contents.currentPage.value}")

        lifecycleScope.launchWhenResumed {
            viewRefresher.isRefreshing = true

            val forumUrl =  contents.forum.value?.link
            val customUrl = when(contents.refreshed.value) {
                // only load custom url once
                false -> requireArguments().getString(URL_ARG, null)
                else -> null
            }

            Network.perform(
                networkAction = { Network.loadForumContents(forumUrl, customUrl, page = contents.currentPage.value!!) },
                uiAction = { loaded ->
                    contents.forum.value = loaded
                    contents.pageCount.value = loaded.pageCount
                    contents.currentPage.value = loaded.currentPage
                    contents.refreshed.value = true
                }
            )

            viewRefresher.isRefreshing = false
        }
    }

    /**
     * Adapter for presenting forum and topic descriptions and links in a material card view.
     * Forum pages can contain both subforums and topics, so the adapter has to distinguish between them.
     *
     * Subforums also never have category assigned.
     */
    class ForumContentsAdapter(forum: Forum) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val ITEM_SUBFORUM = 0
            const val ITEM_REGULAR = 1
            const val ITEM_UNKNOWN = -1
        }

        private val subforums = forum.subforums
        private val topics = forum.topics


        override fun getItemCount() = topics.size + subforums.size

        override fun getItemViewType(position: Int): Int {
            val subForumCnt = subforums.size
            val topicCnt = topics.size

            return when {
                position < subForumCnt -> ITEM_SUBFORUM
                position < subForumCnt + topicCnt -> ITEM_REGULAR
                else -> ITEM_UNKNOWN
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                ITEM_SUBFORUM -> {
                    val view = inflater.inflate(R.layout.fragment_forum_list_item, parent, false)
                    return ForumViewHolder(view)
                }
                else -> {
                    val view = inflater.inflate(R.layout.fragment_topic_list_item, parent, false)
                    TopicViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (getItemViewType(position)) {
                ITEM_SUBFORUM -> {
                    val forum = subforums[position]
                    (holder as ForumViewHolder).apply {
                        setup(forum)

                        // show category if it's changed
                        val categoryChanged = position > 0 && forum.category != subforums[position - 1].category
                        if (forum.category != null && (position == 0 || categoryChanged)) {
                            holder.forumCategoryArea.visibility = View.VISIBLE
                        } else {
                            holder.forumCategoryArea.visibility = View.GONE
                        }
                    }
                }
                else -> {
                    val topic = topics[position - subforums.size]
                    (holder as TopicViewHolder).setup(topic)
                }
            }
        }

    }
}