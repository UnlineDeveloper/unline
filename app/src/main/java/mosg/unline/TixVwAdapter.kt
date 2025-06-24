package mosg.unline

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TixVwAdapter<T:TextView>(private val items:ArrayList<Int>,private val create:(ViewGroup)->T)
    :RecyclerView.Adapter<TixVwHolder>() {
    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): TixVwHolder {
        return TixVwHolder(create(p0).apply {
            textAlignment= View.TEXT_ALIGNMENT_CENTER
        })
    }

    override fun onBindViewHolder(p0: TixVwHolder, p1: Int) {
        val v=p0.itemView
        if(v is TextView)
            v.text=items[p1].toString()
    }

    override fun getItemCount()=items.size
}