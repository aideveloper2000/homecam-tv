package com.homeai.homecamtv.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.homeai.homecamtv.CameraFormActivity
import com.homeai.homecamtv.PlaybackActivity
import com.homeai.homecamtv.R
import com.homeai.homecamtv.data.Camera
import com.homeai.homecamtv.data.CameraStatusChecker
import com.homeai.homecamtv.data.CameraStore
import com.homeai.homecamtv.data.StatusStore
import com.homeai.homecamtv.data.WebLookup

/**
 * Per-camera screen: shows the identifiers (IP/domain, serial number), the
 * endpoint and the actions — Play, Check status (live probe), Lookup online
 * (crawl the web for the serial number), Edit and Delete.
 */
class CameraDetailsFragment : Fragment() {

    private lateinit var camera: Camera
    private lateinit var statusView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_camera_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val id = requireArguments().getString(ARG_ID)
        val found = id?.let { CameraStore.get(requireContext()).get(it) }
        if (found == null) {
            parentFragmentManager.popBackStack()
            return
        }
        camera = found
        statusView = view.findViewById(R.id.detail_status)

        view.findViewById<TextView>(R.id.detail_name).text =
            camera.name.ifBlank { getString(R.string.unnamed_camera) }
        view.findViewById<TextView>(R.id.detail_endpoint).text = camera.endpoint()
        view.findViewById<TextView>(R.id.detail_serial).text =
            getString(R.string.serial_number) + ": " + (camera.serialNumber ?: "—")
        view.findViewById<TextView>(R.id.detail_meta).text = listOf(
            getString(R.string.protocol) + ": " + camera.protocol.uppercase(),
            getString(R.string.group) + ": " + camera.group,
            getString(R.string.channel) + ": " + camera.channel
        ).joinToString("      ")

        showStatus(StatusStore.get(camera.id))

        view.findViewById<Button>(R.id.action_play).setOnClickListener {
            startActivity(
                Intent(requireContext(), PlaybackActivity::class.java)
                    .putExtra(PlaybackActivity.EXTRA_CAMERA_ID, camera.id)
            )
        }
        view.findViewById<Button>(R.id.action_check).setOnClickListener { checkStatus() }
        view.findViewById<Button>(R.id.action_lookup).setOnClickListener { lookup() }
        view.findViewById<Button>(R.id.action_edit).setOnClickListener {
            startActivity(
                Intent(requireContext(), CameraFormActivity::class.java)
                    .putExtra(CameraFormActivity.EXTRA_CAMERA_ID, camera.id)
            )
        }
        view.findViewById<Button>(R.id.action_delete).setOnClickListener {
            CameraStore.get(requireContext()).delete(camera.id)
            parentFragmentManager.popBackStack()
        }
    }

    private fun checkStatus() {
        showStatus(null)
        Thread {
            val status = CameraStatusChecker.check(camera)
            StatusStore.put(camera.id, status)
            view?.post { showStatus(status) }
        }.apply { isDaemon = true }.start()
    }

    private fun showStatus(status: CameraStatusChecker.Status?) {
        when {
            status == null -> {
                statusView.text = getString(R.string.checking)
                statusView.visibility = View.VISIBLE
            }
            status.online -> {
                statusView.text = getString(R.string.status_online, status.latencyMs, status.detail)
                statusView.visibility = View.VISIBLE
            }
            else -> {
                statusView.text = getString(R.string.status_offline, status.latencyMs, status.detail)
                statusView.visibility = View.VISIBLE
            }
        }
    }

    /** Crawls the web for the camera's serial number / name and shows what it found. */
    private fun lookup() {
        val query = camera.lookupQuery()
        if (query.isBlank()) {
            Toast.makeText(requireContext(), R.string.lookup_empty, Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val results = WebLookup.search(query)
            view?.post { showResults(query, results) }
        }.apply { isDaemon = true }.start()
    }

    private fun showResults(query: String, results: List<WebLookup.Result>) {
        if (!isAdded) return
        if (results.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.lookup_title)
                .setMessage(R.string.lookup_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val items = results.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.lookup_title_with_query, query))
            .setItems(items) { _, which -> openUrl(results[which].url) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ARG_ID = "camera_id"

        fun newInstance(id: String): CameraDetailsFragment = CameraDetailsFragment().apply {
            arguments = Bundle().apply { putString(ARG_ID, id) }
        }
    }
}
