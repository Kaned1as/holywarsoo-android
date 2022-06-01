package com.kanedias.holywarsoo

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kanedias.holywarsoo.databinding.FragmentLoginBinding
import com.kanedias.holywarsoo.model.MainPageModel
import com.kanedias.holywarsoo.service.Network
import kotlinx.coroutines.*

/**
 * Fragment responsible for adding account. Appears when you click "add account" in the sidebar.
 * This may be either registration or logging in.
 *
 * @author Kanedias
 *
 * Created on 2017-11-11
 */
class LoginFragment : Fragment() {

    private lateinit var progressDialog: Dialog
    private lateinit var mainPageModel: MainPageModel
    private lateinit var mainPageView: FragmentLoginBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mainPageView = FragmentLoginBinding.inflate(inflater, container, false)
        mainPageModel = ViewModelProvider(requireActivity()).get(MainPageModel::class.java)

        mainPageView.confirmButton.setOnClickListener { doLogin() }
        progressDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.please_wait)
                .setMessage(R.string.logging_in)
                .create()

        return mainPageView.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressDialog.dismiss()
    }

    /**
     * Creates session for the user, saves auth and closes fragment on success.
     */
    fun doLogin() {
        lifecycleScope.launch {
            progressDialog.show()

            Network.perform(
                networkAction = {
                    Network.login(
                        username = mainPageView.accUsernameInput.text.toString(),
                        password = mainPageView.accPasswordInput.text.toString())
                },
                uiAction = {
                    Toast.makeText(requireContext(), R.string.login_successful, Toast.LENGTH_SHORT).show()
                    mainPageModel.account.value = Network.getUsername()
                    parentFragmentManager.popBackStack()
                }
            )

            progressDialog.hide()
        }
    }
}