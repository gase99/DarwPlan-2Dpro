package ir.cabinetplan.fa.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathBuilder().apply(block).nodes,
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object CadIcons {
    val Select: ImageVector = strokeIcon("Select") {
        moveTo(4f, 4f); lineTo(11f, 20f); lineTo(14f, 13f); lineTo(21f, 10f); close()
    }
    val DrawWall: ImageVector = strokeIcon("DrawWall") {
        moveTo(12f, 19f); lineTo(19f, 12f); lineTo(22f, 15f); lineTo(15f, 22f); close()
        moveTo(18f, 13f); lineTo(16.5f, 5.5f); lineTo(2f, 2f); lineTo(5.5f, 16.5f); lineTo(13f, 18f)
    }
    val Measure: ImageVector = strokeIcon("Measure") {
        moveTo(21.3f, 15.3f); lineTo(8.7f, 2.7f); lineTo(2.7f, 8.7f); lineTo(15.3f, 21.3f); close()
        moveTo(7.5f, 10.5f); lineTo(9.5f, 8.5f)
        moveTo(10.5f, 13.5f); lineTo(12.5f, 11.5f)
        moveTo(13.5f, 16.5f); lineTo(15.5f, 14.5f)
    }
    val Pan: ImageVector = strokeIcon("Pan") {
        moveTo(12f, 2f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(22f, 12f)
        moveTo(9f, 5f); lineTo(12f, 2f); lineTo(15f, 5f)
        moveTo(9f, 19f); lineTo(12f, 22f); lineTo(15f, 19f)
        moveTo(5f, 9f); lineTo(2f, 12f); lineTo(5f, 15f)
        moveTo(19f, 9f); lineTo(22f, 12f); lineTo(19f, 15f)
    }
    val MultiSelect: ImageVector = strokeIcon("MultiSelect") {
        moveTo(8f, 3f); lineTo(21f, 3f); lineTo(21f, 16f); lineTo(8f, 16f); close()
        moveTo(3f, 8f); lineTo(3f, 21f); lineTo(16f, 21f)
    }
    val Magnet: ImageVector = strokeIcon("Magnet") {
        moveTo(6f, 3f); lineTo(6f, 12f)
        curveTo(6f, 16f, 9f, 19f, 12f, 19f)
        curveTo(15f, 19f, 18f, 16f, 18f, 12f)
        lineTo(18f, 3f)
        moveTo(4f, 7f); lineTo(8f, 7f)
        moveTo(16f, 7f); lineTo(20f, 7f)
    }
    val AngleLock: ImageVector = strokeIcon("AngleLock") {
        moveTo(4f, 4f); lineTo(4f, 20f); lineTo(20f, 20f)
        moveTo(4f, 13f); lineTo(11f, 13f); lineTo(11f, 20f)
    }
    val Undo: ImageVector = strokeIcon("Undo") {
        moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
        moveTo(4f, 9f); lineTo(15f, 9f)
        curveTo(18.5f, 9f, 20f, 11.5f, 20f, 14.5f)
        curveTo(20f, 17.5f, 18.5f, 20f, 15f, 20f)
        lineTo(11f, 20f)
    }
    val Redo: ImageVector = strokeIcon("Redo") {
        moveTo(15f, 14f); lineTo(20f, 9f); lineTo(15f, 4f)
        moveTo(20f, 9f); lineTo(9f, 9f)
        curveTo(5.5f, 9f, 4f, 11.5f, 4f, 14.5f)
        curveTo(4f, 17.5f, 5.5f, 20f, 9f, 20f)
        lineTo(13f, 20f)
    }
    val Library: ImageVector = strokeIcon("Library") {
        moveTo(3f, 7f); lineTo(12f, 3f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 21f); lineTo(3f, 17f); close()
        moveTo(3f, 7f); lineTo(12f, 11f); lineTo(21f, 7f)
        moveTo(12f, 11f); lineTo(12f, 21f)
    }
    val Inspector: ImageVector = strokeIcon("Inspector") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(20f, 18f)
        moveTo(9f, 4f); lineTo(9f, 8f)
        moveTo(15f, 10f); lineTo(15f, 14f)
        moveTo(11f, 16f); lineTo(11f, 20f)
    }
    val Bom: ImageVector = strokeIcon("Bom") {
        moveTo(12f, 2f); lineTo(2f, 7f); lineTo(12f, 12f); lineTo(22f, 7f); close()
        moveTo(2f, 12f); lineTo(12f, 17f); lineTo(22f, 12f)
        moveTo(2f, 17f); lineTo(12f, 22f); lineTo(22f, 17f)
    }
    val Projects: ImageVector = strokeIcon("Projects") {
        moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
    }
    val Export: ImageVector = strokeIcon("Export") {
        moveTo(12f, 3f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 19f); lineTo(20f, 19f)
    }
    val Rotate90: ImageVector = strokeIcon("Rotate90") {
        moveTo(21f, 12f)
        curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f)
        curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        curveTo(15.5f, 3f, 18.5f, 5f, 20f, 8f)
        moveTo(21f, 3f); lineTo(21f, 8f); lineTo(16f, 8f)
    }
    val Duplicate: ImageVector = strokeIcon("Duplicate") {
        moveTo(9f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(9f, 20f); close()
        moveTo(5f, 15f); lineTo(4f, 15f); lineTo(4f, 4f); lineTo(15f, 4f); lineTo(15f, 5f)
    }
    val Trash: ImageVector = strokeIcon("Trash") {
        moveTo(3f, 6f); lineTo(21f, 6f)
        moveTo(8f, 6f); lineTo(8f, 4f); lineTo(16f, 4f); lineTo(16f, 6f)
        moveTo(6f, 6f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 6f)
    }
    val Plus: ImageVector = strokeIcon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val FitView: ImageVector = strokeIcon("FitView") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }
    val Elevation: ImageVector = strokeIcon("Elevation") {
        moveTo(3f, 20f); lineTo(21f, 20f)
        moveTo(5f, 20f); lineTo(5f, 8f); lineTo(12f, 8f); lineTo(12f, 20f)
        moveTo(12f, 12f); lineTo(19f, 12f); lineTo(19f, 20f)
    }
}
