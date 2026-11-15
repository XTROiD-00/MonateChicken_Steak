package com.example.monatechickensteak.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.monatechickensteak.R
import com.example.monatechickensteak.data.AuthRepository
import com.example.monatechickensteak.data.CartManager
import com.example.monatechickensteak.data.CardDetails
import com.example.monatechickensteak.data.OrderRepository
import com.example.monatechickensteak.data.PaymentResult
import com.example.monatechickensteak.data.Payments
import com.example.monatechickensteak.data.SessionManager
import com.example.monatechickensteak.databinding.DialogCardPaymentBinding
import com.example.monatechickensteak.databinding.FragmentCartBinding
import com.example.monatechickensteak.util.CardValidator
import com.example.monatechickensteak.util.CheckoutOptions
import com.example.monatechickensteak.util.CheckoutValidator
import com.example.monatechickensteak.util.LoyaltyRules
import com.example.monatechickensteak.util.toRand
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CartAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CartAdapter(
            onPlus = { CartManager.increase(it.item.id) },
            onMinus = { CartManager.decrease(it.item.id) },
            onRemove = { CartManager.remove(it.item.id) }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        // Collection time dropdown, starting on the customer's default from Settings
        binding.dropdownTime.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, CheckoutOptions.collectionTimes)
        )
        binding.dropdownTime.setText(CheckoutOptions.validTime(SessionManager.defaultCollectionTime()), false)

        // Pre-select the default payment method from Settings (if one was chosen)
        val defaultPayment = CheckoutOptions.validPaymentOrNull(SessionManager.defaultPayment())
        if (defaultPayment != null) {
            for (i in 0 until binding.rgPayment.childCount) {
                val child = binding.rgPayment.getChildAt(i)
                if (child is RadioButton && child.text.toString() == defaultPayment) {
                    child.isChecked = true
                }
            }
        }

        binding.btnBrowse.setOnClickListener { findNavController().navigate(R.id.menuFragment) }
        binding.btnPlaceOrder.setOnClickListener { placeOrder() }

        // Redraw the screen every time the cart changes
        CartManager.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            val empty = items.isEmpty()
            binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            binding.content.visibility = if (empty) View.GONE else View.VISIBLE

            binding.tvSubtotal.text = CartManager.subtotal().toRand()
            binding.tvDelivery.text = CartManager.deliveryFee().toRand()
            binding.tvTotal.text = CartManager.total().toRand()
        }
    }

    private fun selectedPayment(): String? {
        val id = binding.rgPayment.checkedRadioButtonId
        return if (id == -1) null else binding.root.findViewById<RadioButton>(id).text.toString()
    }

    private fun placeOrder() {
        val items = CartManager.items.value.orEmpty()
        val payment = selectedPayment()

        val error = CheckoutValidator.error(items.isEmpty(), payment)
        if (error != null || payment == null) {
            Snackbar.make(binding.root, error ?: "Choose a payment method", Snackbar.LENGTH_LONG).show()
            return
        }

        val total = CartManager.total()
        val collectionTime = binding.dropdownTime.text.toString()

        if (payment == "Card Payment") {
            // Take the card first. The order is only saved after the payment is approved.
            askForCard(total) { paid -> saveOrder(items, total, collectionTime, payment, paid) }
        } else {
            saveOrder(items, total, collectionTime, payment, null)
        }
    }

    /** Shows the card form. The form stays open until the details are valid and the payment is approved. */
    private fun askForCard(total: Double, onApproved: (PaymentResult.Approved) -> Unit) {
        val form = DialogCardPaymentBinding.inflate(layoutInflater)
        form.tvAmount.text = "Amount to pay: ${total.toRand()}"

        form.etCardNumber.doAfterTextChanged { editable ->
            val grouped = CardValidator.groupNumber(editable?.toString().orEmpty())
            if (grouped != editable?.toString()) {
                form.etCardNumber.setText(grouped)
                form.etCardNumber.setSelection(grouped.length)
            }
            form.tilCardNumber.error = null
        }
        form.etExpiry.doAfterTextChanged { editable ->
            val digits = CardValidator.digitsOnly(editable?.toString().orEmpty()).take(4)
            val shaped = if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits
            if (shaped != editable?.toString()) {
                form.etExpiry.setText(shaped)
                form.etExpiry.setSelection(shaped.length)
            }
            form.tilExpiry.error = null
        }
        form.etCardName.doAfterTextChanged { form.tilCardName.error = null }
        form.etCvv.doAfterTextChanged { form.tilCvv.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Card payment")
            .setView(form.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Pay ${total.toRand()}", null)
            .create()
        dialog.show()

        val payButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        payButton.setOnClickListener {
            val card = CardDetails(
                name = form.etCardName.text?.toString().orEmpty().trim(),
                number = form.etCardNumber.text?.toString().orEmpty(),
                expiry = form.etExpiry.text?.toString().orEmpty(),
                cvv = form.etCvv.text?.toString().orEmpty()
            )
            val nameError = CardValidator.nameError(card.name)
            val numberError = CardValidator.numberError(card.number)
            val expiryError = CardValidator.expiryError(card.expiry)
            val cvvError = CardValidator.cvvError(card.cvv)
            form.tilCardName.error = nameError
            form.tilCardNumber.error = numberError
            form.tilExpiry.error = expiryError
            form.tilCvv.error = cvvError
            if (listOf(nameError, numberError, expiryError, cvvError).any { it != null }) return@setOnClickListener

            payButton.isEnabled = false
            payButton.text = "Processing..."
            dialog.setCancelable(false)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = Payments.gateway.charge(total, card)) {
                    is PaymentResult.Approved -> {
                        dialog.dismiss()
                        onApproved(result)
                    }
                    is PaymentResult.Declined -> {
                        payButton.isEnabled = true
                        payButton.text = "Pay ${total.toRand()}"
                        dialog.setCancelable(true)
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                        form.tilCardNumber.error = result.reason
                    }
                }
            }
        }
    }

    private fun saveOrder(
        items: List<com.example.monatechickensteak.model.CartItem>,
        total: Double,
        collectionTime: String,
        payment: String,
        paid: PaymentResult.Approved?
    ) {
        val userId = SessionManager.userId()
        binding.btnPlaceOrder.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            // NonCancellable: once the customer taps Place order, the order and the points
            // are always saved together, even if they leave the screen at that moment.
            val result = withContext(NonCancellable) {
                try {
                    val order = OrderRepository.placeOrder(userId, items, total, collectionTime, payment)
                    val earned = LoyaltyRules.pointsForOrder(order.total)
                    AuthRepository.addLoyaltyPoints(userId, earned)
                    order to earned
                } catch (e: Exception) {
                    null
                }
            }
            if (result == null) {
                binding.btnPlaceOrder.isEnabled = true
                Snackbar.make(binding.root, "Could not place the order. Please try again.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val (order, earned) = result
            CartManager.clear()
            binding.btnPlaceOrder.isEnabled = true

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Order placed! 🎉")
                .setMessage(
                    "Order #${order.id}\n" +
                            "Total: ${order.total.toRand()}\n" +
                            "Collection: ${order.collectionTime}\n" +
                            "Payment: ${order.paymentMethod}" +
                            (if (paid != null) " (card ending ${paid.last4}, ref ${paid.reference})" else "") + "\n" +
                            "Loyalty points earned: +$earned"
                )
                .setCancelable(false)
                .setPositiveButton("Track order") { _, _ ->
                    findNavController().navigate(R.id.ordersFragment)
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
