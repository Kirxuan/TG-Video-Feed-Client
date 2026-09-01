package com.qixuan.channelvideoflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTokens
import com.qixuan.channelvideoflow.ui.theme.glossColors

@Composable
fun PremiumBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = glossColors
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawWithCache {
                    val background = Brush.linearGradient(
                        colors = listOf(
                            colors.backdropStart,
                            colors.backdropMiddle,
                            colors.backdropEnd,
                        ),
                    )
                    val glow = Brush.radialGradient(
                        colors = listOf(colors.accentGlow, Color.Transparent),
                        radius = size.minDimension * 0.92f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, 0f),
                    )
                    val secondaryGlow = Brush.radialGradient(
                        colors = listOf(
                            colors.accentGlow.copy(alpha = colors.accentGlow.alpha * 0.48f),
                            Color.Transparent,
                        ),
                        radius = size.minDimension * 0.78f,
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.92f,
                            size.height * 0.74f,
                        ),
                    )
                    onDrawBehind {
                        drawRect(background)
                        drawRect(glow)
                        drawRect(secondaryGlow)
                    }
                },
            content = content,
        )
    }
}

@Composable
fun GlossCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = ChannelVideoFlowTokens.Shapes.large,
    contentPadding: PaddingValues = PaddingValues(ChannelVideoFlowTokens.Spacing.large),
    showGlossHighlight: Boolean = true,
    enabled: Boolean = true,
    selected: Boolean = false,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    stateDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = glossColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null) {
            ChannelVideoFlowTokens.Motion.pressedScale
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = if (pressed) {
                ChannelVideoFlowTokens.Motion.pressInMillis
            } else {
                ChannelVideoFlowTokens.Motion.pressOutMillis
            },
        ),
        label = "gloss card press",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
            else -> colors.surface
        },
        animationSpec = tween(ChannelVideoFlowTokens.Motion.stateChangeMillis),
        label = "gloss card surface",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.62f)
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
            else -> colors.border
        },
        animationSpec = tween(ChannelVideoFlowTokens.Motion.stateChangeMillis),
        label = "gloss card border",
    )
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            role = role,
            onClick = onClick,
        )
    }
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.48f
            }
            .clip(shape)
            .then(interactionModifier)
            .semantics {
                if (selected) this.selected = true
                if (!enabled) disabled()
                stateDescription?.let { this.stateDescription = it }
            },
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(ChannelVideoFlowTokens.Elevation.border, borderColor),
        shadowElevation = ChannelVideoFlowTokens.Elevation.card,
    ) {
        val glossModifier = if (showGlossHighlight) {
            Modifier.drawWithCache {
                val highlight = Brush.verticalGradient(
                    colors = listOf(colors.highlight.copy(alpha = 0.32f), Color.Transparent),
                    endY = size.height * 0.34f,
                )
                onDrawBehind { drawRect(highlight) }
            }
        } else {
            Modifier
        }
        Column(
            modifier = glossModifier
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.medium),
            content = content,
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlossCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
fun PremiumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChannelVideoFlowTokens.Spacing.large, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.small),
    ) {
        navigation?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = ChannelVideoFlowTokens.Shapes.pill,
        color = color.copy(alpha = if (selected) 0.18f else 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.52f else 0.24f)),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(ChannelVideoFlowTokens.Motion.stateChangeMillis)) togetherWith
                    fadeOut(tween(ChannelVideoFlowTokens.Motion.stateChangeMillis))
            },
            label = "status pill content",
        ) { currentText ->
            Text(
                text = if (selected) "✓  $currentText" else currentText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                color = color,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GlossActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) ChannelVideoFlowTokens.Motion.pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                ChannelVideoFlowTokens.Motion.pressInMillis
            } else {
                ChannelVideoFlowTokens.Motion.pressOutMillis
            },
        ),
        label = "action pill press",
    )
    val colors = glossColors
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.46f
            }
            .clip(ChannelVideoFlowTokens.Shapes.pill)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        shape = ChannelVideoFlowTokens.Shapes.pill,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else colors.surfaceStrong,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun GlossQuickAction(
    text: String,
    icon: Painter,
    iconContentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledStateDescription: String? = null,
    accentColor: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) ChannelVideoFlowTokens.Motion.pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                ChannelVideoFlowTokens.Motion.pressInMillis
            } else {
                ChannelVideoFlowTokens.Motion.pressOutMillis
            },
        ),
        label = "quick action press",
    )
    val colors = glossColors
    val contentColor = accentColor ?: MaterialTheme.colorScheme.onSurface
    val borderColor = accentColor?.copy(alpha = 0.36f) ?: colors.border
    Surface(
        modifier = modifier
            .height(ChannelVideoFlowTokens.Sizes.quickAction)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.44f
            }
            .clip(ChannelVideoFlowTokens.Shapes.control)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                if (!enabled) {
                    disabled()
                    disabledStateDescription?.let { stateDescription = it }
                }
            },
        shape = ChannelVideoFlowTokens.Shapes.control,
        color = colors.surfaceStrong,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(ChannelVideoFlowTokens.Sizes.icon),
                tint = contentColor,
            )
            Text(
                text = text,
                modifier = Modifier.padding(top = 3.dp),
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun GlossSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    searchIcon: Painter,
    searchIconContentDescription: String,
    clearIcon: Painter,
    clearIconContentDescription: String,
    modifier: Modifier = Modifier,
    clearButtonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    onSearch: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = glossColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = when {
                    !enabled -> "搜索不可用"
                    isError -> "搜索输入有误"
                    focused -> "搜索输入框已聚焦"
                    value.isNotEmpty() -> "已输入搜索条件"
                    else -> "搜索输入框"
                }
            },
        enabled = enabled,
        isError = isError,
        interactionSource = interaction,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = searchIcon,
                contentDescription = searchIconContentDescription,
                modifier = Modifier.size(ChannelVideoFlowTokens.Sizes.icon),
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = clearButtonModifier,
                ) {
                    Icon(
                        painter = clearIcon,
                        contentDescription = clearIconContentDescription,
                        modifier = Modifier.size(ChannelVideoFlowTokens.Sizes.icon),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        supportingText = supportingText?.let { text ->
            { Text(text) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            },
        ),
        shape = ChannelVideoFlowTokens.Shapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceStrong.copy(alpha = 0.96f),
            unfocusedContainerColor = colors.surface.copy(alpha = 0.92f),
            disabledContainerColor = colors.surface.copy(alpha = 0.58f),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.46f),
        ),
    )
}

