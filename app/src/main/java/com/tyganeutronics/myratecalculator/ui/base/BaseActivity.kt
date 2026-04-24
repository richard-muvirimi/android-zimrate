package com.tyganeutronics.myratecalculator.ui.base

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity

abstract class BaseActivity : AppCompatActivity() {
    val firebaseAnalytics: FirebaseAnalytics
        get() = FirebaseAnalytics.getInstance(this)

    override fun setTitle(@StringRes titleId: Int) {
        title = getString(titleId)
    }

    override fun setTitle(title: CharSequence) {
        (findViewById<View>(R.id.ctb_layout) as CollapsingToolbarLayout).title = title
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        onViewCreated()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        onViewCreated()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        onViewCreated()
    }

    open fun onViewCreated() {
        bindViews()

        // https://developer.android.com/develop/ui/views/layout/edge-to-edge#kotlin
        val container = findViewById<CoordinatorLayout>(R.id.layout_container) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                topMargin = insets.top
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    public override fun onStart() {
        super.onStart()
        syncViews()
    }

    protected open fun syncViews() {

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun bindViews() {
        if (this !is MainActivity) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    fun navigateToFragment(fragment: Fragment, tag: String) {

        val transaction = supportFragmentManager.beginTransaction()
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        transaction.replace(
            R.id.nav_host_fragment,
            fragment,
            tag
        )
        transaction.addToBackStack(tag)
        transaction.commit()
    }
}