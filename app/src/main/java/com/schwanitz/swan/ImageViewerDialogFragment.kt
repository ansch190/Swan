package com.schwanitz.swan

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.schwanitz.swan.databinding.FragmentImageViewerBinding

class ImageViewerDialogFragment : DialogFragment() {

    private var _binding: FragmentImageViewerBinding? = null
    private val binding get() = _binding!!
    private val TAG = "ImageViewerDialog"

    companion object {
        private const val ARG_ARTWORKS = "artworks"
        private const val ARG_INITIAL_POSITION = "initial_position"

        fun newInstance(artworks: ArrayList<ByteArray>, initialPosition: Int): ImageViewerDialogFragment {
            return ImageViewerDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_ARTWORKS, ArrayList(artworks.map { byteArray ->
                        ParcelableByteArray(byteArray)
                    }))
                    putInt(ARG_INITIAL_POSITION, initialPosition)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Verwende typsichere getParcelableArrayList mit Klasse
        val artworks = arguments?.getParcelableArrayList(ARG_ARTWORKS, ParcelableByteArray::class.java)?.map { it.bytes } ?: emptyList()
        val initialPosition = arguments?.getInt(ARG_INITIAL_POSITION) ?: 0

        val adapter = ImageViewerAdapter(requireContext(), artworks)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(initialPosition, false)

        // Registriere OnPageChangeCallback für Wischereignisse
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                Log.d(TAG, "Page selected: $position")
            }
        })
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Hilfsklasse, um ByteArray als Parcelable zu behandeln
    private class ParcelableByteArray(val bytes: ByteArray) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(parcel.createByteArray()!!)

        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeByteArray(bytes)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : android.os.Parcelable.Creator<ParcelableByteArray> {
            override fun createFromParcel(parcel: android.os.Parcel): ParcelableByteArray {
                return ParcelableByteArray(parcel)
            }

            override fun newArray(size: Int): Array<ParcelableByteArray?> {
                return arrayOfNulls(size)
            }
        }
    }
}