enum class PrimaryActionState {
    Idle,
    Loading,
    Success,
    Error,
}

@Composable
fun StatefulPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PrimaryActionState = PrimaryActionState.Idle,
    stateText: String? = null,
    progressIndicatorModifier: Modifier = Modifier,
) {
    val actionable = enabled && state != PrimaryActionState.Loading
    Button(
        onClick = onClick,
        modifier = modifier
            .height(ChannelVideoFlowTokens.Sizes.primaryAction)
            .semantics {
                stateDescription = stateText ?: when (state) {
                    PrimaryActionState.Idle -> if (enabled) "可操作" else "不可操作"
                    PrimaryActionState.Loading -> "正在处理"
                    PrimaryActionState.Success -> "操作成功"
                    PrimaryActionState.Error -> "操作失败，可重试"
                }
            },
        enabled = actionable,
        shape = ChannelVideoFlowTokens.Shapes.control,
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(ChannelVideoFlowTokens.Motion.stateChangeMillis)) togetherWith
                    fadeOut(tween(ChannelVideoFlowTokens.Motion.stateChangeMillis))
            },
            contentAlignment = Alignment.Center,
            label = "primary action state",
        ) { currentState ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (currentState) {
                    PrimaryActionState.Loading -> CircularProgressIndicator(
                        modifier = progressIndicatorModifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    PrimaryActionState.Success -> Text("✓")
                    PrimaryActionState.Error -> Text("!")
                    PrimaryActionState.Idle -> Unit
                }
                Text(
                    text = stateText ?: text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BottomPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusText: String? = null,
    state: PrimaryActionState = PrimaryActionState.Idle,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = glossColors.surfaceStrong.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .imePadding()
                .padding(horizontal = ChannelVideoFlowTokens.Spacing.large)
                .padding(top = ChannelVideoFlowTokens.Spacing.small, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.xSmall),
        ) {
            statusText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatefulPrimaryButton(
                text = text,
                onClick = onClick,
                modifier = buttonModifier
                    .fillMaxWidth(),
                enabled = enabled,
                state = state,
                stateText = statusText,
            )
        }
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val colors = glossColors
    val safeSelectedIndex = selectedIndex.coerceIn(options.indices)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceStrong, ChannelVideoFlowTokens.Shapes.pill)
            .height(56.dp),
    ) {
        val indicatorWidth = (maxWidth - 8.dp) / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = 4.dp + indicatorWidth * safeSelectedIndex,
            animationSpec = tween(ChannelVideoFlowTokens.Motion.surfaceMillis),
            label = "segmented indicator",
        )
        Surface(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .padding(vertical = 4.dp)
                .width(indicatorWidth)
                .fillMaxHeight(),
            shape = ChannelVideoFlowTokens.Shapes.pill,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
            ),
            shadowElevation = 1.dp,
            content = {},
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ChannelVideoFlowTokens.Shapes.pill)
                        .selectable(
                            selected = safeSelectedIndex == index,
                            role = Role.Tab,
                        ) { onSelected(index) }
                        .semantics {
                            role = Role.Tab
                            selected = safeSelectedIndex == index
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                        color = if (safeSelectedIndex == index) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun StatePanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    GlossCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp),
        isError = isError,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Preview(showBackground = true)
@Composable
private fun GlossComponentsPreview() {
    ChannelVideoFlowTheme {
        PremiumBackdrop {
            GlossCard(Modifier.padding(24.dp).align(Alignment.Center)) {
                Text("VELORA", style = MaterialTheme.typography.titleLarge)
                StatusPill("已选择 3 个频道", selected = true)
                SegmentedControl(listOf("任一标签", "全部标签"), 0, {})
            }
        }
    }
}
