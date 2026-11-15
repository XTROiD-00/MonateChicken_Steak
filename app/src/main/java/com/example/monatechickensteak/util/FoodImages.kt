package com.example.monatechickensteak.util

import android.graphics.drawable.VectorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.monatechickensteak.model.FoodItem

/**
 * Shows a photo for a menu item when one has been added, otherwise the emoji.
 * Every item ships with a drawn illustration (res/drawable/food_<id>.xml). To use a real photo instead,
 * delete that .xml and copy the photo into app/src/main/res/drawable-nodpi/ as food_<id>.jpg.
 * No code or database change is needed.
 * See docs/PICTURES.md for the list of item ids.
 */
object FoodImages {

    fun photoResource(view: View, item: FoodItem): Int {
        val context = view.context
        return context.resources.getIdentifier("food_${item.id}", "drawable", context.packageName)
    }

    fun bind(photo: ImageView, emoji: TextView, item: FoodItem) {
        emoji.text = item.emoji
        val res = photoResource(photo, item)
        if (res != 0) {
            photo.setImageResource(res)
            // Drawn illustrations are shown whole; real photos fill the frame
            photo.scaleType = if (photo.drawable is VectorDrawable) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            photo.visibility = View.VISIBLE
            emoji.visibility = View.INVISIBLE
        } else {
            photo.setImageDrawable(null)
            photo.visibility = View.GONE
            emoji.visibility = View.VISIBLE
        }
    }

    /** Specials on the Home screen: promo_<id>.jpg in drawable-nodpi, otherwise the emoji. */
    fun bindPromo(photo: ImageView, emoji: TextView, promoId: Int, fallbackEmoji: String) {
        val context = photo.context
        val res = context.resources.getIdentifier("promo_$promoId", "drawable", context.packageName)
        if (res != 0) {
            photo.setImageResource(res)
            photo.visibility = View.VISIBLE
            emoji.visibility = View.GONE
        } else {
            photo.visibility = View.GONE
            emoji.text = fallbackEmoji
            emoji.visibility = View.VISIBLE
        }
    }
}
