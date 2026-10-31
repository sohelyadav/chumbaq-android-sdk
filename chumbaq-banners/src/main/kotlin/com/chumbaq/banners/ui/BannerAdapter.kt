package com.chumbaq.banners.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.chumbaq.banners.R
import com.chumbaq.banners.model.Banner

internal class BannerAdapter(
    private var banners: List<Banner> = emptyList(),
    private val onBannerClick: ((Banner) -> Unit)? = null,
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    fun updateBanners(newBanners: List<Banner>) {
        banners = newBanners
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chumbaq_banner_item, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(banners[position])
    }

    override fun getItemCount(): Int = banners.size

    inner class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.chumbaq_banner_image)
        private val titleView: TextView = itemView.findViewById(R.id.chumbaq_banner_title)
        private val ctaView: TextView = itemView.findViewById(R.id.chumbaq_banner_cta)

        fun bind(banner: Banner) {
            titleView.text = banner.title
            titleView.visibility = if (banner.title.isNotBlank()) View.VISIBLE else View.GONE

            ctaView.text = banner.ctaText ?: ""
            ctaView.visibility = if (!banner.ctaText.isNullOrBlank()) View.VISIBLE else View.GONE

            if (!banner.imageUrl.isNullOrBlank()) {
                imageView.visibility = View.VISIBLE
                imageView.load(banner.imageUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(12f))
                }
            } else {
                imageView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onBannerClick?.invoke(banner)
            }
        }
    }
}
