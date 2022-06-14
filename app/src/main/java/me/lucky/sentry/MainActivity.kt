package me.lucky.sentry

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

import me.lucky.sentry.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesProxy
    private lateinit var admin: DeviceAdminManager

    private val registerForDeviceAdmin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != RESULT_OK) binding.toggle.isChecked = false else setOn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
        setup()
    }

    override fun onStart() {
        super.onStart()
        update()
    }

    private fun init() {
        prefs = PreferencesProxy(this)
        prefs.clone()
        admin = DeviceAdminManager(this)
        if (prefs.isEnabled && prefs.maxFailedPasswordAttempts > 0)
            try { admin.setMaximumFailedPasswordsForWipe(0) } catch (exc: SecurityException) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN))
                hideSecureLockScreenRequired()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !admin.canUsbDataSignalingBeDisabled())
            hideUsbDataSignaling()
        binding.apply {
            maxFailedPasswordAttempts.value = prefs.maxFailedPasswordAttempts.toFloat()
            usbDataSignaling.isChecked = isUsbDataSignalingEnabled()
            toggle.isChecked = prefs.isEnabled
        }
    }

    private fun setup() {
        binding.apply {
            maxFailedPasswordAttempts.addOnChangeListener { _, value, _ ->
                prefs.maxFailedPasswordAttempts = value.toInt()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                usbDataSignaling.setOnCheckedChangeListener { _, isChecked ->
                    try {
                        admin.setUsbDataSignalingEnabled(isChecked)
                    } catch (exc: Exception) {
                        Snackbar.make(
                            usbDataSignaling,
                            R.string.usb_data_signaling_change_failed_popup,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                        usbDataSignaling.isChecked = !isChecked
                    }
                }
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) requestAdmin() else setOff()
            }
        }
    }

    private fun hideSecureLockScreenRequired() {
        binding.apply {
            maxFailedPasswordAttempts.visibility = View.GONE
            maxFailedPasswordAttemptsDescription.visibility = View.GONE
            space.visibility = View.GONE
        }
    }

    private fun hideUsbDataSignaling() {
        binding.apply {
            usbDataSignaling.visibility = View.GONE
            usbDataSignalingDescription.visibility = View.GONE
        }
    }

    private fun setOn() {
        prefs.isEnabled = true
    }

    private fun setOff() {
        prefs.isEnabled = false
        try { admin.remove() } catch (exc: SecurityException) {}
    }

    private fun update() {
        binding.usbDataSignaling.isChecked = isUsbDataSignalingEnabled()
        if (prefs.isEnabled && !admin.isActive())
            Snackbar.make(
                binding.toggle,
                R.string.service_unavailable_popup,
                Snackbar.LENGTH_SHORT,
            ).show()
    }

    private fun isUsbDataSignalingEnabled() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        admin.isUsbDataSignalingEnabled() else true

    private fun requestAdmin() = registerForDeviceAdmin.launch(admin.makeRequestIntent())
}
