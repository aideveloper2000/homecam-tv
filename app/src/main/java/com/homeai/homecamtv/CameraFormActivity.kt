package com.homeai.homecamtv

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.homeai.homecamtv.data.Camera
import com.homeai.homecamtv.data.CameraStore
import com.homeai.homecamtv.databinding.ActivityCameraFormBinding

/**
 * Add or edit a camera. Opened from the browse screen ("Add camera") or from a
 * camera's details screen ("Edit").
 */
class CameraFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraFormBinding
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingId = intent.getStringExtra(EXTRA_CAMERA_ID)
        val existing = editingId?.let { CameraStore.get(this).get(it) }

        title = getString(if (existing == null) R.string.add_camera else R.string.edit_camera)
        binding.formTitle.text = title

        val protocolAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            Camera.PROTOCOLS.map { it.uppercase() }
        )
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.protocolSpinner.adapter = protocolAdapter

        existing?.let { bind(it) } ?: run {
            binding.protocolSpinner.setSelection(0)
            binding.portInput.setText("554")
            binding.channelInput.setText("1")
        }

        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun bind(camera: Camera) {
        binding.nameInput.setText(camera.name)
        binding.serialInput.setText(camera.serialNumber ?: "")
        binding.hostInput.setText(camera.host)
        binding.portInput.setText(camera.port.toString())
        binding.pathInput.setText(camera.path)
        binding.usernameInput.setText(camera.username)
        binding.passwordInput.setText(camera.password)
        binding.groupInput.setText(camera.group)
        binding.channelInput.setText(camera.channel.toString())
        val index = Camera.PROTOCOLS.indexOfFirst {
            it.equals(camera.protocol, ignoreCase = true)
        }
        binding.protocolSpinner.setSelection(if (index >= 0) index else 0)
    }

    private fun save() {
        val name = binding.nameInput.text.toString().trim()
        val host = binding.hostInput.text.toString().trim()

        if (name.isEmpty() || host.isEmpty()) {
            Toast.makeText(this, R.string.name_and_host_required, Toast.LENGTH_SHORT).show()
            return
        }

        val protocol = Camera.PROTOCOLS[binding.protocolSpinner.selectedItemPosition]
        val port = binding.portInput.text.toString().trim().toIntOrNull() ?: defaultPort(protocol)

        val camera = Camera(
            id = editingId ?: java.util.UUID.randomUUID().toString(),
            name = name,
            serialNumber = binding.serialInput.text.toString().trim().ifBlank { null },
            protocol = protocol,
            host = host,
            port = port,
            path = binding.pathInput.text.toString().trim(),
            username = binding.usernameInput.text.toString().trim(),
            password = binding.passwordInput.text.toString(),
            group = binding.groupInput.text.toString().trim().ifBlank { Camera.GROUP_DEFAULT },
            channel = binding.channelInput.text.toString().trim().toIntOrNull() ?: 1
        )

        CameraStore.get(this).upsert(camera)
        finish()
    }

    private fun defaultPort(protocol: String): Int =
        if (protocol.equals(Camera.PROTOCOL_MJPEG, ignoreCase = true)) 80 else 554

    companion object {
        const val EXTRA_CAMERA_ID = "camera_id"
    }
}
