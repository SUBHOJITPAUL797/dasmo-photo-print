package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 3D Claymorphic Card inspired by Swiggy / Blinkit tactile consumer UI.
 * Combines soft rounded squircle geometry, a diagonal 3D surface tint,
 * a top-left specular inner highlight, and a bottom-right depth rim.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.65f),
            accentColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.45f)
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 900f)
    )
    val surfaceBrush = Brush.linearGradient(
        colors = listOf(
            containerColor,
            containerColor,
            accentColor.copy(alpha = 0.07f)
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 1000f)
    )

    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.28f),
                spotColor = accentColor.copy(alpha = 0.32f)
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(width = 1.5.dp, brush = rimBrush, shape = shape),
        shape = shape,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(surfaceBrush)
                .drawWithContent {
                    drawContent()
                    // Subtle top-left inner 3D clay highlight
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.5f, size.height * 0.5f)
                        ),
                        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                },
            content = content
        )
    }
}

/**
 * Puffy 3D Clay Icon Orb with volumetric diagonal shading and glossy top specular rim.
 */
@Composable
fun ClayIconOrb(
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 26.dp,
    cornerRadius: Dp = 18.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.45f),
                spotColor = accentColor.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.85f),
                        accentColor,
                        accentColor.copy(
                            red = (accentColor.red * 0.78f).coerceIn(0f, 1f),
                            green = (accentColor.green * 0.78f).coerceIn(0f, 1f),
                            blue = (accentColor.blue * 0.78f).coerceIn(0f, 1f)
                        )
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(140f, 140f)
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.1f),
                        Color.Black.copy(alpha = 0.18f)
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glossy top-left clay highlight bubble
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 5.dp, top = 4.dp)
                .size(width = size * 0.42f, height = size * 0.16f)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color.Transparent
                        )
                    )
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 3D Clay Image Thumbnail Badge (wraps bundled 3D clay artwork in a tactile squircle frame).
 */
@Composable
fun ClayArtworkBadge(
    @DrawableRes drawableRes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    cornerRadius: Dp = 18.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.4f),
                spotColor = accentColor.copy(alpha = 0.5f)
            )
            .clip(shape)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        accentColor.copy(alpha = 0.45f)
                    )
                ),
                shape = shape
            )
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Swiggy / Blinkit-inspired Quick Action Bento Tile with 3D Clay visual depth.
 */
@Composable
fun ClayQuickBentoTile(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    icon: ImageVector,
    @DrawableRes artworkRes: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        accentColor = accentColor,
        cornerRadius = 22.dp,
        elevation = 5.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (artworkRes != null) {
                    ClayArtworkBadge(
                        drawableRes = artworkRes,
                        accentColor = accentColor,
                        size = 48.dp,
                        cornerRadius = 15.dp
                    )
                } else {
                    ClayIconOrb(
                        icon = icon,
                        accentColor = accentColor,
                        size = 46.dp,
                        iconSize = 23.dp,
                        cornerRadius = 15.dp
                    )
                }

                ClayPillBadge(
                    text = badgeText,
                    accentColor = accentColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Glossy 3D Clay Pill Tag
 */
@Composable
fun ClayPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = modifier,
        shape = shape,
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.6f),
                    accentColor.copy(alpha = 0.45f)
                )
            )
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 9.sp,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
