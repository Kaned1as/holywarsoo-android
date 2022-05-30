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
import com.kanedias.holywarsoo.dto.ForumMessage
import com.kanedias.holywarsoo.dto.SearchResults
import com.kanedias.holywarsoo.model.SearchMessagesContentsModel
import com.kanedias.holywarsoo.service.Network

/**
 * Fragment representing message search content.
 * Shows a list of message this search results contain.
 * Can be navigated with paging controls.
 *
 * @author Kanedias
 *
 * Created on 2019-12-19
 */
class SearchMessagesContentFragment: FullscreenContentFragment() {

    companion object {
        const val URL_ARG = "URL_ARG"
        const val KEYWORD_ARG = "KEYWORD_ARG"
    }

    lateinit var contents: SearchMessagesContentsModel

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_contents, parent, false)
        ButterKnife.bind(this, view)

        contents = ViewModelProvider(this).get(SearchMessagesContentsModel::class.java)
        contents.search.observe(viewLifecycleOwner) { contentView.adapter = SearchPageContentsAdapter(it) }
        contents.search.observe(viewLifecycleOwner) { refreshViews() }

        setupUI(contents)
        refreshContent()

        return view
    }

    override fun refreshContent() {
        Log.d("SearchFrag", "Refreshing content, search ${contents.search.value?.name}, page ${contents.currentPage.value}")

        lifecycleScope.launchWhenResumed {
            viewRefresher.isRefreshing = true

            val url = requireArguments().getString(URL_ARG, null)
            val keyword = requireArguments().getString(KEYWORD_ARG, null)

            Network.perform(
                networkAction = { Network.loadSearchMessagesResults(url, keyword, page = contents.currentPage.value!!) },
                uiAction = { loaded ->
                    contents.search.value = loaded
                    contents.pageCount.value = loaded.pageCount
                    contents.currentPage.value = loaded.currentPage
                }
            )

            viewRefresher.isRefreshing = false
        }
    }

    override fun refreshViews() {
        super.refreshViews()

        val searchResults = contents.search.value ?: return

        toolbar.apply {
            title = searchResults.name
            subtitle = "${getString(R.string.page)} ${searchResults.currentPage}"
        }

        when (searchResults.pageCount) {
            1 -> pageNavigation.visibility = View.GONE
            else -> pageNavigation.visibility = View.VISIBLE
        }
    }

    inner class SearchPageContentsAdapter(search: SearchResults<ForumMessage>) : RecyclerView.Adapter<SearchMessageViewHolder>() {

        val messages = search.results

        override fun getItemCount() = messages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchMessageViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.fragment_search_message_list_item, parent, false)
            return SearchMessageViewHolder(this@SearchMessagesContentFragment, view)
        }

        override fun onBindViewHolder(holder: SearchMessageViewHolder, position: Int) {
            val message = messages[position]
            holder.setup(message)
        }

    }
}