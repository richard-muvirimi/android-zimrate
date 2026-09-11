package com.tyganeutronics.myratecalculator.ui.recyclerview.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.interfaces.SpendItemInterface
import com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders.SpendsViewHolder
import com.tyganeutronics.myratecalculator.utils.traits.getContext

class SpendsAdapter(private val itemInterFace: SpendItemInterface) :
    RecyclerView.Adapter<SpendsViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return itemInterFace.items[position].itemId
    }

    override fun getItemCount(): Int {
        return itemInterFace.items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpendsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_purchase, parent, false)
        return SpendsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpendsViewHolder, position: Int) {
        val purchaseEntity = itemInterFace.items[position]

        val duration = DateUtils.getRelativeTimeSpanString(purchaseEntity.createdAt.toEpochMilli())

        holder.title.text = purchaseEntity.description
        holder.description.text = holder.getContext().getString(R.string.rewards_spent_on, duration)
    }
}