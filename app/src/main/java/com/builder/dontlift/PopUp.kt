/*
 * *
 *  * Created by Shabaz Ahmed at AI Foundry on 11/9/19 4:27 PM
 *  * Copyright (c) 2019 . All rights reserved.
 *  * Last modified 11/9/19 4:27 PM
 *
 */

package com.builder.dontlift


import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import kotlinx.android.synthetic.main.pop_up.*

class PopUp(context: Context) : Dialog(context) {
    companion object {
        @JvmStatic
        fun createPopup(context: Context)
                : PopUp {
            val popup = PopUp(context)
            popup.setContentView(R.layout.pop_up)
            popup.popup_title_onion.text = "Ending Ride"
            popup.popup_reason_onion.text = "Please wait while we verify the helmets"
            popup.popup_icon_onion.setImageResource(R.drawable.scooter)
            popup.popup_primary_button_onion.visibility = View.GONE
            popup.popup_primary_button_onion.visibility = View.VISIBLE
            popup.popup_primary_button_onion.setOnClickListener { popup.dismiss() }
            popup.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popup.setCancelable(false)
            return popup
        }

    }
    fun setDone() {
        this.popup_title_onion.text = "Ride Complete"
        this.popup_reason_onion.text = "Thanks for returning the helmets"
        this.popup_progress.visibility = View.GONE
        this.popup_primary_button_onion.isEnabled = true
        this.popup_primary_button_onion.text = "Done"
    }
    fun setFailed() {
        this.popup_title_onion.text = "Ride InComplete"
        this.popup_reason_onion.text = "Helmet not detected inside the vehicle, Please return the helmet"
        this.popup_progress.visibility = View.GONE
        this.popup_primary_button_onion.isEnabled = true
        this.popup_primary_button_onion.text = "Try Again"
    }


}
