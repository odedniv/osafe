package me.odedniv.osafe.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.odedniv.osafe.R
import me.odedniv.osafe.models.encryption.Key

class BiometricsAdapter(
  private val keys: MutableList<Key>,
  private val onBiometricClickedListener: OnBiometricClickedListener,
) : RecyclerView.Adapter<BiometricsAdapter.ViewHolder>() {
  class ViewHolder(val textBiometric: TextView) : RecyclerView.ViewHolder(textBiometric)

  interface OnBiometricClickedListener {
    fun onBiometricClicked(key: Key)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return ViewHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.text_biometric, parent, false)
        as TextView
    )
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val key = keys[position]
    holder.textBiometric.apply {
      text = (key.label as Key.Label.Biometric).createdAt.toString()
      setOnClickListener { onBiometricClickedListener.onBiometricClicked(key) }
    }
  }

  override fun getItemCount() = keys.size

  fun remove(key: Key) {
    val position = keys.indexOf(key)
    keys.removeAt(position)
    notifyItemRemoved(position)
  }
}
