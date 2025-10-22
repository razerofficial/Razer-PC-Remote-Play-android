package com.razer.neuron.common

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.razer.neuron.RnApp


abstract class BaseDataAdapter<T> : RecyclerView.Adapter<BaseViewHolder<T>> {

    fun interface OnBindListener{
        fun onBind(v : View, t : Any, position: Int)
    }

    /**
     * [onBindListener] will be called when [onBindViewHolder] is called
     */
    var onBindListener : OnBindListener? = null

    protected val data = mutableListOf<T>()
    var adapterRecyclerView : RecyclerView? = null
        protected set

    constructor() : super()

    constructor(dataList: List<T>) : super() {
        data.addAll(dataList)
    }


    /**
     * Find the first index and item in [data] based on a [predicate]
     */
    fun findItem(predicate: (T) -> Boolean): Pair<Int, T>? =
        data.indexOfFirst { dataItem -> predicate(dataItem) }.takeIf { it >= 0 }?.let { index ->
            data.getOrNull(index)?.let { foundItem -> index to foundItem }
        }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.adapterRecyclerView = recyclerView
    }

    open fun setData(dataList: List<T>?) {
        dataList?.apply {
            data.clear()
            data.addAll(dataList)
            notifyDataSetChanged()
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<T>, position: Int) {
        val dataItem = data.getOrNull(position) ?: return
        holder.bind(dataItem , position)
        onBindListener?.onBind(holder.itemView, dataItem, position)
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder<T>) {
        holder.onAttached()
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder<T>) {
        holder.onDetached()
    }

    override fun onViewRecycled(holder: BaseViewHolder<T>) {
        holder.onRecycled()
    }


    override fun getItemCount() = data.size

    abstract inner class BaseDataViewHolder : BaseViewHolder<T> {
        constructor(parent: ViewGroup, layoutId: Int) : super(parent, layoutId)
        constructor(binding: ViewBinding) : super(binding)
    }

    fun copyItems() = data.toList()
}

abstract class BaseViewHolder<T> : RecyclerView.ViewHolder {

    constructor(parent: ViewGroup, layoutId: Int)
            : super(LayoutInflater.from(parent.context).inflate(layoutId, parent, false))

    constructor(binding: ViewBinding) : super(binding.root)

    open fun bind(data: T, position: Int) = Unit

    open fun onRecycled() = Unit

    open fun onAttached() = Unit

    open fun onDetached() = Unit

    fun resources() : Resources = itemView.resources ?: RnApp.appContext.resources
    
}
