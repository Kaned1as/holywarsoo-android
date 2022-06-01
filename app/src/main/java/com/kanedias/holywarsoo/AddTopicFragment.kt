package com.kanedias.holywarsoo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kanedias.holywarsoo.database.entities.OfflineDraft
import com.kanedias.holywarsoo.databinding.FragmentAddMessageBinding
import com.kanedias.holywarsoo.misc.layoutVisibilityBool
import com.kanedias.holywarsoo.misc.showFullscreenFragment
import com.kanedias.holywarsoo.service.Database
import com.kanedias.holywarsoo.service.Network
import kotlinx.coroutines.launch
import java.util.*

/**
 * Fragment responsible for created a new topic in specified forum.
 * Forum id **must** be sent as an argument.
 *
 * @author Kanedias
 *
 * Created on 2019-12-29
 */
class AddTopicFragment: EditorFragment() {

    companion object {
        const val DB_CONTEXT_PREFIX = "newtopic"

        /**
         * Required, the forum in which topic should be created
         */
        const val FORUM_ID_ARG = "FORUM_ID_ARG"
    }

    private lateinit var binding: FragmentAddMessageBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAddMessageBinding.inflate(inflater, container, false)
        binding.messageCancel.setOnClickListener { dialog?.cancel() }
        binding.messageSubmit.setOnClickListener { submit() }

        editor = EditorViews(this, binding)
        binding.sourceSubject.requestFocus()
        binding.sourceSubjectHelper.layoutVisibilityBool = true

        handleDraft()

        return binding.root
    }

    private fun handleDraft() {
        val forumId = requireArguments().getInt(FORUM_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${forumId}"

        // if draft exists with this key, fill content with it
        Database.draftDao().getByKey(contextKey)?.let {
            binding.sourceSubject.setText(it.title)
            binding.sourceText.setText(it.content)
            binding.sourceText.setSelection(binding.sourceText.length())
        }

        // delay saving text a bit so database won't be spammed with it
        binding.sourceText.addTextChangedListener { text ->
            val action = {
                val draft = OfflineDraft(
                    createdAt = Date(),
                    ctxKey = contextKey,
                    title = binding.sourceSubject.text.toString(),
                    content = text.toString())
                Database.draftDao().insertDraft(draft)
            }
            binding.sourceText.removeCallbacks(action)
            binding.sourceText.postDelayed(action, 1500)
        }
    }

    fun submit() {
        val forumId = requireArguments().getInt(FORUM_ID_ARG)
        val contextKey = "${DB_CONTEXT_PREFIX}-${forumId}"

        val waitDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.please_wait)
            .setMessage(R.string.submitting)
            .create()

        lifecycleScope.launch {
            waitDialog.show()

            Network.perform(
                networkAction = { Network.postTopic(forumId, binding.sourceSubject.text.toString(), binding.sourceText.text.toString()) },
                uiAction = { link ->
                    // delete draft of this message, prevent reinsertion
                    // should be race-free since it's in the same thread as this one (Main UI thread)
                    binding.sourceText.handler?.removeCallbacksAndMessages(contextKey)
                    Database.draftDao().deleteByKey(contextKey)

                    // open new topic fragment
                    val fragment = TopicContentFragment().apply {
                        arguments = Bundle().apply {
                            putString(TopicContentFragment.URL_ARG, link.toString())
                        }
                    }
                    activity?.showFullscreenFragment(fragment)

                    dismiss()
                })

            waitDialog.dismiss()
        }
    }
}