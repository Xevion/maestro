package maestro.api.process;

import java.nio.file.Path;

public interface IExploreProcess extends IMaestroProcess {

    void explore(int centerX, int centerZ);

    void applyJsonFilter(Path path, boolean invert) throws Exception;
}
