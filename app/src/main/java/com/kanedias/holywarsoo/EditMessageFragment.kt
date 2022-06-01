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
import com.kanedias.holywarsoo.misc.layoutVisibilityBool
import com.kanedias.holywarsoo.service.Database
import com.kanedias.holywarsoo.service.Network
import com.kanedias.holywarsoo.service.SpanCache
import kotlinx.coroutines.launch
import java.util.*

/**
 * Fragment responsible for editing a message, be it topic starting message or ordinary one.
 *
 * @author Kanedias
 *
 * Created on 2020-03-11
 */
class EditMessageFragment: EditorFragment() {

    companion object {
        const val DB_CONTEXT_PREFIX = "editmessage"

        /**
         * The message id being edited
         */
        const val EDIT_MESSAGE_ID_ARG = "EDIT_MESSAGE_ID_ARG"
    }

    private lateinit var binding: FragmentAddMessageBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAddMessageBinding.inflate(inflater, container, false)
        binding.messageCancel.setOnClickListener { dialog?.cancel() }
        binding.messageSubmit.setOnClickListener { submit() }

        editor = EditorViews(this, binding)
        lifecycleScope.launch {
            handleEdit()
            handleDraft()
        }

        return binding.root
    }

    private suspend fun handleEdit() {
        val editId = requireArguments().getInt(EDIT_MESSAGE_ID_ARG)

        val waitDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.please_wait)
            .setMessage(R.string.loading)
            .create()

        // load message asynchronously while showing wait dialog
        // dismiss if we couldn't edit
        waitDialog.show()

        Network.perform(
            networkAction = { Network.loadEditPost(editId) },
            uiAction = { message ->
                if (!message.subject.isNullOrEmpty()) {
                    binding.sourceSubjectHelper.layoutVisibilityBool = true
                    binding.sourceSubject.setText(message.subject)
                }
                binding.sourceText.setText(message.content)
            },
            exceptionAction = {
                Network.reportErrors(context, it)
                dismiss()
            }
        )

        waitDialog.dismiss()
    }

    private fun handleDraft() {
        val messageId = requireArguments().getInt(EDIT_MESSAGE_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${messageId}"

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

    fun submit() {
        val messageId = requireArguments().getInt(EDIT_MESSAGE_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${messageId}"

        val waitDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.please_wait)
            .setMessage(R.string.submitting)
            .create()

        val frgPredicate = { it: Fragment -> it is ContentFragment }
        val curFrg = parentFragmentManager.fragments.reversed().find(frgPredicate) as TopicContentFragment?
        val edited = Network.EditMessageDesc(
            subject = binding.sourceSubject.text.toString(),
            content = binding.sourceText.text.toString()
        )

        lifecycleScope.launch {
            waitDialog.show()

            Network.perform(
                networkAction = { Network.editMessage(messageId, edited) },
                uiAction = { link ->
                    // delete this message from cache, or refresh
                    // will yield nothing
                    SpanCache.removeMessageId(messageId)

                    // delete draft of this message, prevent reinsertion
                    // should be race-free since it's in the same thread as this one (Main UI thread)
                    binding.sourceText.handler?.removeCallbacksAndMessages(contextKey)
                    Database.draftDao().deleteByKey(contextKey)

                    // refresh parent fragment
                    curFrg?.arguments?.putString(TopicContentFragment.URL_ARG, link.toString())
                    curFrg?.refreshContent()

                    dismiss()
                })

            waitDialog.dismiss()
        }
    }
}