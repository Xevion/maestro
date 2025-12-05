@file:Suppress("unused")

package maestro.api.utils

import maestro.api.pathing.goals.Goal
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Format BlockPos as compact coordinate string.
 * Example: BlockPos(100, 64, 200).format() → "100,64,200"
 */
fun BlockPos.format(): String = "$x,$y,$z"

/**
 * Format PackedBlockPos as compact coordinate string.
 * Example: PackedBlockPos(100, 64, 200).format() → "100,64,200"
 */
fun PackedBlockPos.format(): String = "$x,$y,$z"

/**
 * Format Vec3 as compact coordinate string with 3 decimal precision.
 * Example: Vec3(100.5, 64.0, 200.25).format() → "100.500,64.000,200.250"
 */
fun Vec3.format(): String = "${x.format()},${y.format()},${z.format()}"

/**
 * Format floating point number with 3 decimal precision.
 *
 * Use for critical user-facing values (distances, speeds, percentages). For debug-level
 * internal calculations, prefer raw values to avoid formatting overhead.
 *
 * Examples: 6.634636.format() → "6.635", 0.000123.format() → "0.000", 123.456789.format() → "123.457"
 */
fun Double.format(): String = "%.3f".format(this)

/**
 * Format Goal for logging with smart truncation.
 *
 * Goal types already implement toString() for compact output, so this method primarily
 * serves as a documentation point and future extension hook.
 */
fun Goal.format(): String = toString()
