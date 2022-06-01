package com.kanedias.holywarsoo

import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.iterator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kanedias.holywarsoo.databinding.FragmentTopicMessageListItemBinding
import com.kanedias.holywarsoo.databinding.ViewMessageContentBinding
import com.kanedias.holywarsoo.dto.ForumMessage
import com.kanedias.holywarsoo.dto.ForumTopic
import com.kanedias.holywarsoo.markdown.handleMarkdown
import com.kanedias.holywarsoo.misc.*
import com.kanedias.holywarsoo.service.Network
import com.kanedias.holywarsoo.service.SpanCache
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * View holder that shows topic message
 *
 * @see TopicContentFragment
 *
 * @author Kanedias
 *
 * Created on 2019-12-22
 */
@Suppress("LeakingThis")
open class MessageViewHolder(private val parent: FullscreenContentFragment, iv: View) : RecyclerView.ViewHolder(iv) {

    lateinit var messageArea: MaterialCardView
    lateinit var contentBinding: ViewMessageContentBinding

    open fun initBinding() {
        val holderBinding = FragmentTopicMessageListItemBinding.bind(itemView)
        contentBinding = ViewMessageContentBinding.bind(holderBinding.root)
        messageArea = holderBinding.messageArea
    }

    /**
     * Message-specific setup
     */
    open fun setup(message: ForumMessage) {
        if (message.index == 1) {
            messageArea.cardElevation = dpToPixel(8f, messageArea.context)
        } else {
            messageArea.cardElevation = dpToPixel(2f, messageArea.context)
        }

        if (message.authorAvatarUrl != null) {
            contentBinding.messageAuthorAvatar.layoutVisibilityBool = true
            Glide.with(contentBinding.messageAuthorAvatar)
                .load(message.authorAvatarUrl)
                .apply(RequestOptions()
                    .centerInside()
                    .circleCrop())
                .into(contentBinding.messageAuthorAvatar)
        } else {
            contentBinding.messageAuthorAvatar.layoutVisibilityBool = false
        }

        contentBinding.messageAuthorName.text = message.author
        contentBinding.messageIndex.text = "#${message.index}"

        try {
            val creationDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(message.createdDate)!!
            contentBinding.messageDate.text = DateUtils.getRelativeTimeSpanString(creationDate.time)
            contentBinding.messageDate.setOnClickListener { it.showToast(message.createdDate) }
        } catch (ex: ParseException) {
            contentBinding.messageDate.text = message.createdDate
            contentBinding.messageDate.isClickable = false
        }

        contentBinding.messageBody.handleMarkdown(message.content)

        // make text selectable
        // XXX: this is MAGIC: see https://stackoverflow.com/a/56224791/1696844
        contentBinding.messageBody.setTextIsSelectable(false)
        contentBinding.messageBody.measure(-1, -1)
        contentBinding.messageBody.setTextIsSelectable(true)
    }

    /**
     * Message-and-thread-specific setup.
     */
    fun setup(message: ForumMessage, topic: ForumTopic) {
        this.setup(message)

        contentBinding.messageBody.customSelectionActionModeCallback = SelectionEnhancer(message, topic)

        contentBinding.messageOverflowMenu.setOnClickListener { configureContextMenu(it, message, topic) }
    }

    fun configureContextMenu(pmenu: PopupMenu, anchor: View, message: ForumMessage) {
        // share message permalink
        pmenu.menu.findItem(R.id.menu_message_share).setOnMenuItemClickListener {
            anchor.context.shareLink(message.link)
            true
        }

        // delete message
        val deleteMenuItem = pmenu.menu.findItem(R.id.menu_message_delete)
        if (message.isDeletable) {
            deleteMenuItem.setOnMenuItemClickListener {
                MaterialAlertDialogBuilder(anchor.context)
                    .setTitle(R.string.confirm_action)
                    .setMessage(R.string.delete_message_question)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ -> deleteMessage(message.id) }
                    .show()

                true
            }
        } else {
            deleteMenuItem.isVisible = false
        }

        // edit message
        val editMenuItem = pmenu.menu.findItem(R.id.menu_message_edit)
        if (message.isEditable) {
            editMenuItem.setOnMenuItemClickListener {
                val messageEdit = EditMessageFragment().apply {
                    arguments = Bundle().apply {
                        putInt(EditMessageFragment.EDIT_MESSAGE_ID_ARG, message.id)
                    }
                }

                val activity = itemView.context as AppCompatActivity
                messageEdit.show(activity.supportFragmentManager, "showing edit message fragment")

                true
            }
        } else {
            editMenuItem.isVisible = false
        }


