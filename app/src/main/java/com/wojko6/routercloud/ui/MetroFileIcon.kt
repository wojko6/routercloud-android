package com.wojko6.routercloud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.wojko6.routercloud.network.RouterCloudEntry

private val MetroFolderYellow = Color(0xFFFFC83D)

private enum class MetroFileType {
    Folder,
    Pdf,
    Image,
    Video,
    Audio,
    Archive,
    Code,
    Document,
    Generic,
}

@Composable
fun MetroFileIcon(
    entry: RouterCloudEntry,
    modifier: Modifier = Modifier,
    fileColor: Color = Color.White,
) {
    val type = classifyMetroFile(entry)

    Canvas(
        modifier = modifier.size(27.dp),
    ) {
        when (type) {
            MetroFileType.Folder ->
                drawMetroFolder(MetroFolderYellow)

            MetroFileType.Pdf ->
                drawMetroPdf(fileColor)

            MetroFileType.Image ->
                drawMetroImage(fileColor)

            MetroFileType.Video ->
                drawMetroVideo(fileColor)

            MetroFileType.Audio ->
                drawMetroAudio(fileColor)

            MetroFileType.Archive ->
                drawMetroArchive(fileColor)

            MetroFileType.Code ->
                drawMetroCode(fileColor)

            MetroFileType.Document ->
                drawMetroDocument(fileColor)

            MetroFileType.Generic ->
                drawMetroGenericFile(fileColor)
        }
    }
}

private fun classifyMetroFile(
    entry: RouterCloudEntry,
): MetroFileType {
    if (entry.isDirectory) {
        return MetroFileType.Folder
    }

    val ext = entry.name
        .substringAfterLast('.', "")
        .lowercase()

    return when (ext) {
        "pdf" ->
            MetroFileType.Pdf

        "jpg", "jpeg", "png", "gif",
        "webp", "bmp", "heic", "heif",
        "svg" ->
            MetroFileType.Image

        "mp4", "mkv", "avi", "mov",
        "webm", "m4v", "mpeg", "mpg" ->
            MetroFileType.Video

        "mp3", "flac", "wav", "ogg",
        "m4a", "aac", "opus" ->
            MetroFileType.Audio

        "zip", "7z", "rar", "tar",
        "gz", "bz2", "xz", "tgz",
        "zst" ->
            MetroFileType.Archive

        "kt", "kts", "java", "py",
        "js", "ts", "tsx", "jsx",
        "sh", "bash", "zsh", "c",
        "cpp", "h", "hpp", "rs",
        "go", "php", "rb", "sql",
        "html", "css", "xml", "json",
        "yaml", "yml", "toml" ->
            MetroFileType.Code

        "txt", "md", "log", "csv",
        "doc", "docx", "odt",
        "xls", "xlsx", "ods",
        "ppt", "pptx", "odp", "rtf" ->
            MetroFileType.Document

        else ->
            MetroFileType.Generic
    }
}

private fun DrawScope.metroStroke(
    color: Color,
) = Stroke(
    width = 1.8.dp.toPx(),
    cap = StrokeCap.Butt,
    join = StrokeJoin.Miter,
)

private fun DrawScope.drawMetroFolder(
    color: Color,
) {
    val w = size.width
    val h = size.height

    val path = Path().apply {
        moveTo(w * 0.08f, h * 0.28f)
        lineTo(w * 0.39f, h * 0.28f)
        lineTo(w * 0.48f, h * 0.39f)
        lineTo(w * 0.92f, h * 0.39f)
        lineTo(w * 0.92f, h * 0.82f)
        lineTo(w * 0.08f, h * 0.82f)
        close()
    }

    drawPath(
        path = path,
        color = color,
    )
}

private fun DrawScope.drawPageOutline(
    color: Color,
) {
    val w = size.width
    val h = size.height

    val path = Path().apply {
        moveTo(w * 0.20f, h * 0.08f)
        lineTo(w * 0.64f, h * 0.08f)
        lineTo(w * 0.84f, h * 0.28f)
        lineTo(w * 0.84f, h * 0.92f)
        lineTo(w * 0.20f, h * 0.92f)
        close()

        moveTo(w * 0.64f, h * 0.08f)
        lineTo(w * 0.64f, h * 0.28f)
        lineTo(w * 0.84f, h * 0.28f)
    }

    drawPath(
        path = path,
        color = color,
        style = metroStroke(color),
    )
}

private fun DrawScope.drawMetroGenericFile(
    color: Color,
) {
    drawPageOutline(color)
}

private fun DrawScope.drawMetroDocument(
    color: Color,
) {
    drawPageOutline(color)

    val stroke = metroStroke(color)
    val w = size.width
    val h = size.height

    drawLine(
        color,
        Offset(w * 0.32f, h * 0.48f),
        Offset(w * 0.72f, h * 0.48f),
        strokeWidth = stroke.width,
    )

    drawLine(
        color,
        Offset(w * 0.32f, h * 0.62f),
        Offset(w * 0.72f, h * 0.62f),
        strokeWidth = stroke.width,
    )

    drawLine(
        color,
        Offset(w * 0.32f, h * 0.76f),
        Offset(w * 0.61f, h * 0.76f),
        strokeWidth = stroke.width,
    )
}

