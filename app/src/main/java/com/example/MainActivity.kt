package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: QiblaViewModel by viewModels()

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    // Arrays to hold sensor data
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init sensor services
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val hasCompass = rotationVectorSensor != null || (accelerometerSensor != null && magnetometerSensor != null)
        viewModel.setHasCompassSensor(hasCompass)
        if (!hasCompass) {
            // Automatically enable manual interact simulation if physical magnetic hardware is missing
            viewModel.setSimulationMode(true)
        }

        setContent {
            MyApplicationTheme {
                QiblaCompassScreen(
                    viewModel = viewModel,
                    onRequestGpsUpdate = { requestFreshLocation() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        // Query location immediately on load to start with coordinates
        requestFreshLocation()
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
    }

    private fun registerSensors() {
        try {
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                // Register accelerometers + magnetometers as a dual fallback loop
                if (accelerometerSensor != null) {
                    sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
                }
                if (magnetometerSensor != null) {
                    sensorManager.registerListener(this, magnetometerSensor, SensorManager.SENSOR_DELAY_UI)
                }
            }
        } catch (e: Exception) {
            // Guard registration errors safely
        }
    }

    private fun unregisterSensors() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            // Guard unregistration errors safely
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val state = viewModel.uiState.value
        // If simulation mode is active, prevent sensors from writing to state
        if (state.isSimulationMode) return

        var azimuthDeg = 0f
        var sensorComputed = false

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            sensorComputed = true
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityValues, 0, 3)
            hasGravity = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticValues, 0, 3)
            hasMagnetic = true
        }

        if (!sensorComputed && hasGravity && hasMagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                sensorComputed = true
            }
        }

        if (sensorComputed) {
            azimuthDeg = (azimuthDeg + 360f) % 360f

            // Compute magnetic declination compensation to get True North alignment
            val declination = try {
                val geoField = GeomagneticField(
                    state.userLatitude.toFloat(),
                    state.userLongitude.toFloat(),
                    0f,
                    System.currentTimeMillis()
                )
                geoField.declination
            } catch (e: Exception) {
                0f
            }

            // Azimuth returned is relative to magnetic north, we adjust by declination to map to geographical True North
            val trueAzimuth = (azimuthDeg + declination + 360f) % 360f
            viewModel.updateAzimuth(trueAzimuth, declination)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Tries to fetch coordinates via system location engines, updating the VM.
     */
    private fun requestFreshLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        viewModel.setLocationPermissionGranted(hasFine || hasCoarse)
        if (hasFine || hasCoarse) {
            try {
                // Gather last known position amongst any enabled providers immediately for optimal UI latency
                val providers = locationManager.getProviders(true)
                var bestLocation: Location? = null
                for (provider in providers) {
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }

                bestLocation?.let {
                    viewModel.updateLocationFromGps(it.latitude, it.longitude, it.altitude, it.accuracy)
                }

                // Launch one-shot refresh query from the ideal physical engine to resolve accurate coordinates
                val activeProvider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    LocationManager.NETWORK_PROVIDER
                } else {
                    null
                }

                if (activeProvider != null) {
                    locationManager.requestSingleUpdate(
                        activeProvider,
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                viewModel.updateLocationFromGps(
                                    location.latitude,
                                    location.longitude,
                                    location.altitude,
                                    location.accuracy
                                )
                            }
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        },
                        mainLooper
                    )
                }
            } catch (e: SecurityException) {
                // Guard permission revoke conditions safely
            } catch (e: Exception) {
                // Safeguard against system service failures
            }
        }
    }
}
