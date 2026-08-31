package com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.tyganeutronics.myratecalculator.R

class SectionHeaderViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.item_rate_header, parent, false)
) {
    private val txtTitle: TextView = itemView.findViewById(R.id.txt_section_title)

    fun bind(@StringRes titleRes: Int) {
        txtTitle.setText(titleRes)
    }
}