        // report message to the administration
        pmenu.menu.findItem(R.id.menu_message_report).setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(anchor.context)
                .setTitle(R.string.report)
                .setView(R.layout.view_report_dialog)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) {dialog, _ ->
                    val waitDialog = MaterialAlertDialogBuilder(anchor.context)
                        .setTitle(R.string.please_wait)
                        .setMessage(R.string.loading)
                        .create()

                    val input = (dialog as AlertDialog).findViewById<EditText>(R.id.report_dialog_input)!!

                    parent.lifecycleScope.launch {
                        waitDialog.show()

                        Network.perform(
                            networkAction = { Network.postReport(message.id, input.text.toString()) },
                            uiAction = { Toast.makeText(anchor.context, R.string.reported, Toast.LENGTH_SHORT).show() }
                        )

                        waitDialog.dismiss()
                    }
                }
                .show()

            true
        }
    }

    private fun configureContextMenu(anchor: View, message: ForumMessage, topic: ForumTopic) {
        val pmenu = PopupMenu(anchor.context, anchor)
        pmenu.inflate(R.menu.message_menu)
        pmenu.menu.iterator().forEach { mi -> DrawableCompat.setTint(mi.icon, anchor.resolveAttr(R.attr.colorOnSecondary)) }

        this.configureContextMenu(pmenu, anchor, message)

        // insert full message quote
        pmenu.menu.findItem(R.id.menu_message_quote).setOnMenuItemClickListener {
            val waitDialog = MaterialAlertDialogBuilder(anchor.context)
                .setTitle(R.string.please_wait)
                .setMessage(R.string.loading)
                .create()

            parent.lifecycleScope.launch {
                waitDialog.show()

                Network.perform(
                    networkAction = { Network.loadQuote(topic.id, message.id) },
                    uiAction = { quote -> openQuotedReply(topic, mapOf(AddMessageFragment.FULL_QUOTE_ARG to quote)) }
                )

                waitDialog.dismiss()
            }
            true
        }

        val helper = MenuPopupHelper(anchor.context, pmenu.menu as MenuBuilder, anchor)
        helper.setForceShowIcon(true)
        helper.show()
    }

    private fun deleteMessage(messageId: Int) {
        val waitDialog = MaterialAlertDialogBuilder(itemView.context)
            .setTitle(R.string.please_wait)
            .setMessage(R.string.loading)
            .create()

        parent.lifecycleScope.launch {
            waitDialog.show()

            Network.perform(
                networkAction = { Network.deleteMessage(messageId) },
                uiAction = { link ->
                    // delete this message from cache
                    SpanCache.removeMessageId(messageId)

                    // refresh parent fragment
                    parent.arguments?.putString(TopicContentFragment.URL_ARG, link.toString())
                    parent.refreshContent()
                }
            )

            waitDialog.dismiss()
        }
    }

    /**
     * open create new message fragment and insert quote
     */
    private fun openQuotedReply(topic: ForumTopic, params: Map<String, String>) {
        val messageAdd = AddMessageFragment().apply {
            arguments = Bundle().apply {
                putInt(AddMessageFragment.TOPIC_ID_ARG, topic.id)
                params.forEach(action = {entry ->  putString(entry.key, entry.value)})
            }
        }

        val activity = itemView.context as AppCompatActivity
        messageAdd.show(activity.supportFragmentManager, "showing add message fragment")
    }

    /**
     * Enhances selection of the text in the specified message.
     * Shows "Reply" button that opens [AddMessageFragment] with the selected text quoted.
     */
    inner class SelectionEnhancer(private val message: ForumMessage, private val topic: ForumTopic): ActionMode.Callback {

        private val textView = contentBinding.messageBody

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val text = textView.text.subSequence(textView.selectionStart, textView.selectionEnd)

            when(item.itemId) {
                R.id.menu_reply -> {
                    openQuotedReply(topic, mapOf(
                        AddMessageFragment.AUTHOR_ARG to message.author,
                        AddMessageFragment.MSGID_ARG to message.id.toString(),
                        AddMessageFragment.PARTIAL_QUOTE_ARG to text.toString()
                    ))

                    mode.finish()
                    return true
                }
                else -> return false
            }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // don't show "Reply" option if topic is closed or we are not logged in
            if (!topic.isWritable) {
                return true
            }

            // we can write comments here, show "Reply" option
            mode.menuInflater.inflate(R.menu.content_selection_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }
}