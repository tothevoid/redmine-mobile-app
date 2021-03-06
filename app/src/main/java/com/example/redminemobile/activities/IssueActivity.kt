package com.example.redminemobile.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SwitchCompat
import android.view.View
import android.widget.AbsListView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.beust.klaxon.Klaxon
import com.example.redminemobile.adapters.IssuesListViewAdapter
import com.example.redminemobile.models.Issue
import com.example.redminemobile.services.ApiService
import com.example.redminemobile.R
import com.example.redminemobile.services.StorageService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.io.StringReader

class IssueActivity : AppCompatActivity() {

    private var issuesPlaceholder: TextView? = null
    private var listView: ListView? = null
    private var loadMoreButton: Button? = null
    private var projectId: Int = -1
    private var offset: Int = 0
    private var quantity: Int = 6
    private var isAdapterCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_issue)
        projectId = intent.getIntExtra("project_id",-1)
        issuesPlaceholder = findViewById(R.id.issuesPlaceholder) as TextView?
        loadMoreButton = findViewById(R.id.loadMoreButton) as Button?
        listView = findViewById(R.id.listView) as ListView?
        getIssues()
        fixScrollListView()
        configureRefresh()
        configureSwitch()
    }

    private fun configureRefresh(){
        val mSwipeRefreshLayout = findViewById(R.id.swipeRefreshIssue) as SwipeRefreshLayout
        mSwipeRefreshLayout.setOnRefreshListener{
            reset()
            getIssues()
            mSwipeRefreshLayout.isRefreshing = false
        }
    }

    private fun configureSwitch(){
        val mSwitchCompat = findViewById(R.id.switchCompatIssues) as SwitchCompat
        mSwitchCompat.setOnCheckedChangeListener { switchCompatIssues, isChecked ->
            reset()
            if (isChecked) {
                getIssues(true)
            } else {
                getIssues()
            }
        }
    }

    private fun fixScrollListView(){
        val mSwipeRefreshLayout = findViewById(R.id.swipeRefreshIssue) as SwipeRefreshLayout
        listView?.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                val topRowVerticalPosition = if (listView == null || listView?.childCount == 0)
                    0

                else
                    listView?.getChildAt(0)?.top
                if (topRowVerticalPosition != null) {
                    mSwipeRefreshLayout.setEnabled(firstVisibleItem == 0 && topRowVerticalPosition >= 0)
                }
            }
        })
    }

    private fun reset(){
        isAdapterCreated = false
        offset = 0
        loadMoreButton?.visibility = View.VISIBLE
    }

    private fun getIssues(isChecked: Boolean = false)
    {
        val prefs = getSharedPreferences("Server", Context.MODE_PRIVATE)
        val id = StorageService().fetchByKey(prefs,"user_id")
        var additionalParams = "project_id=$projectId"
        if(isChecked){
            additionalParams += "&assigned_to_id=$id"
        }
        ApiService(prefs).requestGet("issues.json", offset, quantity, additionalParams = additionalParams, callback =  object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string()
                val klaxon = Klaxon()
                val parsed = klaxon.parseJsonObject(StringReader(body))
                val dataArray = parsed.array<Any>("issues")
                var output = dataArray?.let { klaxon.parseFromJsonArray<Issue>(it) }
                if (output != null && output.count() != 0){
                    updateView(ArrayList(output))
                }
                else if (offset == 0){
                    runOnUiThread {
                        issuesPlaceholder?.text = getString(R.string.issuesNotifyEmpty)
                    }
                }
            }
        })
    }

    private fun createAdapter(items: ArrayList<Issue>)
    {
        runOnUiThread {
            val adapter = IssuesListViewAdapter(this, items)
            listView?.adapter = adapter
            listView?.setOnItemClickListener { parent, view, position, id ->
                val id = items[position].id
                var intent = Intent(this, TimeEntryActivity::class.java)
                intent.putExtra("issue_id", id)
                startActivity(intent)
            }

            adapter.notifyDataSetChanged()
            issuesPlaceholder?.visibility = View.GONE
            isAdapterCreated = true
        }
    }

    private fun updateView(items: ArrayList<Issue>)
    {
        runOnUiThread{
            var localItems: ArrayList<Issue> = items
            if (items.count() == quantity){
                offset += quantity -1
                localItems = ArrayList(items.dropLast(1))
            }
            else
            {
                loadMoreButton?.visibility = View.GONE
            }

            if (isAdapterCreated){
                addItemsToAdapter(localItems)
            }
            else{
                createAdapter(localItems)
            }
        }
    }
    private fun addItemsToAdapter(projects: ArrayList<Issue>){
        val adapter = listView?.adapter as IssuesListViewAdapter
        adapter.addItems(projects)
    }

    fun loadMore(View: View?){
        getIssues()
    }
}
