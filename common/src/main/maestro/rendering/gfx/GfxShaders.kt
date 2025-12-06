package maestro.rendering.gfx

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderDefines
import net.minecraft.client.renderer.ShaderProgram
import net.minecraft.resources.ResourceLocation

/**
 * Registry of SDF-based shader programs for high-quality anti-aliased rendering.
 *
 * All shaders use POSITION_TEX_COLOR format where:
 * - Position: World-space vertex position
 * - UV/Tex: Encodes shape-space coordinates for SDF evaluation
 * - Color: Per-vertex RGBA color
 *
 * SDF Rendering Approach:
 * 1. Geometry is expanded into camera-facing quads (or planar quads for flat shapes)
 * 2. UV coordinates encode position within the shape's local space
 * 3. Fragment shader evaluates SDF function to determine inside/outside
 * 4. fwidth() provides screen-space derivatives for antialiasing
 * 5. smoothstep() creates smooth edge transitions
 */
object GfxShaders {
    /**
     * SDF line shader with fwidth-based antialiasing.
     *
     * UV encoding:
     * - U: Position along line (0 = start, 1 = end)
     * - V: Perpendicular distance from center (-1 to 1, where |V|=1 is edge)
     *
     * The fragment shader computes: alpha = smoothstep(1.0, 1.0 - fwidth(|V|), |V|)
     */
    val LINE: ShaderProgram =
        ShaderProgram(
            ResourceLocation.parse("maestro:core/gfx_line"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY,
        )

    /**
     * SDF filled quad shader with edge antialiasing.
     *
     * UV encoding:
     * - U: Horizontal position (-1 to 1)
     * - V: Vertical position (-1 to 1)
     *
     * For basic filled quads, this provides edge AA via:
     * sdf = max(|U| - 1, |V| - 1)
     * alpha = smoothstep(0, -fwidth(sdf), sdf)
     */
    val QUAD: ShaderProgram =
        ShaderProgram(
            ResourceLocation.parse("maestro:core/gfx_quad"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY,
        )

    /**
     * SDF circle/disc shader with radial antialiasing.
     *
     * UV encoding:
     * - UV encodes position in unit circle space
     * - length(UV) < 1.0 is inside the circle
     * - Expanded to ~1.1 for AA padding
     *
     * Fragment shader: sdf = length(UV) - 1.0
     * Anti-aliasing: alpha = smoothstep(fwidth(sdf), -fwidth(sdf), sdf)
     *
     * For rings, inner radius is encoded in the vertex data.
     */
    val CIRCLE: ShaderProgram =
        ShaderProgram(
            ResourceLocation.parse("maestro:core/gfx_circle"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY,
        )

    /**
     * SDF polyline shader with support for joins and end caps.
     *
     * UV encoding:
     * - U: Position along line segment (0 = start, 1 = end)
     * - V: Perpendicular distance from center (-1 to 1, where |V|=1 is edge)
     *
     * For joins and caps, UV encodes SDF coordinates for smooth antialiasing.
     * The fragment shader evaluates the SDF and applies fwidth-based AA.
     */
    val POLYLINE: ShaderProgram =
        ShaderProgram(
            ResourceLocation.parse("maestro:core/gfx_polyline"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY,
        )
}
