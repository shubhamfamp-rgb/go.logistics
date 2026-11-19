package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.VehicleClass
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class Face3D(
    val vertices: List<Vec3>,
    val baseColor: Color,
    val strokeColor: Color = Color(0x33000000)
)

@Composable
fun Vehicle3DShowcase(
    vehicleClass: VehicleClass,
    plateNumber: String = "",
    modifier: Modifier = Modifier
) {
    var yaw by remember(vehicleClass) { mutableFloatStateOf(-0.62f) }
    var pitch by remember(vehicleClass) { mutableFloatStateOf(0.32f) }

    val bgTop = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val bgBottom = MaterialTheme.colorScheme.surface
    val accentBlue = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(bgTop, bgBottom))
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${vehicleClass.label} 3D Studio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${vehicleClass.axlesLabel} · Drag to rotate 360°",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (plateNumber.isNotBlank()) {
                Text(
                    text = plateNumber,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(vehicleClass) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.011f
                        pitch = (pitch - dragAmount.y * 0.008f).coerceIn(0.05f, 0.95f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                draw3DVehicleScene(
                    vehicleClass = vehicleClass,
                    yaw = yaw,
                    pitch = pitch,
                    accentBlue = accentBlue,
                    isDark = isDark
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            listOf(
                Triple("3/4 View", -0.62f, 0.32f),
                Triple("Side", -1.55f, 0.14f),
                Triple("Front", 0.02f, 0.22f),
                Triple("Top", -0.65f, 0.85f)
            ).forEach { (label, presetYaw, presetPitch) ->
                FilterChip(
                    selected = kotlin.math.abs(yaw - presetYaw) < 0.15f &&
                        kotlin.math.abs(pitch - presetPitch) < 0.15f,
                    onClick = {
                        yaw = presetYaw
                        pitch = presetPitch
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

private fun DrawScope.draw3DVehicleScene(
    vehicleClass: VehicleClass,
    yaw: Float,
    pitch: Float,
    accentBlue: Color,
    isDark: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height * 0.56f
    val unit = min(size.width, size.height) * 0.21f
    val focal = 7.5f

    // Subtle ground studio shadow ellipse
    drawOval(
        color = if (isDark) Color(0x66000000) else Color(0x1E1D1D1F),
        topLeft = Offset(cx - unit * 2.2f, cy + unit * 0.65f),
        size = androidx.compose.ui.geometry.Size(unit * 4.4f, unit * 0.85f)
    )

    val faces = mutableListOf<Face3D>()
    val bodySilver = if (isDark) Color(0xFFD1D5DB) else Color(0xFFF3F4F6)
    val bodySlate = if (isDark) Color(0xFF4B5563) else Color(0xFFCBD5E1)
    val glassColor = Color(0xFF1E293B)
    val tireColor = Color(0xFF18181B)
    val rimColor = Color(0xFF9CA3AF)
    val headlightColor = Color(0xFFFEF08A)
    val taillightColor = Color(0xFFEF4444)

    fun addBox(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, color: Color) {
        val minX = min(x1, x2); val maxX = max(x1, x2)
        val minY = min(y1, y2); val maxY = max(y1, y2)
        val minZ = min(z1, z2); val maxZ = max(z1, z2)

        // Front (+Z), Back (-Z), Top (+Y), Bottom (-Y), Right (+X), Left (-X)
        faces += Face3D(listOf(Vec3(minX, minY, maxZ), Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(minX, maxY, maxZ)), color)
        faces += Face3D(listOf(Vec3(maxX, minY, minZ), Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), Vec3(maxX, maxY, minZ)), color)
        faces += Face3D(listOf(Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), Vec3(minX, maxY, minZ)), color)
        faces += Face3D(listOf(Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), Vec3(minX, minY, maxZ)), color)
        faces += Face3D(listOf(Vec3(maxX, minY, maxZ), Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), Vec3(maxX, maxY, maxZ)), color)
        faces += Face3D(listOf(Vec3(minX, minY, minZ), Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), Vec3(minX, maxY, minZ)), color)
    }

    fun addWheel(wx: Float, wy: Float, wz: Float, r: Float = 0.34f, w: Float = 0.22f) {
        addBox(wx - w / 2f, wy - r, wz - r, wx + w / 2f, wy + r, wz + r, tireColor)
        addBox(wx - w / 2f - 0.02f, wy - r * 0.5f, wz - r * 0.5f, wx + w / 2f + 0.02f, wy + r * 0.5f, wz + r * 0.5f, rimColor)
    }

    when (vehicleClass) {
        VehicleClass.LMV -> {
            // Sleek SUV / Pickup chassis
            addBox(-0.85f, -0.35f, -1.65f, 0.85f, 0.22f, 1.65f, bodySilver)
            // Cabin roof
            addBox(-0.76f, 0.22f, -0.95f, 0.76f, 0.72f, 0.65f, bodySilver)
            // Windshield + rear glass
            addBox(-0.68f, 0.25f, 0.66f, 0.68f, 0.64f, 0.68f, glassColor)
            addBox(-0.68f, 0.25f, -0.97f, 0.68f, 0.64f, -0.94f, glassColor)
            // Side windows
            addBox(-0.78f, 0.27f, -0.8f, 0.78f, 0.62f, 0.5f, glassColor)
            // Accent stripe & headlights
            addBox(-0.7f, -0.05f, 1.66f, -0.35f, 0.12f, 1.68f, headlightColor)
            addBox(0.35f, -0.05f, 1.66f, 0.7f, 0.12f, 1.68f, headlightColor)
            addBox(-0.7f, -0.05f, -1.68f, -0.35f, 0.12f, -1.66f, taillightColor)
            addBox(0.35f, -0.05f, -1.68f, 0.7f, 0.12f, -1.66f, taillightColor)
            addWheel(-0.88f, -0.38f, 1.0f)
            addWheel(0.88f, -0.38f, 1.0f)
            addWheel(-0.88f, -0.38f, -1.0f)
            addWheel(0.88f, -0.38f, -1.0f)
        }

        VehicleClass.BIKE -> {
            // Two-wheeler delivery bike
            addBox(-0.18f, -0.15f, -1.05f, 0.18f, 0.22f, 0.9f, accentBlue)
            // Seat & courier box
            addBox(-0.22f, 0.22f, -0.45f, 0.22f, 0.36f, 0.25f, tireColor)
            addBox(-0.34f, 0.25f, -1.18f, 0.34f, 0.78f, -0.52f, bodySilver)
            // Fork & Handlebars
            addBox(-0.08f, -0.25f, 0.85f, 0.08f, 0.68f, 1.02f, bodySlate)
            addBox(-0.48f, 0.62f, 0.82f, 0.48f, 0.72f, 0.94f, tireColor)
            // Headlight
            addBox(-0.14f, 0.38f, 1.03f, 0.14f, 0.56f, 1.08f, headlightColor)
            // 2 Wheels
            addWheel(0f, -0.45f, 0.95f, r = 0.42f, w = 0.18f)
            addWheel(0f, -0.45f, -0.95f, r = 0.42f, w = 0.18f)
        }

        VehicleClass.BUS -> {
            // Coach Body
            addBox(-0.92f, -0.40f, -2.55f, 0.92f, 1.05f, 2.55f, bodySilver)
            // Blue lower livery band
            addBox(-0.94f, -0.32f, -2.56f, 0.94f, -0.05f, 2.56f, accentBlue)
            // Panoramic windows
            addBox(-0.94f, 0.25f, -2.35f, 0.94f, 0.78f, 2.48f, glassColor)
            // Front windshield & lights
            addBox(-0.82f, 0.15f, 2.56f, 0.82f, 0.86f, 2.58f, glassColor)
            addBox(-0.76f, -0.24f, 2.56f, -0.45f, -0.08f, 2.58f, headlightColor)
            addBox(0.45f, -0.24f, 2.56f, 0.76f, -0.08f, 2.58f, headlightColor)
            // Roof AC pods
            addBox(-0.45f, 1.05f, -0.6f, 0.45f, 1.22f, 0.7f, bodySlate)
            addWheel(-0.92f, -0.44f, 1.55f, r = 0.38f)
            addWheel(0.92f, -0.44f, 1.55f, r = 0.38f)
            addWheel(-0.92f, -0.44f, -1.45f, r = 0.38f)
            addWheel(0.92f, -0.44f, -1.45f, r = 0.38f)
        }

        VehicleClass.TRUCK -> {
            // Heavy Rigid Freight Truck
            addBox(-0.92f, -0.42f, -2.35f, 0.92f, -0.18f, 2.25f, bodySlate)
            // Cargo Container Box
            addBox(-0.95f, -0.18f, -2.38f, 0.95f, 1.22f, 0.85f, bodySilver)
            // Cargo Accent Stripe
            addBox(-0.97f, 0.38f, -2.38f, 0.97f, 0.56f, 0.85f, accentBlue)
            // Cab
            addBox(-0.88f, -0.18f, 0.95f, 0.88f, 1.04f, 2.25f, accentBlue)
            // Windshield & Grille
            addBox(-0.78f, 0.42f, 2.26f, 0.78f, 0.90f, 2.28f, glassColor)
            addBox(-0.55f, -0.05f, 2.26f, 0.55f, 0.25f, 2.28f, bodySlate)
            addBox(-0.80f, -0.08f, 2.26f, -0.60f, 0.08f, 2.28f, headlightColor)
            addBox(0.60f, -0.08f, 2.26f, 0.80f, 0.08f, 2.28f, headlightColor)
            // 6 Wheels
            addWheel(-0.92f, -0.46f, 1.65f, r = 0.38f)
            addWheel(0.92f, -0.46f, 1.65f, r = 0.38f)
            addWheel(-0.92f, -0.46f, -0.85f, r = 0.38f)
            addWheel(0.92f, -0.46f, -0.85f, r = 0.38f)
            addWheel(-0.92f, -0.46f, -1.78f, r = 0.38f)
            addWheel(0.92f, -0.46f, -1.78f, r = 0.38f)
        }

        VehicleClass.LONG_VEHICLE -> {
            // Tractor-Trailer 40ft Articulated Semi
            addBox(-0.85f, -0.42f, -3.25f, 0.85f, -0.20f, 2.95f, bodySlate)
            // Long 40ft Container Trailer
            addBox(-0.96f, -0.16f, -3.30f, 0.96f, 1.24f, 1.15f, bodySilver)
            addBox(-0.98f, 0.40f, -3.30f, 0.98f, 0.58f, 1.15f, accentBlue)
            // Sleeper Prime Mover Cab
            addBox(-0.88f, -0.18f, 1.38f, 0.88f, 1.12f, 2.95f, accentBlue)
            addBox(-0.78f, 0.44f, 2.96f, 0.78f, 0.95f, 2.98f, glassColor)
            addBox(-0.55f, -0.04f, 2.96f, 0.55f, 0.28f, 2.98f, bodySlate)
            addBox(-0.80f, -0.08f, 2.96f, -0.60f, 0.08f, 2.98f, headlightColor)
            addBox(0.60f, -0.08f, 2.96f, 0.80f, 0.08f, 2.98f, headlightColor)
            // Multi-axle wheels
            listOf(2.40f, 1.55f, -0.95f, -1.85f, -2.75f).forEach { wz ->
                addWheel(-0.92f, -0.46f, wz, r = 0.36f)
                addWheel(0.92f, -0.46f, wz, r = 0.36f)
            }
        }
    }

    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val cosX = cos(pitch)
    val sinX = sin(pitch)

    fun rotate(v: Vec3): Vec3 {
        val x1 = v.x * cosY + v.z * sinY
        val z1 = -v.x * sinY + v.z * cosY
        val y2 = v.y * cosX - z1 * sinX
        val z2 = v.y * sinX + z1 * cosX
        return Vec3(x1, y2, z2)
    }

    val lightDir = Vec3(0.42f, 0.78f, 0.46f)

    data class ProjectedFace(
        val points: List<Offset>,
        val avgZ: Float,
        val shadedColor: Color,
        val strokeColor: Color
    )

    val projected = faces.mapNotNull { face ->
        val rotVerts = face.vertices.map(::rotate)
        val avgZ = rotVerts.map { it.z }.average().toFloat()

        // Compute face normal for backface & diffuse lighting
        val a = rotVerts[0]
        val b = rotVerts[1]
        val c = rotVerts[2]
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val nLen = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        val dot = ((nx / nLen) * lightDir.x + (ny / nLen) * lightDir.y + (nz / nLen) * lightDir.z)
        val shade = (0.58f + 0.42f * kotlin.math.abs(dot)).coerceIn(0.45f, 1.05f)

        val pts = rotVerts.map { rv ->
            val depth = (focal - rv.z).coerceAtLeast(1.5f)
            val perspective = focal / depth
            Offset(
                x = cx + rv.x * unit * perspective,
                y = cy - rv.y * unit * perspective
            )
        }

        val shaded = Color(
            red = (face.baseColor.red * shade).coerceIn(0f, 1f),
            green = (face.baseColor.green * shade).coerceIn(0f, 1f),
            blue = (face.baseColor.blue * shade).coerceIn(0f, 1f),
            alpha = face.baseColor.alpha
        )

        ProjectedFace(pts, avgZ, shaded, face.strokeColor)
    }.sortedBy { it.avgZ }

    for (pf in projected) {
        val path = Path().apply {
            moveTo(pf.points[0].x, pf.points[0].y)
            for (i in 1 until pf.points.size) {
                lineTo(pf.points[i].x, pf.points[i].y)
            }
            close()
        }
        drawPath(path, color = pf.shadedColor)
        drawPath(path, color = pf.strokeColor, style = Stroke(width = 1.2f))
    }
}
