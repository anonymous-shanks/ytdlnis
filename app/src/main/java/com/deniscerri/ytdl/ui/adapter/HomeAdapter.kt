package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class HomeAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<ResultItem?, HomeAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val checkedItems: ArrayList<String>
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        checkedItems = ArrayList()
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView

        init {
            cardView = itemView.findViewById(R.id.result_card_view)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
                .inflate(R.layout.result_card, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        val card = holder.cardView
        card.popup()

        val uiHandler = Handler(Looper.getMainLooper())
        val thumbnail = card.findViewById<ImageView>(R.id.result_image_view)

        // THUMBNAIL ----------------------------------
        val hideThumb = sharedPreferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home")
        uiHandler.post { thumbnail.loadThumbnail(hideThumb, video!!.thumb) }

        // TITLE  ----------------------------------
        val videoTitle = card.findViewById<TextView>(R.id.result_title)
        var title = video!!.title.ifBlank { video.url }
        if (title.length > 100) {
            title = title.substring(0, 40) + "..."
        }
        videoTitle.text = title

        // DYNAMIC UI SWITCH LOGIC ----------------------------------
        val durationTop = card.findViewById<TextView>(R.id.duration_top)
        val publishedTimeTop = card.findViewById<TextView>(R.id.published_time_top)
        
        val durationBottom = card.findViewById<TextView>(R.id.duration_bottom)
        val publishedTimeBottom = card.findViewById<TextView>(R.id.published_time_bottom)
        val authorBottom = card.findViewById<TextView>(R.id.author_bottom)
        val authorBottomIcon = card.findViewById<ImageView>(R.id.author_bottom_icon)

        authorBottom.setOnClickListener(null)
        authorBottomIcon.setOnClickListener(null)

        val durationText = video.duration
        val timeText = video.publishedTime
        val authorText = video.author
        
        val isChannelView = video.playlistTitle.isNotEmpty() && video.playlistTitle != "YTDLNIS_SEARCH"

        val channelClickListener = View.OnClickListener {
            val channelUrl = video.uploaderUrl
            if (channelUrl.isNotEmpty()) {
                var fullUrl = channelUrl
                if (!fullUrl.startsWith("http")) {
                    fullUrl = if (fullUrl.startsWith("//")) "https:$fullUrl" else "https://www.youtube.com$fullUrl"
                }
                onItemClickListener.onAuthorClick(fullUrl)
            } else {
                android.widget.Toast.makeText(activity, "Channel URL missing!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        if (isChannelView) {
            // Inside Channel: Hide Top Info. Show Duration/Date at bottom. Hide Author.
            durationTop.visibility = View.GONE
            publishedTimeTop.visibility = View.GONE

            authorBottom.visibility = View.GONE
            authorBottomIcon.visibility = View.GONE

            durationBottom.visibility = if (durationText.isNotEmpty()) View.VISIBLE else View.GONE
            durationBottom.text = durationText

            publishedTimeBottom.visibility = if (timeText.isNotEmpty()) View.VISIBLE else View.GONE
            publishedTimeBottom.text = timeText
        } else {
            // Outside Channel (Normal Search): Show Duration/Date Top. Show Icon+Author at bottom.
            durationTop.visibility = if (durationText.isNotEmpty()) View.VISIBLE else View.GONE
            durationTop.text = durationText

            publishedTimeTop.visibility = if (timeText.isNotEmpty()) View.VISIBLE else View.GONE
            publishedTimeTop.text = timeText

            authorBottom.visibility = View.VISIBLE
            authorBottomIcon.visibility = View.VISIBLE
            authorBottom.text = authorText
            
            authorBottom.setOnClickListener(channelClickListener)
            authorBottomIcon.setOnClickListener(channelClickListener)

            durationBottom.visibility = View.GONE
            publishedTimeBottom.visibility = View.GONE
        }

        // BUTTONS ----------------------------------
        val videoURL = video.url
        val musicBtn = card.findViewById<MaterialButton>(R.id.download_music)
        musicBtn.tag = "$videoURL##audio"
        musicBtn.setTag(R.id.cancelDownload, "false")
        musicBtn.setOnClickListener { onItemClickListener.onButtonClick(videoURL, DownloadType.audio) }
        musicBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(videoURL, DownloadType.audio); true}
        val videoBtn = card.findViewById<MaterialButton>(R.id.download_video)
        videoBtn.tag = "$videoURL##video"
        videoBtn.setTag(R.id.cancelDownload, "false")
        videoBtn.setOnClickListener { onItemClickListener.onButtonClick(videoURL, DownloadType.video) }
        videoBtn.setOnLongClickListener{ onItemClickListener.onLongButtonClick(videoURL, DownloadType.video); true}

        // PROGRESS BAR ----------------------------------------------------
        val progressBar = card.findViewById<LinearProgressIndicator>(R.id.download_progress)
        progressBar.tag = "$videoURL##progress"
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE

        if (checkedItems.contains(videoURL)) {
            card.isChecked = true
            card.strokeWidth = 5
        } else {
            card.isChecked = false
            card.strokeWidth = 0
        }
        card.tag = "$videoURL##card"
        card.setOnLongClickListener {
            checkCard(card, videoURL)
            true
        }
        
        // Pura Card click karne par REVERTED: Wapas video dialog khulega
        card.setOnClickListener {
            if (checkedItems.size > 0) {
                checkCard(card, videoURL)
            } else {
                onItemClickListener.onCardDetailsClick(videoURL)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, videoURL: String) {
        if (card.isChecked) {
            card.strokeWidth = 0
            checkedItems.remove(videoURL)
        } else {
            card.strokeWidth = 5
            checkedItems.add(videoURL)
        }
        card.isChecked = !card.isChecked
        onItemClickListener.onCardClick(videoURL, card.isChecked)
    }

    interface OnItemClickListener {
        fun onButtonClick(videoURL: String, type: DownloadType?)
        fun onLongButtonClick(videoURL: String, type: DownloadType?)
        fun onCardClick(videoURL: String, add: Boolean)
        fun onCardDetailsClick(videoURL: String)
        fun onAuthorClick(channelUrl: String) 
    }

    fun checkAll(items: List<ResultItem?>?){
        checkedItems.clear()
        checkedItems.addAll(items!!.map { it!!.url })
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun checkMultipleItems(list: List<String>){
        checkedItems.clear()
        checkedItems.addAll(list)
        notifyDataSetChanged()
    }

    fun invertSelected(items: List<ResultItem?>?){
        val invertedList = mutableListOf<String>()
        items?.forEach {
            if (!checkedItems.contains(it!!.url)) invertedList.add(it.url)
        }
        checkedItems.clear()
        checkedItems.addAll(invertedList)
        notifyDataSetChanged()
    }

    fun clearCheckedItems(){
        checkedItems.clear()
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<ResultItem> = object : DiffUtil.ItemCallback<ResultItem>() {
            override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                val ranged = arrayListOf(oldItem.id, newItem.id)
                return ranged[0] == ranged[1]
            }

            override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem): Boolean {
                return oldItem.url == newItem.url && oldItem.title == newItem.title && oldItem.author == newItem.author
            }
        }
    }
}
