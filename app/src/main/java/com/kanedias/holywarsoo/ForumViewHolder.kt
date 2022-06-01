package com.kanedias.holywarsoo

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.kanedias.holywarsoo.databinding.FragmentForumListItemBinding
import com.kanedias.holywarsoo.dto.ForumDesc
import com.kanedias.holywarsoo.misc.showFullscreenFragment
import com.kanedias.holywarsoo.misc.visibilityBool

/**
 * View holder representing forum
 *
 * @see MainPageContentFragment
 * @see ForumContentFragment
 *
 * @author Kanedias
 *
 * Created on 2019-12-23
 */
class ForumViewHolder(iv: View) : RecyclerView.ViewHolder(iv) {

    val binding = FragmentForumListItemBinding.bind(iv)

    fun setup(forumDesc: ForumDesc) {
        binding.forumName.text = forumDesc.name
        binding.forumSubtext.text = forumDesc.subtext
        binding.listItemSeparatorText.text = forumDesc.category
        binding.forumLastMessageDate.text = forumDesc.lastMessageDate
        binding.forumLastMessageTopic.text = forumDesc.lastMessageName

        binding.forumListItem.setOnClickListener {
            val fragment = ForumContentFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ForumContentFragment.URL_ARG, forumDesc.link)
                }
            }

            (itemView.context as AppCompatActivity).showFullscreenFragment(fragment)
        }

        if (!forumDesc.lastMessageDate.isNullOrEmpty()) {
            binding.forumLastMessageTopic.visibilityBool = true
            binding.forumLastMessageTopic.setOnClickListener {
                val fragment = TopicContentFragment().apply {
                    arguments = Bundle().apply {
                        putString(TopicContentFragment.URL_ARG, forumDesc.lastMessageLink)
                    }
                }

                (itemView.context as AppCompatActivity).showFullscreenFragment(fragment)
            }
        } else {
            binding.forumLastMessageTopic.visibilityBool = false
        }
    }

}