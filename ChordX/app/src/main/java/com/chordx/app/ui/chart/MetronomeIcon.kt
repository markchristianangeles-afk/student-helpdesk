package com.chordx.app.ui.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A triangular (tapered) metronome: outlined body, swinging arm and a weight on the arm. */
val MetronomeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Metronome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Tapered body
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9.5f, 3f)
            lineTo(14.5f, 3f)
            lineTo(20f, 21f)
            lineTo(4f, 21f)
            close()
        }
        // Arm
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 17f)
            lineTo(15.5f, 6.5f)
        }
        // Weight on the arm
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.2f, 11f)
            lineTo(16.6f, 11f)
            lineTo(16.6f, 13.2f)
            lineTo(13.2f, 13.2f)
            close()
        }
    }.build()
}
