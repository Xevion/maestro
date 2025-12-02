package maestro.api.pathing.path

import maestro.api.pathing.calc.IPath

interface IPathExecutor {
    val path: IPath
    val position: Int
}
