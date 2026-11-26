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
}