private fun DrawScope.drawMetroPdf(
    color: Color,
) {
    drawPageOutline(color)

    val w = size.width
    val h = size.height
    val strokeWidth = 1.7.dp.toPx()

    drawLine(
        color,
        Offset(w * 0.30f, h * 0.52f),
        Offset(w * 0.30f, h * 0.78f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.30f, h * 0.52f),
        Offset(w * 0.43f, h * 0.52f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.43f, h * 0.52f),
        Offset(w * 0.43f, h * 0.64f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.30f, h * 0.64f),
        Offset(w * 0.43f, h * 0.64f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.50f, h * 0.52f),
        Offset(w * 0.50f, h * 0.78f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.50f, h * 0.52f),
        Offset(w * 0.61f, h * 0.52f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.61f, h * 0.52f),
        Offset(w * 0.61f, h * 0.78f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.50f, h * 0.78f),
        Offset(w * 0.61f, h * 0.78f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.68f, h * 0.52f),
        Offset(w * 0.68f, h * 0.78f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.68f, h * 0.52f),
        Offset(w * 0.79f, h * 0.52f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.68f, h * 0.64f),
        Offset(w * 0.77f, h * 0.64f),
        strokeWidth,
    )
}

private fun DrawScope.drawMetroImage(
    color: Color,
) {
    val w = size.width
    val h = size.height
    val stroke = metroStroke(color)

    drawRect(
        color = color,
        topLeft = Offset(w * 0.11f, h * 0.18f),
        size = androidx.compose.ui.geometry.Size(
            w * 0.78f,
            h * 0.64f,
        ),
        style = stroke,
    )

    drawCircle(
        color = color,
        radius = w * 0.07f,
        center = Offset(w * 0.68f, h * 0.36f),
        style = stroke,
    )

    val mountains = Path().apply {
        moveTo(w * 0.18f, h * 0.72f)
        lineTo(w * 0.39f, h * 0.48f)
        lineTo(w * 0.52f, h * 0.61f)
        lineTo(w * 0.62f, h * 0.51f)
        lineTo(w * 0.82f, h * 0.72f)
    }

    drawPath(
        mountains,
        color,
        style = stroke,
    )
}

private fun DrawScope.drawMetroVideo(
    color: Color,
) {
    val w = size.width
    val h = size.height
    val stroke = metroStroke(color)

    drawRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.23f),
        size = androidx.compose.ui.geometry.Size(
            w * 0.60f,
            h * 0.54f,
        ),
        style = stroke,
    )

    val triangle = Path().apply {
        moveTo(w * 0.44f, h * 0.38f)
        lineTo(w * 0.44f, h * 0.63f)
        lineTo(w * 0.62f, h * 0.505f)
        close()
    }

    drawPath(
        triangle,
        color,
    )

    val camera = Path().apply {
        moveTo(w * 0.72f, h * 0.38f)
        lineTo(w * 0.90f, h * 0.30f)
        lineTo(w * 0.90f, h * 0.70f)
        lineTo(w * 0.72f, h * 0.62f)
        close()
    }

    drawPath(
        camera,
        color,
        style = stroke,
    )
}

private fun DrawScope.drawMetroAudio(
    color: Color,
) {
    val w = size.width
    val h = size.height
    val strokeWidth = 2.dp.toPx()

    drawLine(
        color,
        Offset(w * 0.58f, h * 0.19f),
        Offset(w * 0.58f, h * 0.68f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.58f, h * 0.19f),
        Offset(w * 0.84f, h * 0.13f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.84f, h * 0.13f),
        Offset(w * 0.84f, h * 0.58f),
        strokeWidth,
    )

    drawCircle(
        color,
        radius = w * 0.13f,
        center = Offset(w * 0.46f, h * 0.72f),
    )

    drawCircle(
        color,
        radius = w * 0.13f,
        center = Offset(w * 0.72f, h * 0.62f),
    )
}

private fun DrawScope.drawMetroArchive(
    color: Color,
) {
    drawPageOutline(color)

    val w = size.width
    val h = size.height
    val strokeWidth = 1.7.dp.toPx()

    val x = w * 0.52f

    for (i in 0..4) {
        val y = h * (0.40f + i * 0.09f)

        drawLine(
            color,
            Offset(x, y),
            Offset(x + w * 0.10f, y),
            strokeWidth,
        )
    }
}

private fun DrawScope.drawMetroCode(
    color: Color,
) {
    val w = size.width
    val h = size.height
    val strokeWidth = 2.dp.toPx()

    drawLine(
        color,
        Offset(w * 0.37f, h * 0.30f),
        Offset(w * 0.16f, h * 0.50f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.16f, h * 0.50f),
        Offset(w * 0.37f, h * 0.70f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.63f, h * 0.30f),
        Offset(w * 0.84f, h * 0.50f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.84f, h * 0.50f),
        Offset(w * 0.63f, h * 0.70f),
        strokeWidth,
    )

    drawLine(
        color,
        Offset(w * 0.57f, h * 0.22f),
        Offset(w * 0.43f, h * 0.78f),
        strokeWidth,
    )
}
