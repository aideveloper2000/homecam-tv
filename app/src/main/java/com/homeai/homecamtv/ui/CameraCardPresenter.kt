package com.homeai.homecamtv.ui

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.homeai.homecamtv.R
import com.homeai.homecamtv.data.Camera
import com.homeai.homecamtv.data.StatusStore

/**
 * Presents camera endpoints, action cards and empty-row hints as Leanback
 * [ImageCardView]s in the browse grid. Camera cards carry a status badge from
 * the last [StatusStore] probe.
 */
class CameraCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            // show the camera name / host line even when the card is not focused
            setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS)
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setInfoAreaBackgroundColor(
                ContextCompat.getColor(parent.context, R.color.card_info_bg)
            )
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = viewHolder.view as ImageCardView
        when (item) {
            is Camera -> {
                card.titleText = item.name.ifBlank { "Unnamed camera" }
                card.contentText = item.protocol.uppercase() + "  •  " + item.host.ifBlank { "—" }
                val status = StatusStore.get(item.id)
                val cardImage = when {
                    status == null -> R.drawable.card_camera_unknown
                    status.online -> R.drawable.card_camera_online
                    else -> R.drawable.card_camera_offline
                }
                card.setMainImage(ContextCompat.getDrawable(card.context, cardImage))
                card.setBadgeImage(null)
            }
            is ActionCard -> {
                card.titleText = item.title
                card.contentText = item.subtitle
                card.setMainImage(ContextCompat.getDrawable(card.context, R.drawable.card_add))
                card.setBadgeImage(null)
            }
            is HintCard -> {
                card.titleText = item.title
                card.contentText = item.subtitle
                card.setMainImage(ContextCompat.getDrawable(card.context, R.drawable.card_camera))
                card.setBadgeImage(null)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.setMainImage(null)
        card.setBadgeImage(null)
    }

    companion object {
        const val CARD_WIDTH = 312
        const val CARD_HEIGHT = 176
    }
}
