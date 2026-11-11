package com.example.iotapp.ui.chat

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.iotapp.R
import com.example.iotapp.databinding.FragmentChatBinding
import com.example.iotapp.ui.common.VmFactory
import kotlinx.coroutines.launch

/** 对话页：气泡列表 + 输入框。回复和动作文案都直接用后端返回的文本。 */
class ChatFragment : Fragment(R.layout.fragment_chat) {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val vm: ChatViewModel by viewModels { VmFactory() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentChatBinding.bind(view)

        binding.btnSend.setOnClickListener { send() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.messages.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.sending.collect { sending ->
                    binding.btnSend.isEnabled = !sending
                    binding.btnSend.text = getString(if (sending) R.string.chat_sending else R.string.chat_send)
                }
            }
        }
    }

    private fun send() {
        val text = binding.etInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        vm.send(text)
        binding.etInput.setText("")
    }

    private fun render(messages: List<ChatMessage>) {
        binding.llMessages.removeAllViews()
        messages.forEach { addBubble(it) }
        binding.svMessages.post { binding.svMessages.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(message: ChatMessage) {
        val bubble = TextView(requireContext()).apply {
            text = message.text
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(
                if (message.fromUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_bot
            )
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (message.fromUser) R.color.bubble_user_text else R.color.text_primary
                )
            )
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (message.fromUser) Gravity.END else Gravity.START
            topMargin = dp(8)
        }
        binding.llMessages.addView(bubble, lp)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
