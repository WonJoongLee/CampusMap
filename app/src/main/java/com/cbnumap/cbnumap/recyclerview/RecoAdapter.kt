package com.cbnumap.cbnumap.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cbnumap.cbnumap.R

class RecoAdapter(private val context : Context) : RecyclerView.Adapter<RecoAdapter.RecoViewHolder>() {

    var data = mutableListOf<RecoData>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_location, parent, false)
        return RecoViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecoViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    class RecoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val locationName = itemView.findViewById<TextView>(R.id.locationNameTV)
        fun bind(recoData : RecoData){
            locationName.text = recoData.locationName
        }
    }
}