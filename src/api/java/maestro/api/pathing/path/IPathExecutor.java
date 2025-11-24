package maestro.api.pathing.path;

import maestro.api.pathing.calc.IPath;

public interface IPathExecutor {

    IPath getPath();

    int getPosition();
}
