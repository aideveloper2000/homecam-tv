package com.homeai.homecamtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.homeai.homecamtv.ui.MainBrowseFragment

/**
 * Root activity. Hosts the Leanback browse screen with the camera grid.
 *
 * Extends [FragmentActivity] (not AppCompatActivity) because the Leanback
 * fragments are designed for a Leanback-themed host.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, MainBrowseFragment())
                .commit()
        }
    }
}
