package com.example.monatechickensteak.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.monatechickensteak.data.AuthRepository
import com.example.monatechickensteak.data.AuthResult
import com.example.monatechickensteak.data.ResetRequest
import com.example.monatechickensteak.databinding.FragmentForgotPasswordBinding
import com.example.monatechickensteak.util.Validators
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Two steps: get a code for an email, then use the code to choose a new password. */
class ForgotPasswordFragment : Fragment() {

    companion object {
        const val ARG_EMAIL = "email"
    }

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString(ARG_EMAIL)?.takeIf { it.isNotBlank() }?.let { binding.etEmail.setText(it) }

        binding.btnSendCode.setOnClickListener { sendCode() }
        binding.btnReset.setOnClickListener { resetPassword() }
        binding.tvBackToLogin.setOnClickListener { findNavController().popBackStack() }

        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etCode.doAfterTextChanged { binding.tilCode.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirm.doAfterTextChanged { binding.tilConfirm.error = null }
    }

    private fun email() = binding.etEmail.text?.toString()?.trim().orEmpty()

    private fun sendCode() {
        val emailError = Validators.emailError(email())
        binding.tilEmail.error = emailError
        if (emailError != null) return

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = AuthRepository.requestPasswordReset(email())) {
                is ResetRequest.Error -> {
                    setLoading(false)
                    binding.tilEmail.error = result.message
                }
                is ResetRequest.CodeCreated -> {
                    setLoading(false)
                    binding.stepTwo.visibility = View.VISIBLE
                    binding.btnSendCode.text = "Send a new code"
                    binding.tvIntro.text = "Enter the code and choose a new password. The code works for 10 minutes."
                    // No mail server in this app, so the code is shown here. With a server it would be emailed.
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Your reset code")
                        .setMessage(
                            "${result.code}\n\nDemo mode: the app has no email server, so the code is shown here. " +
                                    "A live system would email it to ${email()}."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun resetPassword() {
        val code = binding.etCode.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()

        val codeError = Validators.resetCodeError(code)
        val passwordError = Validators.newPasswordError(password)
        val confirmError = Validators.confirmPasswordError(password, confirm)
        binding.tilCode.error = codeError
        binding.tilPassword.error = passwordError
        binding.tilConfirm.error = confirmError
        if (codeError != null || passwordError != null || confirmError != null) return

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = AuthRepository.resetPassword(email(), code, password)) {
                is AuthResult.Success -> {
                    Snackbar.make(requireActivity().findViewById(android.R.id.content),
                        "Password updated. Please log in.", Snackbar.LENGTH_LONG).show()
                    findNavController().popBackStack()
                }
                is AuthResult.Error -> {
                    setLoading(false)
                    binding.tilCode.error = result.message
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendCode.isEnabled = !loading
        binding.btnReset.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
