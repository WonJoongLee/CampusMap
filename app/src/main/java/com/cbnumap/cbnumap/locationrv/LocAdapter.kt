package com.cbnumap.cbnumap.locationrv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cbnumap.cbnumap.R

class LocAdapter(private val context : Context) : RecyclerView.Adapter<LocAdapter.LocViewHolder>() {

    var data = mutableListOf<LocData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_location_info, parent, false)
        return LocViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    class LocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val buildingNumberTV = itemView.findViewById<TextView>(R.id.itemBuildingNumberTV)
        private val buildingNameTV = itemView.findViewById<TextView>(R.id.itemBuildingNameTV)
        fun bind(locData : LocData){
            buildingNumberTV.text = locData.buildingNumber
            buildingNameTV.text = locData.buildingName
        }
    }
}