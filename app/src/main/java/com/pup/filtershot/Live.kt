package com.pup.filtershot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [Live.newInstance] factory method to
 * create an instance of this fragment.
 */
class Live : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_live, container, false)

        // Reference to the Switch and TextView
        val switch: Switch = view.findViewById(R.id.switch2)
        val resultTextView: TextView = view.findViewById(R.id.state)

        // Initial text for the TextView
        resultTextView.text = "FilterShot is Paused"
        resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Error))

        // Set a listener on the Switch to detect changes
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // If the switch is turned on
                resultTextView.text = "FilterShot is Currently Running"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Go))
            } else {
                // If the switch is turned off
                resultTextView.text = "FilterShot is Paused"
                resultTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Error))
            }
        }

        return view
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Live.
         */
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Live().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
