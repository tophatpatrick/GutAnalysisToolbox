package services.merge;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class DirectoryUtils {
    private DirectoryUtils() {}

    public static Optional<Path> firstSubdirectory(Path root) throws IOException {
        DirectoryStream<Path> ds = null;
        try {
            ds = Files.newDirectoryStream(root);
            for (Path p : ds) {
                if (Files.isDirectory(p)) return Optional.of(p);
            }
            return Optional.empty();
        } finally {
            if (ds != null) try { ds.close(); } catch (IOException ignored) {}
        }
    }

    public static Set<String> collectCsvBaseNames(Path start) throws IOException {
        java.util.stream.Stream<Path> stream = null;
        try {
            stream = Files.walk(start);
            return stream
                    .filter(new java.util.function.Predicate<Path>() {
                        @Override public boolean test(Path p) { return Files.isRegularFile(p); }
                    })
                    .map(new java.util.function.Function<Path, String>() {
                        @Override public String apply(Path p) { return p.getFileName().toString(); }
                    })
                    .filter(new java.util.function.Predicate<String>() {
                        @Override public boolean test(String name) {
                            return name.toLowerCase(Locale.ROOT).endsWith(".csv");
                        }
                    })
                    .collect(Collectors.toCollection(new java.util.function.Supplier<LinkedHashSet<String>>() {
                        @Override public LinkedHashSet<String> get() { return new LinkedHashSet<String>(); }
                    }));
        } finally {
            if (stream != null) stream.close();
        }
    }

    public static String nameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot <= 0) ? name : name.substring(0, dot);
    }
}
