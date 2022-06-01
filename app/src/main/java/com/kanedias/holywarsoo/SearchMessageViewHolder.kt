package com.kanedias.holywarsoo

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.iterator
import com.kanedias.holywarsoo.databinding.FragmentSearchMessageListItemBinding
import com.kanedias.holywarsoo.databinding.FragmentTopicMessageListItemBinding
import com.kanedias.holywarsoo.databinding.ViewMessageContentBinding
import com.kanedias.holywarsoo.dto.ForumMessage
import com.kanedias.holywarsoo.dto.NavigationScope
import com.kanedias.holywarsoo.misc.resolveAttr

/**
 * View holder for search messages. Search messages, in addition to usual content,
 * also contain navigation links to various places they can be located in.
 *
 * @see SearchMessagesContentFragment
 *
 * @author Kanedias
 *
 * Created on 20-01-26
 */
class SearchMessageViewHolder(parent: SearchMessagesContentFragment, iv: View): MessageViewHolder(parent, iv) {

    private lateinit var messageNavlinkToForum: TextView
    private lateinit var messageNavlinkToTopic: TextView
    private lateinit var messageNavlinkToMessage: TextView

    override fun initBinding() {
        val holderBinding = FragmentSearchMessageListItemBinding.bind(itemView)
        contentBinding = ViewMessageContentBinding.bind(holderBinding.root)
        messageArea = holderBinding.messageArea
        messageNavlinkToForum = holderBinding.messageNavlinkToForum
        messageNavlinkToTopic = holderBinding.messageNavlinkToTopic
        messageNavlinkToMessage = holderBinding.messageNavlinkToMessage
    }

    override fun setup(message: ForumMessage) {
        // this forum message *must* have navigation links
        super.setup(message)

        val ctx = itemView.context

        val toForumSuffix = ctx.getString(R.string.navigate_to_forum)
        val toForumMessage = message.navigationLinks.getValue(NavigationScope.FORUM).first
        val toForumLink = message.navigationLinks.getValue(NavigationScope.FORUM).second
        messageNavlinkToForum.text = "$toForumSuffix $toForumMessage"
        messageNavlinkToForum.setOnClickListener { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(toForumLink))) }

        val toTopicSuffix = ctx.getString(R.string.navigate_to_topic)
        val toTopicMessage = message.navigationLinks.getValue(NavigationScope.TOPIC).first
        val toTopicLink = message.navigationLinks.getValue(NavigationScope.TOPIC).second
        messageNavlinkToTopic.text = "$toTopicSuffix $toTopicMessage"
        messageNavlinkToTopic.setOnClickListener { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(toTopicLink))) }

        val toMessageSuffix = ctx.getString(R.string.navigate_to_message)
        val toMessageMessage = message.navigationLinks.getValue(NavigationScope.MESSAGE).first
        val toMessageLink = message.navigationLinks.getValue(NavigationScope.MESSAGE).second
        messageNavlinkToMessage.text = "$toMessageSuffix $toMessageMessage"
        messageNavlinkToMessage.setOnClickListener { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(toMessageLink))) }

        contentBinding.messageOverflowMenu.setOnClickListener { configureContextMenu(it, message) }
    }

    private fun configureContextMenu(anchor: View, message: ForumMessage) {
        val pmenu = PopupMenu(anchor.context, anchor)
        pmenu.inflate(R.menu.message_menu)
        pmenu.menu.iterator().forEach { mi -> DrawableCompat.setTint(mi.icon, anchor.resolveAttr(R.attr.colorOnSecondary)) }

        // we have nowhere to reply here, we're in the search page
        pmenu.menu.findItem(R.id.menu_message_quote).setVisible(false)

        configureContextMenu(pmenu, anchor, message)

        val helper = MenuPopupHelper(anchor.context, pmenu.menu as MenuBuilder, anchor)
        helper.setForceShowIcon(true)
        helper.show()
    }
}