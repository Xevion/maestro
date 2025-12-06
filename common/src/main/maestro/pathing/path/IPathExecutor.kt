package maestro.pathing.path

import maestro.pathing.calc.IPath

interface IPathExecutor {
    val path: IPath
    val position: Int
}
