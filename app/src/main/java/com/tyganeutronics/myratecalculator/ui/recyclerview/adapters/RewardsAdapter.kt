package com.tyganeutronics.myratecalculator.ui.recyclerview.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.interfaces.RewardItemInterface
import com.tyganeutronics.myratecalculator.ui.recyclerview.viewholders.RewardViewHolder
import com.tyganeutronics.myratecalculator.utils.traits.getContext

class RewardsAdapter(private val itemInterFace: RewardItemInterface) :
    RecyclerView.Adapter<RewardViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return itemInterFace.items[position].itemId
    }

    override fun getItemCount(): Int {
        return itemInterFace.items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RewardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_reward, parent, false)
        return RewardViewHolder(view)
    }

    override fun onBindViewHolder(holder: RewardViewHolder, position: Int) {
        val rewardEntity = itemInterFace.items[position]

        val duration = DateUtils.getRelativeTimeSpanString(rewardEntity.expiresAt.toEpochMilli())

        holder.title.text = rewardEntity.description
        holder.description.text = holder.getContext().getString(R.string.rewards_expires, duration)
        holder.balance.text = holder.getContext()
            .getString(R.string.rewards_balance_amount, rewardEntity.balance, rewardEntity.amount)
    }
}