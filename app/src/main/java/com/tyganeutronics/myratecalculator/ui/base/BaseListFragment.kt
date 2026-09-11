package com.tyganeutronics.myratecalculator.ui.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.interfaces.SearchableInterface
import com.tyganeutronics.myratecalculator.utils.traits.invalidateOptionsMenu
import com.tyganeutronics.myratecalculator.utils.traits.requireViewById

abstract class BaseListFragment : BaseFragment(), SearchableInterface {

    protected abstract fun hasItems(): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    protected open fun refreshContentLayout(searching: Boolean = search()?.isNotEmpty() ?: false) {
        if (hasItems()) {
            recyclerView.isVisible = true
            layoutItemsEmpty.isGone = true
            layoutItemsSearchEmpty.isGone = true
        } else {
            recyclerView.isGone = true
            if (search() == null) {
                //no search
                showEmptyView()
            } else {
                if (search().isNullOrBlank() && searching) {
                    //before search
                    showSearchView()
                } else {
                    showEmptyView()
                }
            }
        }
    }

    override fun bindViews() {
        super.bindViews()

        recyclerView.layoutManager = object : LinearLayoutManager(requireContext()) {
            override fun onMeasure(
                recycler: RecyclerView.Recycler,
                state: RecyclerView.State,
                widthSpec: Int,
                heightSpec: Int
            ) {
                super.onMeasure(recycler, state, widthSpec, heightSpec)
                setMeasuredDimension(requireView().width, requireView().height)
            }

        }
    }

    private fun showEmptyView() {
        layoutItemsEmpty.isVisible = true
        layoutItemsSearchEmpty.isGone = true
    }

    private fun showSearchView() {
        layoutItemsEmpty.isGone = true
        layoutItemsSearchEmpty.isVisible = true
    }

    /**
     * When content is ready hide progress bar
     */
    @SuppressLint("NotifyDataSetChanged")
    protected open fun contentReady() {
        loadingProgressBar.hide()
        recyclerView.adapter!!.notifyDataSetChanged()

        if (search().isNullOrEmpty()) {
            //redo menu only when not searching
            invalidateOptionsMenu()
        }

        refreshContentLayout()
    }

    protected fun contentLoading() {
        loadingProgressBar.show()
    }

    /**
     * Shows a line above the list explaining how it is sorted.
     *
     * For orders that are deliberate but not guessable — the reward list runs soonest-to-expire
     * first rather than newest first, which looks like a mistake until you know coins are spent
     * in that order.
     *
     * The padding is applied once the caption has been measured rather than from a fixed dimen,
     * so a line that wraps on a narrow screen still clears the first row.
     */
    protected fun setCaption(@StringRes text: Int) {
        val caption = requireViewById<AppCompatTextView>(R.id.txt_list_caption)

        caption.setText(text)
        caption.isVisible = true
        caption.post { recyclerView.updatePadding(top = caption.height) }
    }

    protected val recyclerView: RecyclerView
        get() = requireViewById(R.id.recycler_view)

    protected val loadingProgressBar: ContentLoadingProgressBar
        get() = requireViewById(R.id.pb_loading)

    protected val layoutItemsEmpty: FrameLayout
        get() = requireViewById(R.id.layout_content_empty)

    protected val layoutItemsSearchEmpty: FrameLayout
        get() = requireViewById(R.id.layout_content_search)
}