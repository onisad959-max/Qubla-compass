package com.example

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LocationSource {
    DEFAULT,
    GPS,
    MANUAL_CITY,
    MANUAL_COORDINATES
}

data class City(val name: String, val latitude: Double, val longitude: Double)

data class QiblaUiState(
    val userLatitude: Double = 23.8103,              // Default Dhaka, Bangladesh
    val userLongitude: Double = 90.4125,
    val locationName: String = "ঢাকা, বাংলাদেশ (ডিফল্ট)",
    val locationSource: LocationSource = LocationSource.DEFAULT,
    val deviceAzimuth: Float = 0f,                 // Raw or smoothed degree heading from sensor (magnetic/true)
    val magneticDeclination: Float = 0f,            // Geomagnetic declination offset for true north
    val qiblaTrueBearing: Double = 261.6,          // Calculated angle of Mecca relative to True North for Dhaka
    val distanceKm: Double = 5334.0,               // Calculated distance to Mecca from Dhaka
    val isAligned: Boolean = false,                // Device top is pointing towards Mecca (+- 3 degrees)
    val hasCompassSensor: Boolean = true,
    val isSimulationMode: Boolean = false,          // Interactive slider simulation for emulators
    val simulatedAzimuth: Float = 0f,              // Simulates current sensor azimuth
    val isLocationPermissionGranted: Boolean = false,
    val showCityDialog: Boolean = false,
    val showCustomCoordsDialog: Boolean = false,
    val gpsAltitude: Double = 0.0,
    val gpsAccuracy: Float = 0f,
    val lastGpsUpdateTimestamp: Long = 0L
)

class QiblaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(QiblaUiState())
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    // Precompiled list of major world cities with Bengali names
    val predefinedCities = listOf(
        City("মক্কা, সৌদি আরব (আল-মসজিদ আল-হারাম)", 21.4225, 39.8262),
        City("ঢাকা, বাংলাদেশ", 23.8103, 90.4125),
        City("চট্টগ্রাম, বাংলাদেশ", 22.3569, 91.7832),
        City("সিলেট, বাংলাদেশ", 24.8949, 91.8687),
        City("কলকাতা, ভারত", 22.5726, 88.3639),
        City("জাকার্তা, ইন্দোনেশিয়া", -6.2088, 106.8456),
        City("কুয়ালালামপুর, মালয়েশিয়া", 3.1390, 101.6869),
        City("কায়রো, মিশর", 30.0444, 31.2357),
        City("লন্ডন, যুক্তরাজ্য", 51.5074, -0.1278),
        City("নিউ ইয়র্ক, মার্কিন যুক্তরাষ্ট্র", 40.7128, -74.0060),
        City("ইস্তাম্বুল, তুরস্ক", 41.0082, 28.9784),
        City("দুবাই, সংযুক্ত আরব আমিরাত", 25.2048, 55.2708),
        City("করাচি, পাকিস্তান", 24.8607, 67.0011),
        City("মুম্বাই, ভারত", 19.0760, 72.8777)
    )

    init {
        // Run first calculation based on default state
        recomputeQiblaDetails()
    }

    /**
     * Updates coordinates dynamically from live GPS sensor
     */
    fun updateLocationFromGps(lat: Double, lon: Double, alt: Double, accuracy: Float, name: String = "বর্তমান অবস্থান (জিপিএস)") {
        _uiState.update { state ->
            state.copy(
                userLatitude = lat,
                userLongitude = lon,
                locationName = name,
                locationSource = LocationSource.GPS,
                gpsAltitude = alt,
                gpsAccuracy = accuracy,
                lastGpsUpdateTimestamp = System.currentTimeMillis()
            )
        }
        recomputeQiblaDetails()
    }

    /**
     * Handles live updates of the device's sensor compass azimuth.
     * Incorporates circular-wrapping interpolation (shortest path) to avoid jittery full-swing wrap-arounds.
     */
    fun updateAzimuth(newAzimuth: Float, declination: Float = 0f) {
        _uiState.update { state ->
            val finalNewAzimuth = (newAzimuth + 360f) % 360f
            val currentSmooth = state.deviceAzimuth
            val smoothed = if (state.isSimulationMode) {
                // In simulation, directly follow simulated value
                state.simulatedAzimuth
            } else {
                interpolateAngle(currentSmooth, finalNewAzimuth, 0.25f)
            }

            // Calculate true bearing the user is pointing at:
            // Top of phone is facing: deviceAzimuth relative to True North.
            // Qibla is at: qiblaTrueBearing from True North.
            // Difference is how many degrees the user needs to turn.
            val diffAngle = Math.abs(shortestAngleDifference(smoothed.toDouble(), state.qiblaTrueBearing))
            val aligned = diffAngle <= 3.0 // Aligned when pointing within 3 degrees of Kaaba
            
            state.copy(
                deviceAzimuth = smoothed,
                magneticDeclination = declination,
                isAligned = aligned
            )
        }
    }

    /**
     * Set dynamic simulation mode for emulators or testing.
     */
    fun setSimulationMode(enabled: Boolean) {
        _uiState.update { it.copy(isSimulationMode = enabled) }
        if (enabled) {
            // Seed simulated angle with current real azimuth
            _uiState.update { it.copy(simulatedAzimuth = it.deviceAzimuth) }
        }
    }

    /**
     * Update the simulated angle from slider or compass touch dragging.
     */
    fun updateSimulatedAzimuth(angle: Float) {
        val normalized = (angle + 360f) % 360f
        _uiState.update { state ->
            val diffAngle = Math.abs(shortestAngleDifference(normalized.toDouble(), state.qiblaTrueBearing))
            state.copy(
                simulatedAzimuth = normalized,
                deviceAzimuth = normalized,
                isAligned = diffAngle <= 3.0
            )
        }
    }

    /**
     * Explicit location selection for manual city overrides
     */
    fun selectPredefinedCity(city: City) {
        _uiState.update { state ->
            state.copy(
                userLatitude = city.latitude,
                userLongitude = city.longitude,
                locationName = city.name,
                locationSource = LocationSource.MANUAL_CITY,
                showCityDialog = false
            )
        }
        recomputeQiblaDetails()
    }

    /**
     * Apply custom latitude and longitude coordinates.
     */
    fun applyCustomCoordinates(lat: Double, lon: Double) {
        _uiState.update { state ->
            state.copy(
                userLatitude = lat,
                userLongitude = lon,
                locationName = "কাস্টম স্থানাঙ্ক (${String.format("%.4f", lat)}°, ${String.format("%.4f", lon)}°)",
                locationSource = LocationSource.MANUAL_COORDINATES,
                showCustomCoordsDialog = false
            )
        }
        recomputeQiblaDetails()
    }

    fun setHasCompassSensor(hasSensor: Boolean) {
        _uiState.update { it.copy(hasCompassSensor = hasSensor) }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isLocationPermissionGranted = granted) }
    }

    fun setShowCityDialog(show: Boolean) {
        _uiState.update { it.copy(showCityDialog = show) }
    }

    fun setShowCustomCoordsDialog(show: Boolean) {
        _uiState.update { it.copy(showCustomCoordsDialog = show) }
    }

    /**
     * Recompute Qibla angle and distance to Mecca whenever coordinates change
     */
    private fun recomputeQiblaDetails() {
        val state = _uiState.value
        val latU = state.userLatitude
        val lonU = state.userLongitude

        val qiblaAngle = calculateQiblaDirection(latU, lonU)
        val distance = calculateDistanceToMecca(latU, lonU)

        _uiState.update {
            val diffAngle = Math.abs(shortestAngleDifference(state.deviceAzimuth.toDouble(), qiblaAngle))
            it.copy(
                qiblaTrueBearing = qiblaAngle,
                distanceKm = distance,
                isAligned = diffAngle <= 3.0
            )
        }
    }

    /**
     * Calculates the Qibla bearing (direction relative to True North)
     * using spherical great-circle formulation.
     */
    private fun calculateQiblaDirection(userLat: Double, userLon: Double): Double {
        val latMecca = Math.toRadians(21.422487)
        val lonMecca = Math.toRadians(39.826206)
        val latUser = Math.toRadians(userLat)
        val lonUser = Math.toRadians(userLon)

        val deltaLon = lonMecca - lonUser

        val y = Math.sin(deltaLon)
        val x = Math.cos(latUser) * Math.tan(latMecca) - Math.sin(latUser) * Math.cos(deltaLon)

        val qiblaAngleRad = Math.atan2(y, x)
        var qiblaAngleDeg = Math.toDegrees(qiblaAngleRad)
        qiblaAngleDeg = (qiblaAngleDeg + 360.0) % 360.0
        return qiblaAngleDeg
    }

    /**
     * Calculates Spherical distance in Km via Haversine formulation
     */
    private fun calculateDistanceToMecca(userLat: Double, userLon: Double): Double {
        val earthRadiusKm = 6371.0
        val latMecca = Math.toRadians(21.422487)
        val lonMecca = Math.toRadians(39.826206)
        val latUser = Math.toRadians(userLat)
        val lonUser = Math.toRadians(userLon)

        val deltaLat = latMecca - latUser
        val deltaLon = lonMecca - lonUser

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(latUser) * Math.cos(latMecca) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Safely interpolates two angles around a circular wrapping boundary
     */
    private fun interpolateAngle(current: Float, target: Float, alpha: Float): Float {
        var diff = (target - current) % 360f
        if (diff < -180f) {
            diff += 360f
        } else if (diff > 180f) {
            diff -= 360f
        }

        val smoothed = current + alpha * diff
        return (smoothed + 360f) % 360f
    }

    /**
     * Computes raw circular distance range [-180..180] between two headings
     */
    private fun shortestAngleDifference(angle1: Double, angle2: Double): Double {
        var diff = (angle2 - angle1) % 360.0
        if (diff < -180.0) {
            diff += 360.0
        } else if (diff > 180.0) {
            diff -= 360.0
        }
        return diff
    }
}
