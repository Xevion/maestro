package maestro.api.command.datatypes;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import net.minecraft.client.Minecraft;

public enum RelativeFile implements IDatatypePost<File, File> {
    INSTANCE;

    @Override
    public File apply(IDatatypeContext ctx, File original) throws CommandException {
        if (original == null) {
            original = new File("./");
        }

        Path path;
        try {
            path = FileSystems.getDefault().getPath(ctx.getConsumer().getString());
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("invalid path");
        }
        return getCanonicalFileUnchecked(original.toPath().resolve(path).toFile());
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        return Stream.empty();
    }

    /**
     * Seriously
     *
     * @param file File
     * @return Canonical file of file
     */
    private static File getCanonicalFileUnchecked(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Stream<String> tabComplete(IArgConsumer consumer, File base0)
            throws CommandException {
        // I will not make the caller deal with this, seriously
        File base = getCanonicalFileUnchecked(base0);
        String currentPathStringThing = consumer.getString();
        Path currentPath = FileSystems.getDefault().getPath(currentPathStringThing);
        Path basePath = currentPath.isAbsolute() ? currentPath.getRoot() : base.toPath();
        boolean useParent =
                !currentPathStringThing.isEmpty()
                        && !currentPathStringThing.endsWith(File.separator);
        File currentFile =
                currentPath.isAbsolute()
                        ? currentPath.toFile()
                        : new File(base, currentPathStringThing);
        return Stream.of(
                        Objects.requireNonNull(
                                getCanonicalFileUnchecked(
                                                useParent
                                                        ? currentFile.getParentFile()
                                                        : currentFile)
                                        .listFiles()))
                .map(
                        f ->
                                (currentPath.isAbsolute()
                                                ? f
                                                : basePath.relativize(f.toPath()).toString())
                                        + (f.isDirectory() ? File.separator : ""))
                .filter(
                        s ->
                                s.toLowerCase(Locale.US)
                                        .startsWith(currentPathStringThing.toLowerCase(Locale.US)))
                .filter(s -> !s.contains(" "));
    }

    public static File gameDir(Minecraft mc) {
        File gameDir = mc.gameDirectory.getAbsoluteFile();
        if (gameDir.getName().equals(".")) {
            return gameDir.getParentFile();
        }
        return gameDir;
    }
}
