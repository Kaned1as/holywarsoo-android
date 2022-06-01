package com.kanedias.holywarsoo

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.kanedias.holywarsoo.databinding.FragmentTopicListItemBinding
import com.kanedias.holywarsoo.dto.ForumTopicDesc
import com.kanedias.holywarsoo.misc.layoutVisibilityBool
import com.kanedias.holywarsoo.misc.resolveAttr
import com.kanedias.holywarsoo.misc.showFullscreenFragment


/**
 * View holder that shows forum topic
 *
 * @see TopicContentFragment
 *
 * @author Kanedias
 *
 * Created on 2019-12-22
 */
class TopicViewHolder(iv: View) : RecyclerView.ViewHolder(iv) {

    val binding = FragmentTopicListItemBinding.bind(iv)

    fun setup(topic: ForumTopicDesc) {
        binding.topicStickyMarker.layoutVisibilityBool = topic.sticky
        binding.topicClosedMarker.layoutVisibilityBool = topic.closed
        binding.topicName.text = topic.name

        if (topic.replyCount != null) {
            binding.topicRepliesLabel.visibility = View.VISIBLE
            binding.topicRepliesCount.visibility = View.VISIBLE
            binding.topicRepliesCount.text = topic.replyCount.toString()
        } else {
            binding.topicRepliesLabel.visibility = View.GONE
            binding.topicRepliesCount.visibility = View.GONE
        }

        if (topic.viewCount != null) {
            binding.topicViewsLabel.visibility = View.VISIBLE
            binding.topicViewCount.visibility = View.VISIBLE
            binding.topicViewCount.text = topic.viewCount.toString()
        } else {
            binding.topicViewsLabel.visibility = View.GONE
            binding.topicViewCount.visibility = View.GONE
        }

        if (topic.lastMessageDate.isNullOrEmpty()) {
            binding.topicLastMessageTopic.visibility = View.GONE
        } else {
            binding.topicLastMessageTopic.visibility = View.VISIBLE
            binding.topicLastMessageTopic.text = topic.lastMessageDate
        }

        if (topic.newMessageUrl != null) {
            val color = itemView.resolveAttr(R.attr.colorPrimary)
            binding.topicLastMessageTopic.setTextColor(color)
            TextViewCompat.setCompoundDrawableTintList(binding.topicLastMessageTopic, ColorStateList.valueOf(color))
        } else {
            val color = itemView.resolveAttr(R.attr.colorNonImportantText)
            binding.topicLastMessageTopic.setTextColor(color)
            TextViewCompat.setCompoundDrawableTintList(binding.topicLastMessageTopic, ColorStateList.valueOf(color))
        }

        itemView.setOnClickListener {
            val relevantUrl = topic.newMessageUrl ?: topic.url

            val fragment = TopicContentFragment().apply {
                arguments = Bundle().apply {
                    putString(TopicContentFragment.URL_ARG, relevantUrl)
                }
            }

            (itemView.context as AppCompatActivity).showFullscreenFragment(fragment)
        }

        binding.topicLastMessageTopic.setOnClickListener {
            val fragment = TopicContentFragment().apply {
                arguments = Bundle().apply {
                    putString(TopicContentFragment.URL_ARG, topic.lastMessageUrl)
                }
            }

            (itemView.context as AppCompatActivity).showFullscreenFragment(fragment)
        }
    }

}