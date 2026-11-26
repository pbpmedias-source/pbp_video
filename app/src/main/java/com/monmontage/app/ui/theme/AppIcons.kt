package com.monmontage.app.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Icônes au trait de la maquette (absentes du jeu d'icônes Material de base). */
object AppIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d,
        )

    val Music: ImageVector by lazy {
        icon("Music") {
            stroke { moveTo(9f, 18f); lineTo(9f, 5f); lineTo(20f, 3f); lineTo(20f, 16f) }
            stroke { moveTo(9f, 18f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f) }
            stroke { moveTo(20f, 16f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f) }
        }
    }

    val Title: ImageVector by lazy {
        icon("Title") {
            stroke { moveTo(4f, 7f); lineTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 7f) }
            stroke { moveTo(12f, 5f); lineTo(12f, 19f) }
            stroke { moveTo(9f, 19f); lineTo(15f, 19f) }
        }
    }

    val Lines: ImageVector by lazy {
        icon("Lines") {
            stroke { moveTo(5f, 7f); lineTo(19f, 7f) }
            stroke { moveTo(5f, 12f); lineTo(19f, 12f) }
            stroke { moveTo(5f, 17f); lineTo(14f, 17f) }
        }
    }

    val Scissors: ImageVector by lazy {
        icon("Scissors") {
            stroke { moveTo(9f, 6f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f) }
            stroke { moveTo(9f, 18f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f) }
            stroke { moveTo(20f, 4f); lineTo(8.1f, 15.9f) }
            stroke { moveTo(14.5f, 14.5f); lineTo(20f, 20f) }
            stroke { moveTo(8.1f, 8.1f); lineTo(12f, 12f) }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.Black)) { moveTo(7f, 5f); lineTo(11f, 5f); lineTo(11f, 19f); lineTo(7f, 19f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(13f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(13f, 19f); close() }
        }
    }

    val Transition: ImageVector by lazy {
        icon("Transition") {
            stroke { moveTo(4.5f, 6f); lineTo(9.5f, 6f); quadTo(11f, 6f, 11f, 7.5f); lineTo(11f, 16.5f); quadTo(11f, 18f, 9.5f, 18f); lineTo(4.5f, 18f); quadTo(3f, 18f, 3f, 16.5f); lineTo(3f, 7.5f); quadTo(3f, 6f, 4.5f, 6f); close() }
            stroke { moveTo(14.5f, 6f); lineTo(19.5f, 6f); quadTo(21f, 6f, 21f, 7.5f); lineTo(21f, 16.5f); quadTo(21f, 18f, 19.5f, 18f); lineTo(14.5f, 18f); quadTo(13f, 18f, 13f, 16.5f); lineTo(13f, 7.5f); quadTo(13f, 6f, 14.5f, 6f); close() }
        }
    }

    val YouTube: ImageVector by lazy {
        icon("YouTube") {
            stroke { moveTo(6f, 5f); lineTo(18f, 5f); quadTo(22f, 5f, 22f, 9f); lineTo(22f, 15f); quadTo(22f, 19f, 18f, 19f); lineTo(6f, 19f); quadTo(2f, 19f, 2f, 15f); lineTo(2f, 9f); quadTo(2f, 5f, 6f, 5f); close() }
            path(fill = SolidColor(Color.Black)) { moveTo(10f, 9.5f); lineTo(14.5f, 12f); lineTo(10f, 14.5f); close() }
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
                moveTo(12f, 10f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
                moveTo(12f, 17f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
            }
        }
    }
}
