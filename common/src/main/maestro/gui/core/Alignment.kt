package maestro.gui.core

/**
 * Horizontal alignment within a cell.
 */
enum class AlignmentX {
    LEFT,
    CENTER,
    RIGHT,
}

/**
 * Vertical alignment within a cell.
 */
enum class AlignmentY {
    TOP,
    CENTER,
    BOTTOM,
}

/**
 * Combined horizontal and vertical alignment.
 */
data class Alignment(
    val x: AlignmentX = AlignmentX.LEFT,
    val y: AlignmentY = AlignmentY.TOP,
) {
    companion object {
        val TOP_LEFT = Alignment(AlignmentX.LEFT, AlignmentY.TOP)
        val TOP_CENTER = Alignment(AlignmentX.CENTER, AlignmentY.TOP)
        val TOP_RIGHT = Alignment(AlignmentX.RIGHT, AlignmentY.TOP)
        val CENTER_LEFT = Alignment(AlignmentX.LEFT, AlignmentY.CENTER)
        val CENTER = Alignment(AlignmentX.CENTER, AlignmentY.CENTER)
        val CENTER_RIGHT = Alignment(AlignmentX.RIGHT, AlignmentY.CENTER)
        val BOTTOM_LEFT = Alignment(AlignmentX.LEFT, AlignmentY.BOTTOM)
        val BOTTOM_CENTER = Alignment(AlignmentX.CENTER, AlignmentY.BOTTOM)
        val BOTTOM_RIGHT = Alignment(AlignmentX.RIGHT, AlignmentY.BOTTOM)
    }
}
