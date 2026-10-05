package com.wojko6.routercloud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MetroActionGlyphType {
    Upload,
    NewFolder,
    Files,
    Recent,
    Home,
    Add,
    More,
}

@Composable
fun MetroActionGlyph(
    type: MetroActionGlyphType,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 48.dp,
) {
    val color = LocalContentColor.current

    Canvas(
        modifier = modifier.size(glyphSize),
    ) {
        val stroke = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Butt,
            join = StrokeJoin.Miter,
        )

        when (type) {
            MetroActionGlyphType.Upload -> {
                val w = size.width
                val h = size.height

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.82f),
                    end = Offset(w * 0.50f, h * 0.20f),
                    strokeWidth = stroke.width,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.20f),
                    end = Offset(w * 0.26f, h * 0.44f),
                    strokeWidth = stroke.width,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.20f),
                    end = Offset(w * 0.74f, h * 0.44f),
                    strokeWidth = stroke.width,
                )
            }

            MetroActionGlyphType.NewFolder -> {
                val w = size.width
                val h = size.height

                val folder = Path().apply {
                    moveTo(w * 0.10f, h * 0.30f)
                    lineTo(w * 0.38f, h * 0.30f)
                    lineTo(w * 0.47f, h * 0.40f)
                    lineTo(w * 0.90f, h * 0.40f)
                    lineTo(w * 0.90f, h * 0.80f)
                    lineTo(w * 0.10f, h * 0.80f)
                    close()
                }

                drawPath(
                    path = folder,
                    color = color,
                    style = stroke,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.49f),
                    end = Offset(w * 0.50f, h * 0.70f),
                    strokeWidth = stroke.width,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.39f, h * 0.595f),
                    end = Offset(w * 0.61f, h * 0.595f),
                    strokeWidth = stroke.width,
                )
            }

            MetroActionGlyphType.Files -> {
                val w = size.width
                val h = size.height

                // Folder bez plusa — ekran Pliki.
                drawLine(
                    color = color,
                    start = Offset(w * 0.14f, h * 0.30f),
                    end = Offset(w * 0.42f, h * 0.30f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.42f, h * 0.30f),
                    end = Offset(w * 0.50f, h * 0.40f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.40f),
                    end = Offset(w * 0.86f, h * 0.40f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.86f, h * 0.40f),
                    end = Offset(w * 0.80f, h * 0.78f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.80f, h * 0.78f),
                    end = Offset(w * 0.16f, h * 0.78f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.16f, h * 0.78f),
                    end = Offset(w * 0.14f, h * 0.30f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }

            MetroActionGlyphType.Recent -> {
                val w = size.width
                val h = size.height

                drawCircle(
                    color = color,
                    radius = w * 0.34f,
                    center = Offset(
                        w * 0.50f,
                        h * 0.50f,
                    ),
                    style = stroke,
                )

                // wskazówka godzinowa
                drawLine(
                    color = color,
                    start = Offset(
                        w * 0.50f,
                        h * 0.50f,
                    ),
                    end = Offset(
                        w * 0.50f,
                        h * 0.30f,
                    ),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )

                // wskazówka minutowa
                drawLine(
                    color = color,
                    start = Offset(
                        w * 0.50f,
                        h * 0.50f,
                    ),
                    end = Offset(
                        w * 0.66f,
                        h * 0.58f,
                    ),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }


            MetroActionGlyphType.Home -> {
                val w = size.width
                val h = size.height
                val lineWidth = 2.2.dp.toPx()

                // Dach
                drawLine(
                    color = color,
                    start = Offset(w * 0.18f, h * 0.48f),
                    end = Offset(w * 0.50f, h * 0.18f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.18f),
                    end = Offset(w * 0.82f, h * 0.48f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )

                // Ściany
                drawLine(
                    color = color,
                    start = Offset(w * 0.25f, h * 0.42f),
                    end = Offset(w * 0.25f, h * 0.82f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.75f, h * 0.42f),
                    end = Offset(w * 0.75f, h * 0.82f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.25f, h * 0.82f),
                    end = Offset(w * 0.75f, h * 0.82f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )
            }

            MetroActionGlyphType.Add -> {
                val w = size.width
                val h = size.height
                val lineWidth = 2.2.dp.toPx()

                drawLine(
                    color = color,
                    start = Offset(w * 0.50f, h * 0.24f),
                    end = Offset(w * 0.50f, h * 0.76f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )

                drawLine(
                    color = color,
                    start = Offset(w * 0.24f, h * 0.50f),
                    end = Offset(w * 0.76f, h * 0.50f),
                    strokeWidth = lineWidth,
                    cap = StrokeCap.Butt,
                )
            }

            MetroActionGlyphType.More -> {
                val w = size.width
                val h = size.height

                drawCircle(
                    color = color,
                    radius = w * 0.065f,
                    center = Offset(
                        w * 0.25f,
                        h * 0.50f,
                    ),
                )

                drawCircle(
                    color = color,
                    radius = w * 0.065f,
                    center = Offset(
                        w * 0.50f,
                        h * 0.50f,
                    ),
                )

                drawCircle(
                    color = color,
                    radius = w * 0.065f,
                    center = Offset(
                        w * 0.75f,
                        h * 0.50f,
                    ),
                )
            }

}
    }
}
