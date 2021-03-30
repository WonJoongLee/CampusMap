package com.cbnumap.cbnumap.locationrv

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cbnumap.cbnumap.R

class LocAdapter(private val context : Context) : RecyclerView.Adapter<LocAdapter.LocViewHolder>() {

    var data = mutableListOf<LocData>()
    private lateinit var mListener: OnItemClickListener // 리스너 객체 참조를 저장하는 변수

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_location_info, parent, false)
        return LocViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocViewHolder, position: Int) {
        holder.bind(data[position], mListener)
    }

    override fun getItemCount(): Int = data.size

    // onItemClickListener와 setOnItemClickListener는 어댑터 외부에서 리사이클러 뷰 내의 아이템 클릭 처리를 위한 interface와 함수
    interface OnItemClickListener{
        fun onStartButtonClick(v: View?, position: Int)
        fun onEndButtonClick(v: View?, position: Int)
    }

    fun setOnItemClickListener(listener:OnItemClickListener){
        this.mListener = listener
    }

    class LocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        private val buildingNumberTV = itemView.findViewById<TextView>(R.id.itemBuildingNumberTV)
        private val buildingNameTV = itemView.findViewById<TextView>(R.id.itemBuildingNameTV)
        private val startButton = itemView.findViewById<Button>(R.id.setStartButton)
        private val endButton = itemView.findViewById<Button>(R.id.setEndButton)
        fun bind(locData : LocData, mListener:OnItemClickListener){
            val pos = adapterPosition
            buildingNumberTV.text = locData.buildingNumber
            buildingNameTV.text = locData.buildingName

            // 어댑터 외부에서 click listener 처리를 위한 부분
            startButton.setOnClickListener {
                mListener.onStartButtonClick(itemView, pos)
            }
            endButton.setOnClickListener {
                mListener.onEndButtonClick(itemView, pos)
            }
        }
    }
}