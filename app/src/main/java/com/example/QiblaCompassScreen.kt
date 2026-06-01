package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QiblaCompassScreen(
    viewModel: QiblaViewModel,
    onRequestGpsUpdate: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current

    // Set up Accompanist multi-permissions for live location
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Sync permission status back to the ViewModel
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        viewModel.setLocationPermissionGranted(locationPermissionsState.allPermissionsGranted)
        if (locationPermissionsState.allPermissionsGranted) {
            onRequestGpsUpdate()
        }
    }

    // Trigger visual/physical feedback when alignment checks out
    LaunchedEffect(uiState.isAligned) {
        if (uiState.isAligned) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Smooth-easing the degrees azimuth
    val animatedAzimuth by animateFloatAsState(
        targetValue = uiState.deviceAzimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "azimuth"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "কম্পাস আইকন",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "আল-কিবলা কম্পাস",
                            style = TextStyle(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setShowCityDialog(true) },
                        modifier = Modifier.testTag("city_dropdown_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "পূর্বনির্ধারিত শহর নির্বাচন করুন",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setShowCustomCoordsDialog(true) },
                        modifier = Modifier.testTag("custom_coords_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "কাস্টম স্থানাঙ্ক লিখুন",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Location HUD Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("location_hud_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "বর্তমান স্টেশন",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = uiState.locationName,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (locationPermissionsState.allPermissionsGranted) {
                                        onRequestGpsUpdate()
                                    } else {
                                        locationPermissionsState.launchMultiplePermissionRequest()
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .testTag("gps_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "অবস্থান আপডেট করুন",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "অক্ষাংশ (Latitude)",
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = String.format("%.4f°", uiState.userLatitude),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column {
                                Text(
                                    text = "দ্রাঘিমাংশ (Longitude)",
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = String.format("%.4f°", uiState.userLongitude),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column {
                                Text(
                                    text = "উৎস (Source)",
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = when (uiState.locationSource) {
                                                LocationSource.GPS -> SacredGreen.copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when(uiState.locationSource) {
                                            LocationSource.DEFAULT -> "ডিফল্ট"
                                            LocationSource.GPS -> "জিপিএস"
                                            LocationSource.MANUAL_CITY -> "শহর"
                                            LocationSource.MANUAL_COORDINATES -> "ম্যানুয়াল"
                                        },
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = when (uiState.locationSource) {
                                            LocationSource.GPS -> SacredGreen
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fallback Banner for Emulator or missing hardware sensor
            if (!uiState.hasCompassSensor || uiState.isSimulationMode) {
                item {
                    val message = if (!uiState.hasCompassSensor) {
                        "কম্পাস সেন্সর হার্ডওয়্যার অনুপস্থিত। ইন্টারেক্টিভ সিমুলেটর সক্রিয় আছে! ডিভাইসটি ম্যানুয়ালি ঘোরাতে কম্পাস ডায়ালটি টেনে মাউস বা বুড়ো আঙুল দিয়ে ঘোরান।"
                    } else {
                        "ইন্টারেক্টিভ কম্পাস সিমুলেটর মোড চালু আছে। ফোন ঘোরানো অনুকরণ করতে কম্পাস ডায়ালটি টেনে আনুন।"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "সিমুলেটর নোটিশ",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "কম্পাস সিমুলেটর সক্রিয়",
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message,
                                    style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

            // Radial Glow Compass Housing
            item {
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = if (uiState.isAligned) {
                                        listOf(SacredGreen.copy(alpha = 0.22f), Color.Transparent)
                                    } else {
                                        listOf(PaleGold.copy(alpha = 0.08f), Color.Transparent)
                                    },
                                    center = center,
                                    radius = size.minDimension * 0.7f
                                )
                            )
                        }
                        .testTag("compass_container"),
                    contentAlignment = Alignment.Center
                ) {
                    val colorGolden = GoldenBrass
                    val colorAntiqueGold = AntiqueGold
                    val colorWhite = WhiteTealHint
                    val textMeasurer = rememberTextMeasurer()

                    Canvas(
                        modifier = Modifier
                            .size(280.dp)
                            .testTag("qibla_compass_canvas")
                            .pointerInput(uiState.isSimulationMode || !uiState.hasCompassSensor) {
                                if (uiState.isSimulationMode || !uiState.hasCompassSensor) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val centerPx = size.width / 2f
                                        val pos = change.position
                                        val dx = pos.x - centerPx
                                        val dy = pos.y - centerPx
                                        var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        angle = (angle + 90f + 360f) % 360f
                                        viewModel.updateSimulatedAzimuth(angle)
                                    }
                                }
                            }
                    ) {
                        val center = size.center
                        val radius = size.minDimension / 2f

                        // Inner bezel
                        drawCircle(
                            color = if (uiState.isAligned) SacredGreen else colorGolden,
                            radius = radius,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        drawCircle(
                            color = TealSurface.copy(alpha = 0.4f),
                            radius = radius - 1.5.dp.toPx()
                        )

                        // Rotatable Compass Dial relative to North
                        rotate(degrees = -animatedAzimuth) {
                            // 360 Ticks
                            for (angle in 0 until 360 step 5) {
                                val isCardinal = angle % 90 == 0
                                val isMajor = angle % 30 == 0
                                
                                val tickLength = if (isCardinal) 15.dp.toPx() else if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                                val tickStroke = if (isCardinal) 3.dp.toPx() else if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                                val tickColor = if (isCardinal) colorGolden else if (isMajor) colorAntiqueGold.copy(alpha = 0.8f) else colorWhite.copy(alpha = 0.35f)

                                rotate(degrees = angle.toFloat(), pivot = center) {
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + tickLength),
                                        strokeWidth = tickStroke
                                    )
                                }
                            }

                            // Cardinals labels: N, E, S, W
                            val cardinals = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                            cardinals.forEach { (label, degree) ->
                                rotate(degrees = degree, pivot = center) {
                                    val textResult = textMeasurer.measure(
                                        text = AnnotatedString(label),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = if (label == "N") 18.sp else 14.sp,
                                            color = if (label == "N") Color(0xFFEF4444) else colorAntiqueGold
                                        )
                                    )
                                    val textW = textResult.size.width
                                    
                                    drawText(
                                        textLayoutResult = textResult,
                                        topLeft = Offset(center.x - textW / 2f, center.y - radius + 18.dp.toPx())
                                    )
                                }
                            }

                            // High-end Mecca Needle pointing to the calculated bearing from north
                            val meccaBearing = uiState.qiblaTrueBearing.toFloat()
                            rotate(degrees = meccaBearing, pivot = center) {
                                val needleTop = center.y - radius * 0.8f

                                val qiblaNeedlePath = Path().apply {
                                    moveTo(center.x - 7.dp.toPx(), center.y)
                                    lineTo(center.x + 7.dp.toPx(), center.y)
                                    lineTo(center.x, needleTop)
                                    close()
                                }

                                drawPath(
                                    path = qiblaNeedlePath,
                                    brush = Brush.linearGradient(
                                        colors = listOf(colorGolden, PaleGold),
                                        start = Offset(center.x, center.y),
                                        end = Offset(center.x, needleTop)
                                    )
                                )

                                // Glowing backdrop for Kaaba
                                val kaabaDimension = 22.dp.toPx()
                                val kaabaY = needleTop - kaabaDimension / 2f

                                drawCircle(
                                    color = if (uiState.isAligned) SacredGreen.copy(alpha = 0.4f) else colorGolden.copy(alpha = 0.15f),
                                    radius = 16.dp.toPx(),
                                    center = Offset(center.x, kaabaY)
                                )

                                // Flat procedural Kaaba structure drawing
                                // Black cube
                                drawRect(
                                    color = CharcoalDark,
                                    topLeft = Offset(center.x - kaabaDimension / 2f, kaabaY - kaabaDimension / 2f),
                                    size = Size(kaabaDimension, kaabaDimension)
                                )

                                // Golden belt (Kiswa)
                                drawRect(
                                    color = colorGolden,
                                    topLeft = Offset(center.x - kaabaDimension / 2f, kaabaY - kaabaDimension / 2f + kaabaDimension * 0.25f),
                                    size = Size(kaabaDimension, kaabaDimension * 0.11f)
                                )

                                // Gold Door
                                drawRect(
                                    color = colorGolden,
                                    topLeft = Offset(center.x - kaabaDimension * 0.1f, kaabaY),
                                    size = Size(kaabaDimension * 0.2f, kaabaDimension * 0.5f)
                                )

                                // Gold border stroke
                                drawRect(
                                    color = colorGolden,
                                    topLeft = Offset(center.x - kaabaDimension / 2f, kaabaY - kaabaDimension / 2f),
                                    size = Size(kaabaDimension, kaabaDimension),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            // Minimalist Red Magnetic North Indicator
                            val nNeedleLength = radius * 0.75f
                            val nNeedleWidth = 5.dp.toPx()
                            
                            val northNeedlePath = Path().apply {
                                moveTo(center.x - nNeedleWidth, center.y)
                                lineTo(center.x + nNeedleWidth, center.y)
                                lineTo(center.x, center.y - nNeedleLength)
                                close()
                            }
                            drawPath(
                                path = northNeedlePath,
                                color = Color(0xFFEF4444)
                            )

                            // Silver South Tail
                            val southNeedlePath = Path().apply {
                                moveTo(center.x - nNeedleWidth, center.y)
                                lineTo(center.x + nNeedleWidth, center.y)
                                lineTo(center.x, center.y + nNeedleLength * 0.8f)
                                close()
                            }
                            drawPath(
                                path = southNeedlePath,
                                color = colorAntiqueGold.copy(alpha = 0.5f)
                            )
                        }

                        // Center pin pivot
                        drawCircle(color = colorGolden, radius = 6.dp.toPx())
                        drawCircle(color = TealSurface, radius = 2.5.dp.toPx())
                    }
                }
            }

            // Interactive simulation toggle (when sensor is available)
            if (uiState.hasCompassSensor) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "ম্যানুয়াল সিমুলেটর টগল",
                                tint = if (uiState.isSimulationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "কম্পাস ইন্টারেক্টিভ সিমুলেটর",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = if (uiState.isSimulationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = uiState.isSimulationMode,
                            onCheckedChange = { viewModel.setSimulationMode(it) },
                            modifier = Modifier.testTag("sim_mode_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldenBrass,
                                checkedTrackColor = TealSurface
                            )
                        )
                    }
                }
            }

            // Slide alignment verification banner
            item {
                AnimatedVisibility(
                    visible = uiState.isAligned,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("aligned_hud_banner"),
                        colors = CardDefaults.cardColors(containerColor = SacredGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "সঠিক বিন্যাস নিশ্চিতকরণ",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "পবিত্র কাবা (মক্কা) বরাবর সঠিক বিন্যাস",
                                style = TextStyle(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Stat Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Distance
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("distance_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "দূরত্ব",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "দূরত্ব",
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = String.format("%,.0f কিমি", uiState.distanceKm),
                                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Qibla angle
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("angle_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "কোণ",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "কিবলার দিক (কোণ)",
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = String.format("%.1f° %s", uiState.qiblaTrueBearing, getCardinalDirection(uiState.qiblaTrueBearing)),
                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Precision specifications card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "দিকনির্দেশনার বিস্তারিত তথ্য",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ডিভাইসের দিক (Heading):",
                                style = TextStyle(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = String.format("%.1f° %s", uiState.deviceAzimuth, getCardinalDirection(uiState.deviceAzimuth.toDouble())),
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "চৌম্বকীয় বিচ্যুতি (Declination):",
                                style = TextStyle(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = String.format(
                                    "%+.1f° (%s)", 
                                    uiState.magneticDeclination,
                                    if (uiState.magneticDeclination >= 0) "পূর্ব" else "পশ্চিম"
                                ),
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "কিবলার সাথে পার্থক্য:",
                                style = TextStyle(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            val diff = shortestAngleDifference(uiState.deviceAzimuth.toDouble(), uiState.qiblaTrueBearing)
                            Text(
                                text = String.format("%.1f° %s", Math.abs(diff), if (diff > 0) "ডানে ঘোরান" else "বামে ঘোরান"),
                                style = TextStyle(
                                    fontSize = 13.sp, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isAligned) SacredGreen else MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // LIST DIALOGS
    // 1. Predefined City selection dialog
    if (uiState.showCityDialog) {
        Dialog(onDismissRequest = { viewModel.setShowCityDialog(false) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .testTag("city_picker_dialog"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "স্টেশন শহর নির্বাচন করুন",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(viewModel.predefinedCities) { city ->
                            Text(
                                text = city.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectPredefinedCity(city) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("city_item_${city.name.replace(" ", "_").replace(",", "")}"),
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                color = if (uiState.locationName == city.name) GoldenBrass else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.setShowCityDialog(false) }) {
                            Text("বাতিল", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }

    // 2. Custom coordinates dialog
    if (uiState.showCustomCoordsDialog) {
        var inputLat by remember { mutableStateOf("") }
        var inputLon by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { viewModel.setShowCustomCoordsDialog(false) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_coords_dialog"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "কাস্টম স্টেশন প্রবেশ করান",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    
                    OutlinedTextField(
                        value = inputLat,
                        onValueChange = { inputLat = it; inputError = null },
                        label = { Text("অক্ষাংশ Latitude (-৯০.০ থেকে ৯০.০)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("custom_lat_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldenBrass,
                            unfocusedBorderColor = AntiqueGold.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = inputLon,
                        onValueChange = { inputLon = it; inputError = null },
                        label = { Text("দ্রাঘিমাংশ Longitude (-১৮০.০ থেকে ১৮০.০)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("custom_lon_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldenBrass,
                            unfocusedBorderColor = AntiqueGold.copy(alpha = 0.5f)
                        )
                    )

                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.setShowCustomCoordsDialog(false) }) {
                            Text("বাতিল", color = MaterialTheme.colorScheme.secondary)
                        }
                        
                        Button(
                            onClick = {
                                val latDouble = inputLat.toDoubleOrNull()
                                val lonDouble = inputLon.toDoubleOrNull()
                                if (latDouble == null || latDouble < -90.0 || latDouble > 90.0) {
                                    inputError = "অনুগ্রহ করে অক্ষাংশ -৯০ এবং ৯০ এর মধ্যে লিখুন।"
                                } else if (lonDouble == null || lonDouble < -180.0 || lonDouble > 180.0) {
                                    inputError = "অনুগ্রহ করে দ্রাঘিমাংশ -১৮০ এবং ১৮০ এর মধ্যে লিখুন।"
                                } else {
                                    viewModel.applyCustomCoordinates(latDouble, lonDouble)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenBrass, contentColor = DeepTeal),
                            modifier = Modifier.testTag("apply_coords_button")
                        ) {
                            Text("প্রয়োগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun getCardinalDirection(bearing: Double): String {
    val directions = listOf("উঃ", "উ-উ-পূ", "উত্তর-পূর্ব", "পূ-উ-পূ", "পূাঃ", "পূ-দ-পূ", "দক্ষিণ-পূর্ব", "দ-দ-পূ", "দঃ", "দ-দ-প", "দক্ষিণ-পশ্চিম", "প-দ-প", "পঃ", "প-উ-প", "উত্তর-পশ্চিম", "উ-উ-প", "উঃ")
    val index = ((bearing + 11.25) / 22.5).toInt() % 16
    return directions[index]
}

private fun shortestAngleDifference(angle1: Double, angle2: Double): Double {
    var diff = (angle2 - angle1) % 360.0
    if (diff < -180.0) {
        diff += 360.0
    } else if (diff > 180.0) {
        diff -= 360.0
    }
    return diff
}
