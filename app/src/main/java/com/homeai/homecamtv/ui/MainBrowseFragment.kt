package com.homeai.homecamtv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import com.homeai.homecamtv.CameraFormActivity
import com.homeai.homecamtv.R
import com.homeai.homecamtv.data.Camera
import com.homeai.homecamtv.data.CameraStatusChecker
import com.homeai.homecamtv.data.CameraStore
import com.homeai.homecamtv.data.StatusStore

/**
 * The Google TV home screen: a Leanback browse grid of live camera cards,
 * grouped by camera group, plus a settings row with "Add camera".
 *
 * Each camera is probed in the background when the grid loads; the resulting
 * Online/Offline badge lands on its card.
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private val cardPresenter = CameraCardPresenter()
    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = BrowseSupportFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.accent)

        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Camera -> openDetails(item)
                is ActionCard -> openAction(item)
                is HintCard -> openAction(
                    ActionCard(ActionType.ADD_CAMERA, "", "")
                )
            }
        })

        loadRows()
    }

    override fun onResume() {
        super.onResume()
        StatusStore.clear()
        loadRows()
    }

    private fun loadRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val store = CameraStore.get(requireContext())
        val cameras = store.load()

        rowsAdapter.add(buildCameraRow(getString(R.string.all_cameras), cameras))

        store.groups().forEach { group ->
            rowsAdapter.add(
                buildCameraRow(group, cameras.filter { it.group.equals(group, ignoreCase = true) })
            )
        }

        val actions = ArrayObjectAdapter(cardPresenter)
        actions.add(
            ActionCard(
                ActionType.ADD_CAMERA,
                getString(R.string.add_camera),
                getString(R.string.add_camera_hint)
            )
        )
        rowsAdapter.add(ListRow(HeaderItem(getString(R.string.settings)), actions))

        adapter = rowsAdapter

        probeStatuses(cameras)
    }

    /** Probes every camera in the background and updates its card badge. */
    private fun probeStatuses(cameras: List<Camera>) {
        cameras.forEach { camera ->
            Thread {
                val status = CameraStatusChecker.check(camera)
                StatusStore.put(camera.id, status)
                view?.post { updateCard(camera.id) }
            }.apply { isDaemon = true }.start()
        }
    }

    private fun updateCard(cameraId: String) {
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            val adapter = row.adapter as? ArrayObjectAdapter ?: continue
            for (position in 0 until adapter.size()) {
                if ((adapter.get(position) as? Camera)?.id == cameraId) {
                    adapter.notifyArrayItemRangeChanged(position, 1)
                    return
                }
            }
        }
    }

    private fun buildCameraRow(title: String, cameras: List<Camera>): ListRow {
        val adapter = ArrayObjectAdapter(cardPresenter)
        if (cameras.isEmpty()) {
            adapter.add(
                HintCard(
                    getString(R.string.no_cameras),
                    getString(R.string.no_cameras_hint)
                )
            )
        } else {
            cameras.forEach { adapter.add(it) }
        }
        return ListRow(HeaderItem(title), adapter)
    }

    private fun openDetails(camera: Camera) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, CameraDetailsFragment.newInstance(camera.id))
            .addToBackStack("details")
            .commit()
    }

    private fun openAction(card: ActionCard) {
        when (card.type) {
            ActionType.ADD_CAMERA ->
                startActivity(Intent(requireContext(), CameraFormActivity::class.java))
        }
    }
}
