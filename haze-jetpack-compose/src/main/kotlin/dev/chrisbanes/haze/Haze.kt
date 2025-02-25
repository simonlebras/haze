// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Stable
class HazeState {
  /**
   * The areas which are blurred by any [Modifier.haze] instances which use this state.
   */
  private val _areas = mutableStateListOf<HazeArea>()

  val areas: List<HazeArea> get() = _areas.toList()

  fun registerArea(area: HazeArea) {
    _areas.add(area)
  }

  fun unregisterArea(area: HazeArea) {
    _areas.remove(area)
  }
}

internal fun Path.addOutline(outline: Outline, offset: Offset) = when (outline) {
  is Outline.Rectangle -> addRect(outline.rect.translate(offset))
  is Outline.Rounded -> addRoundRect(outline.roundRect.translate(offset))
  is Outline.Generic -> addPath(outline.path, offset)
}

@Stable
class HazeArea(
  size: Size = Size.Unspecified,
  positionOnScreen: Offset = Offset.Unspecified,
  shape: Shape = RectangleShape,
  tint: Color = Color.Unspecified,
) {
  var size: Size by mutableStateOf(size)
    internal set

  var positionOnScreen: Offset by mutableStateOf(positionOnScreen)
    internal set

  var shape: Shape by mutableStateOf(shape)
    internal set

  var tint: Color by mutableStateOf(tint)
    internal set

  val isValid: Boolean
    get() = size.isSpecified && positionOnScreen.isSpecified && !size.isEmpty()
}

internal fun HazeArea.boundsInLocal(position: Offset): Rect? {
  if (!isValid) return null
  if (position.isUnspecified) return null

  return size.toRect().translate(positionOnScreen - position)
}

internal fun HazeArea.updatePath(
  path: Path,
  area: Rect,
  layoutDirection: LayoutDirection,
  density: Density,
) {
  path.reset()
  path.addOutline(
    outline = shape.createOutline(size, layoutDirection, density),
    offset = area.topLeft,
  )
}

/**
 * Draw content within the provided [HazeState.areas] blurred in a 'glassmorphism' style.
 *
 * When running on Android 12 devicees (and newer), usage of this API renders the corresponding composable
 * into a separate graphics layer. On older Android platforms, a translucent scrim will be drawn
 * instead.
 *
 * @param backgroundColor Background color of the content. Typically you would provide
 * `MaterialTheme.colorScheme.surface` or similar.
 * @param tint Default color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content. Can be overridden by the `tint` parameter on [hazeChild].
 * @param blurRadius Radius of the blur.
 * @param noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 */
fun Modifier.haze(
  state: HazeState,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
  noiseFactor: Float = HazeDefaults.noiseFactor,
): Modifier = this then HazeNodeElement(
  state = state,
  tint = tint,
  backgroundColor = backgroundColor,
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
)

/**
 * Default values for the [haze] modifiers.
 */
@Suppress("ktlint:standard:property-naming")
object HazeDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  val blurRadius: Dp = 20.dp

  /**
   * Noise factor.
   */
  const val noiseFactor = 0.15f

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  val tintAlpha: Float = 0.7f

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  fun tint(color: Color): Color = color.copy(alpha = tintAlpha)
}

internal data class HazeNodeElement(
  val state: HazeState,
  val backgroundColor: Color,
  val tint: Color,
  val blurRadius: Dp,
  val noiseFactor: Float,
) : ModifierNodeElement<HazeNode>() {
  override fun create(): HazeNode = createHazeNode(
    state = state,
    backgroundColor = backgroundColor,
    tint = tint,
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
  )

  override fun update(node: HazeNode) {
    node.state = state
    node.backgroundColor = backgroundColor
    node.defaultTint = tint
    node.blurRadius = blurRadius
    node.noiseFactor = noiseFactor
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "haze"
    properties["backgroundColor"] = backgroundColor
    properties["tint"] = tint
    properties["blurRadius"] = blurRadius
    properties["noiseFactor"] = noiseFactor
  }
}

internal abstract class HazeNode(
  var state: HazeState,
  var backgroundColor: Color,
  var defaultTint: Color,
  var blurRadius: Dp,
  var noiseFactor: Float,
) : Modifier.Node() {
  open fun onUpdate() {}
}
