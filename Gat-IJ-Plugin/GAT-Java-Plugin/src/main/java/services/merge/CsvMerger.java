package services.merge;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.Iterator;
import java.util.Locale;

public class CsvMerger {

    public static final String MERGED_PREFIX = "Merged_";
    private final Charset charset;
    private final LabelStrategy labelStrategy;
    private final boolean quoteLabelIfComma;
    private final boolean verifyHeaderMatch;

    public CsvMerger(LabelStrategy labelStrategy) {
        this(labelStrategy, StandardCharsets.UTF_8, true, false);
    }

    public CsvMerger(LabelStrategy labelStrategy,
                     Charset charset,
                     boolean quoteLabelIfComma,
                     boolean verifyHeaderMatch) {
        this.labelStrategy = labelStrategy;
        this.charset = charset;
        this.quoteLabelIfComma = quoteLabelIfComma;
        this.verifyHeaderMatch = verifyHeaderMatch;
    }

    /** Macro A: merge files whose filename CONTAINS the given pattern. */
    public Path mergeSinglePattern(Path root, String namePattern) throws IOException, MergeException {
        if (root == null || namePattern == null) throw new IllegalArgumentException("null args");
        if (!Files.isDirectory(root)) throw new MergeException("Root is not a directory: " + root);

        String experiment = DirectoryUtils.nameWithoutExtension(root.getFileName().toString());
        Path output = root.resolve(MERGED_PREFIX + experiment + "_" + namePattern);

        if (Files.exists(output)) {
            throw new MergeException("Output already exists: " + output);
        }

        final String[] headerHolder = new String[1];

        BufferedWriter w = null;
        try {
            w = Files.newBufferedWriter(output, charset, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            final Predicate<Path> fileFilter = new Predicate<Path>() {
                @Override public boolean test(Path p) {
                    String name = p.getFileName().toString();
                    return name.toLowerCase(Locale.ROOT).endsWith(".csv")
                            && name.contains(namePattern)
                            && !name.startsWith(MERGED_PREFIX);
                }
            };

            traverseAndAppend(root, fileFilter, headerHolder, w);
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(output); } catch (IOException ignored) {}
            throw e;
        } finally {
            if (w != null) try { w.close(); } catch (IOException ignored) {}
        }

        return output;
    }

    /** Macro B: detect CSV names from first subfolder and merge each across the tree. */
    public List<Path> mergeFromFirstSubfolderPatterns(Path root) throws IOException, MergeException {
        if (root == null) throw new IllegalArgumentException("null root");
        if (!Files.isDirectory(root)) throw new MergeException("Root is not a directory: " + root);

        Optional<Path> firstOpt = DirectoryUtils.firstSubdirectory(root);
        if (!firstOpt.isPresent()) throw new MergeException("No subfolders found in " + root);

        Path first = firstOpt.get();
        Set<String> baseNames = DirectoryUtils.collectCsvBaseNames(first);
        if (baseNames.isEmpty()) throw new MergeException("No CSV files found under: " + first);

        String experiment = DirectoryUtils.nameWithoutExtension(root.getFileName().toString());

        // Pre-check outputs
        List<Path> outputs = new ArrayList<Path>();
        for (String base : baseNames) {
            Path out = root.resolve(MERGED_PREFIX + experiment + "_" + base);
            if (Files.exists(out)) {
                throw new MergeException("Merged file already exists (remove it first): " + out);
            }
            outputs.add(out);
        }

        List<Path> written = new ArrayList<Path>();
        int idx = 0;
        for (String base : baseNames) {
            Path out = outputs.get(idx++);
            final String[] headerHolder = new String[1];

            BufferedWriter w = null;
            try {
                w = Files.newBufferedWriter(out, charset, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

                final String baseLower = base.toLowerCase(Locale.ROOT);
                final Predicate<Path> fileFilter = new Predicate<Path>() {
                    @Override public boolean test(Path p) {
                        String name = p.getFileName().toString();
                        return name.toLowerCase(Locale.ROOT).equals(baseLower) && !name.startsWith(MERGED_PREFIX);
                    }
                };

                traverseAndAppend(root, fileFilter, headerHolder, w);
                written.add(out);
            } catch (IOException | RuntimeException e) {
                try { Files.deleteIfExists(out); } catch (IOException ignored) {}
                throw e;
            } finally {
                if (w != null) try { w.close(); } catch (IOException ignored) {}
            }
        }

        return written;
    }

    private void traverseAndAppend(Path root,
                                   Predicate<Path> fileFilter,
                                   String[] headerHolder,
                                   BufferedWriter writer) throws IOException, MergeException {

        // Java 8-friendly Files.walk usage
        java.util.stream.Stream<Path> stream = null;
        try {
            stream = Files.walk(root);
            Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (!Files.isRegularFile(p)) continue;
                if (!fileFilter.test(p)) continue;
                appendCsvFile(root, p, headerHolder, writer);
            }
        } finally {
            if (stream != null) stream.close();
        }
    }

    private void appendCsvFile(Path root,
                               Path csvFile,
                               String[] headerHolder,
                               BufferedWriter writer) throws IOException, MergeException {

        String label = labelStrategy.labelFor(root, csvFile);
        boolean labelHasComma = label.indexOf(',') >= 0;

        BufferedReader r = null;
        try {
            r = Files.newBufferedReader(csvFile, charset);
            String raw;
            int lineIdx = 0;

            while ((raw = r.readLine()) != null) {
                // Java 8: use trim() (strip() is Java 11)
                String line = raw.trim();
                if (line.isEmpty()) continue;

                if (lineIdx == 0) {
                    if (headerHolder[0] == null) {
                        headerHolder[0] = line;
                        writer.write("Experiment," + line);
                        writer.newLine();
                    } else if (verifyHeaderMatch && !headerHolder[0].equals(line)) {
                        throw new MergeException("Header mismatch in " + csvFile +
                                "\nExpected: " + headerHolder[0] + "\nFound: " + line);
                    }
                } else {
                    String exp = label;
                    if (quoteLabelIfComma && labelHasComma) {
                        exp = "\"" + label.replace("\"", "\"\"") + "\"";
                    }
                    writer.write(exp);
                    writer.write(",");
                    writer.write(line);
                    writer.newLine();
                }
                lineIdx++;
            }
        } finally {
            if (r != null) try { r.close(); } catch (IOException ignored) {}
        }
    }
}
