package mosg.unline

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private var tix=0
    private val remain=ArrayList<Int>()
    private var tixVwAdapter:TixVwAdapter<TextView>?=null
    private var numVw:TextView?=null
    private var linearLayout:LinearLayout?=null
    private var skipped:ArrayList<Int>?=null
    private var skipAdapter:TixVwAdapter<Button>?=null
    private var noDraw=true
    private val startItemId=1
    private val restartItemId=2
    private val logItemId=3
    private var scrollView:ScrollView?=null
    private var tixView:EditText?=null
    private val okBtnCaption="OK"
    private fun getFile(fileName:String)=File(filesDir,fileName)
    private val logFile get()=getFile("log.txt")
    private val maxTix=999
    private fun log(msg:String)=FileOutputStream(logFile,true).use{
        it.writer().use{writer->
            writer.appendLine(msg)
        }
    }
    private fun showThrowable(throwable:Throwable){
        val msg=if (throwable.message != null) throwable.localizedMessage else throwable.toString()
        log(msg)
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            showThrowable(e)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        log("App restarted")
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tixView=findViewById(R.id.ticketsEdt)
        findViewById<Button>(R.id.incrementBtn).setOnClickListener {
            setTixNum(tixView!!){
                if (it != null)
                    max(minTix, it + 1)
                else minTix
            }
        }
        tixView?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event.keyCode == KeyEvent.KEYCODE_ENTER) validateTixNum(v)
            false
        }
        tixFile.apply{
            val num=tixFile.length().toInt()
            if(num>maxTix)
                delete()
            else if(num>0){
                tix=num
                var drawn=-1
                tixFile.readBytes().forEachIndexed { idx, byte ->
                    when(byte.toInt()){
                        1->drawn=idx+1
                        2->{}
                        3->{
                            if(skipped==null)skipped= arrayListOf(idx+1)
                            else skipped!!.add(idx+1)
                        }
                        else->remain.add(idx+1)
                    }
                }
                createViews()
                if(drawn>=0)showTicket(drawn)else numVw?.isVisible=false
                if(skipped!=null)addSkippedView()
                noDraw=false
                tixView?.setText("$num")
            }
        }
    }
    private fun validateTixNum(v:TextView=tixView!!){
        val minimum = minTix
        setTixNum(v){
            if(it==null||it<minimum)minimum else it
        }
    }
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("super.onBackPressed()", "androidx.appcompat.app.AppCompatActivity"))
    override fun onBackPressed() {
        super.onBackPressed()
        validateTixNum()
    }
    private fun addMenuItem(menu:Menu,title:String,itemId:Int)=menu.add(Menu.NONE,itemId,Menu.NONE,title)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let{
            addMenuItem(it,"Log",logItemId)
            if(!noDraw) {
                addMenuItem(it, "Draw", startItemId).apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                addMenuItem(it, "Restart", restartItemId)
            }
        }
        return true
    }
    private val tixFile get()=getFile("tix")
    private fun tixRAF(pos:Int,callback:(RandomAccessFile)->Unit)=RandomAccessFile(tixFile,"rws").use{
        if(pos>0)it.seek(pos.toLong())
        callback(it)
    }
    private fun addTix(num:Int){
        for(i in tix+1..num)remain.add(i)
        tixRAF(tix){
            it.write(ByteArray(num-tix))
        }
        tixVwAdapter?.notifyItemRangeChanged(tix, num - tix)
        tix = num
        log("$num tix total")
    }
    private val minTix get()=max(1, tix)
    private val chosenNum get()=numInVw(numVw!!)
    private fun createViews(){
        scrollView = ScrollView(this).apply {
            with(ConstraintLayout.LayoutParams(-1, 0)) {
                topToTop = 0
                bottomToTop = R.id.incrementBtn
                layoutParams = this
            }
        }
        linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        numVw = TextView(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(-1, -2)
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        linearLayout?.addView(numVw)
        val recyclerView = RecyclerView(this)
        tixVwAdapter = TixVwAdapter(remain) { group ->
            TextView(group.context)
        }
        recyclerView.layoutManager = StaggeredGridLayoutManager(5, 1)
        recyclerView.adapter = tixVwAdapter
        recyclerView.layoutParams=LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        linearLayout?.addView(recyclerView)
        scrollView!!.addView(linearLayout)
        mainVw.addView(scrollView)
        numVw?.setOnClickListener {
            updateTicket(chosenNum,2)
            draw()
        }
    }
    private fun addSkippedView(){
        skipAdapter = TixVwAdapter(skipped!!) { viewGroup ->
            Button(viewGroup.context).apply {
                setOnClickListener {
                    val n= numInVw(this)
                    skipAdapter?.notifyItemRemoved(removeTicket(skipped!!,n))
                    log("Claimed ticket $n")
                    updateTicket(n,2)
                }
            }
        }
        RecyclerView(this@MainActivity).also {
            it.adapter = skipAdapter
            it.layoutManager = StaggeredGridLayoutManager(4, 1)
            linearLayout?.addView(it)
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try{
            when(item.itemId){
                startItemId-> {
                    val num = getTixNum(tixView!!) {
                        val minimum = minTix
                        if (it == null || it < minimum) minimum else it
                    }
                    if (num > tix) {
                        addTix(num)
                        if (scrollView == null) {
                            createViews()
                            choose()
                            return true
                        }
                    }
                    if(remain.size==0){
                        restart()
                        return true
                    }
                    if(numVw?.isVisible==true) {
                        val chosen = chosenNum
                        log("Skipped ticket $chosen")
                        updateTicket(chosen,3)
                        draw()
                        if (skipped == null) {
                            skipped = arrayListOf(chosen)
                            addSkippedView()
                        } else {
                            var idx = skipped!!.indexOfFirst {
                                it > chosen
                            }
                            if (idx < 0) idx = skipped!!.size
                            skipped?.add(idx, chosen)
                            skipAdapter?.notifyItemInserted(idx)
                        }
                    }else choose()
                    true
                }
                restartItemId->{
                    restart()
                    true
                }
                logItemId->{
                    AlertDialog.Builder(this).apply{
                        setView(ScrollView(context).apply{
                            addView(TextView(context).apply{
                                val file=logFile
                                var txt=""
                                val logSize=file.length()
                                if(logSize>0)
                                    txt=FileInputStream(file).use{
                                        it.reader().readText()
                                    }
                                if(txt.isEmpty())txt="Log is empty"
                                text=txt
                            })
                        })
                        setPositiveButton(okBtnCaption){ _, _->}
                        if(logFile.exists())setNeutralButton("Clear"){_,_->
                            logFile.delete()
                        }
                        show()
                    }
                    true
                }
                else->super.onOptionsItemSelected(item)
            }
        }catch (throwable:Throwable){
            showThrowable(throwable)
            true
        }
    }
    private fun restart(){
        AlertDialog.Builder(this).apply{
            setView(LinearLayout(context).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(50,50,50,50)
                addView(TextView(context).apply{
                    text= context.getString(R.string.restartPrompt)
                    textSize=20f
                    textAlignment=View.TEXT_ALIGNMENT_CENTER
                    layoutParams=LinearLayout.LayoutParams(-1,-1).apply{
                        gravity=Gravity.CENTER_VERTICAL
                    }
                })
            })
            setPositiveButton(okBtnCaption) { _, _ ->
                log("Restarted")
                numVw?.isVisible=false
                val remaining=remain.size
                if(remaining>0){
                    remain.clear()
                    tixVwAdapter?.notifyItemRangeRemoved(0,remaining)
                }
                tixView?.setText("")
                noDraw=true
                invalidateOptionsMenu()
                tix=0
                skipped?.let{
                    val itemCount=it.size
                    if(itemCount>0) {
                        it.clear()
                        skipAdapter?.notifyItemRangeRemoved(0, itemCount)
                    }
                }
                tixFile.apply{
                    if(exists())delete()
                }
            }
            setNegativeButton("Cancel"){_,_->}
            show()
        }
    }
    private fun getTixNum(vw:TextView, callback:(Int?)->Int)=vw.text.toString().toIntOrNull().let(callback).coerceAtMost(maxTix)
    private fun setTixNum(vw:TextView, callback:(Int?)->Int){
        val num=getTixNum(vw,callback)
        vw.text = (if(num>tix) {
            if(noDraw){
                noDraw=false
                invalidateOptionsMenu()
            }
            else if(tix>0)addTix(num)
            num
        }else tix).toString()
    }
    private fun removeTicket(list:ArrayList<Int>,num:Int)=list.indexOf(num).also{
        list.removeAt(it)
    }
    private fun draw(){
        if(remain.size==0) {
            numVw?.isVisible = false
            log("All tickets have been drawn")
        }
        else
            choose()
    }
    private fun numInVw(view:TextView)=view.text.toString().toInt()
    private val mainVw get()=findViewById<ConstraintLayout>(R.id.main)
    private fun showTicket(i:Int) {
        numVw?.apply {
            text = "$i"
            textSize = max(100f, 400f - log10(i.toDouble()).toInt() * 100)
        }
    }
    private fun choose(){
        numVw?.apply{
            val i=remain[Random.nextInt(remain.size)]
            showTicket(i)
            isVisible=true
            tixVwAdapter?.notifyItemRemoved(removeTicket(remain,i))
            log("Drew ticket $i")
            updateTicket(i,1)
        }
    }
    private fun updateTicket(index:Int,byte:Int)=tixRAF(index-1){
        it.write(byte)
    }
}