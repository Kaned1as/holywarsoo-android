package com.kanedias.holywarsoo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kanedias.holywarsoo.database.entities.OfflineDraft
import com.kanedias.holywarsoo.databinding.FragmentAddMessageBinding
import com.kanedias.holywarsoo.service.Database
import com.kanedias.holywarsoo.service.Network
import kotlinx.coroutines.launch
import java.util.*

/**
 * Fragment responsible for adding a new message to the specified topic.
 * Topic id **must** be sent as an argument.
 *
 * @author Kanedias
 *
 * Created on 2019-12-29
 */
class AddMessageFragment: EditorFragment() {

    companion object {
        const val DB_CONTEXT_PREFIX = "newmessage"

        /**
         * Required, the topic to which reply should be posted
         */
        const val TOPIC_ID_ARG = "TOPIC_ID_ARG"

        /**
         * Used when quoting whole text, already contains
         * author and message id references
         */
        const val FULL_QUOTE_ARG = "QUOTE_ARG"

        /**
         * Used when quoting part of the text, author
         * and message id should be set manually
         */
        const val PARTIAL_QUOTE_ARG = "PARTIAL_QUOTE_ARG"
        const val AUTHOR_ARG = "AUTHOR_ARG"
        const val MSGID_ARG = "MSGID_ARG"
    }

    private lateinit var binding: FragmentAddMessageBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAddMessageBinding.inflate(inflater, container, false)
        binding.messageCancel.setOnClickListener { dialog?.cancel() }
        binding.messageSubmit.setOnClickListener { submit() }

        editor = EditorViews(this, binding)
        handleDraft()
        handleQuote()
        handleMisc()

        return binding.root
    }

    private fun handleDraft() {
        val topicId = requireArguments().getInt(TOPIC_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${topicId}"

        // if draft exists with this key, fill content with it
        Database.draftDao().getByKey(contextKey)?.let {
            binding.sourceText.setText(it.content)
            binding.sourceText.setSelection(binding.sourceText.length())
        }

        // delay saving text a bit so database won't be spammed with it
        binding.sourceText.addTextChangedListener { text ->
            val action = {
                val draft = OfflineDraft(createdAt = Date(), ctxKey = contextKey, content = text.toString())
                Database.draftDao().insertDraft(draft)
            }
            binding.sourceText.removeCallbacks(action)
            binding.sourceText.postDelayed(action, 1500)
        }
    }

    private fun handleQuote() {
        val quoteText = requireArguments().getString(FULL_QUOTE_ARG)
        if (quoteText.isNullOrEmpty()) {
            return
        }

        appendQuoted(quoteText + "\n\n")
    }

    /**
     * Handles miscellaneous conditions, such as:
     * * This fragment was shown due to click on author's nickname
     * * This fragment was shown due to quoting
     *
     */
    private fun handleMisc() {
        // handle click on reply in text selection menu
        val authorName = requireArguments().getString(AUTHOR_ARG)
        val quotedText = requireArguments().getString(PARTIAL_QUOTE_ARG)
        val quotedId = requireArguments().getString(MSGID_ARG)

        if (authorName.isNullOrEmpty() || quotedText.isNullOrEmpty() || quotedId.isNullOrEmpty()) {
            // not provided, add nothing
            return
        }

        val partialQuoteText = "[quote=\"$authorName\", post=${quotedId}]\n" +
                "$quotedText\n" +
                "[/quote]\n\n"

        appendQuoted(partialQuoteText)
    }

    private fun appendQuoted(quote: String) {
        var quoteText = quote

        if (!binding.sourceText.text.isNullOrEmpty()) {
            quoteText = "${binding.sourceText.text}\n" + quoteText
        }

        binding.sourceText.setText(quoteText)
        binding.sourceText.setSelection(quoteText.length)
    }

    fun submit() {
        val topicId = requireArguments().getInt(TOPIC_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${topicId}"

        val waitDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.please_wait)
            .setMessage(R.string.submitting)
            .create()

        val frgPredicate = { it: Fragment -> it is ContentFragment }
        val curFrg = parentFragmentManager.fragments.reversed().find(frgPredicate) as TopicContentFragment?

        lifecycleScope.launch {
            waitDialog.show()

            Network.perform(
                networkAction = { Network.postMessage(topicId, binding.sourceText.text.toString()) },
                uiAction = { link ->
                    // delete draft of this message, prevent reinsertion
                    // should be race-free since it's in the same thread as this one (Main UI thread)
                    binding.sourceText.handler?.removeCallbacksAndMessages(contextKey)
                    Database.draftDao().deleteByKey(contextKey)

                    // refresh parent fragment
                    curFrg?.apply {
                        // make it scroll to newly added message
                        requireArguments().putString(TopicContentFragment.URL_ARG, link.toString())
                        contents.refreshed.value = false

                        refreshContent()
                    }

                    dismiss()
                })

            waitDialog.dismiss()
        }
    }
}