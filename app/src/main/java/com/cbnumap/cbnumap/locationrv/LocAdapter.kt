package com.cbnumap.cbnumap.locationrv

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cbnumap.cbnumap.R

class LocAdapter(private val context: Context) : RecyclerView.Adapter<LocAdapter.LocViewHolder>() {

    var data = mutableListOf<LocData>()
    private lateinit var mListener: OnItemClickListener // 리스너 객체 참조를 저장하는 변수
    private var selectedItemPosition = -1
    private var clicked = false
    var selectedStartText = ""
    var selectedEndText = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_location_info, parent, false)
        return LocViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocViewHolder, position: Int) {
        holder.bind(data[position], mListener, context)

//        holder.startButton.setOnClickListener {
//            mListener.onStartButtonClick(position)
//
//            Log.e("pos", "$position, $selectedItemPosition")
//
//            selectedItemPosition = position
//
//            if(position == selectedItemPosition){
//                holder.startButton.setTextColor(context.getColor(R.color.white))
//                holder.startButton.backgroundTintList = context.getColorStateList(R.color.crimson)
//            }
//
//
//            notifyItemChanged(selectedItemPosition)
//        }
//
//        holder.endButton.setOnClickListener {
//            mListener.onEndButtonClick(position)
//
//            selectedEndText = holder.endButton.text.toString()
//
//            if(selectedEndText == holder.endButton.text.toString()){
//                holder.endButton.setTextColor(context.getColor(R.color.white))
//                holder.endButton.backgroundTintList = context.getColorStateList(R.color.crimson)
//            }
//        }
    }

    override fun getItemCount(): Int = data.size

    // onItemClickListener와 setOnItemClickListener는 어댑터 외부에서 리사이클러 뷰 내의 아이템 클릭 처리를 위한 interface와 함수
    interface OnItemClickListener {
        fun onStartButtonClick(position: Int)
        fun onEndButtonClick(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.mListener = listener
    }

    class LocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val buildingNumberTV = itemView.findViewById<TextView>(R.id.itemBuildingNumberTV)
        private val buildingNameTV = itemView.findViewById<TextView>(R.id.itemBuildingNameTV)
        val startButton : Button = itemView.findViewById(R.id.setStartButton)
        val endButton : Button = itemView.findViewById(R.id.setEndButton)
        fun bind(locData: LocData, mListener: OnItemClickListener, context: Context) {
            val pos = adapterPosition
            buildingNumberTV.text = locData.buildingNumber
            buildingNameTV.text = locData.buildingName

             //어댑터 외부에서 click listener 처리를 위한 부분
            startButton.setOnClickListener {
                mListener.onStartButtonClick(pos)
            }

            endButton.setOnClickListener {
                mListener.onEndButtonClick(pos)
            }
        }
    }
}