package UI.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;   // Java 8 friendly
import java.util.Properties;

public final class GatSettings {
    // Java 8: use Paths.get(...)
    private static final Path DEFAULT_PATH = Paths.get(
            System.getProperty("user.home"), ".gat", "settings.properties");

    public Double huTrainingRescale;   // e.g. 1.00
    public Double huProb;              // 0..1
    public Double huNms;               // 0..1
    public Double subtypeProb;         // 0..1
    public Double subtypeNms;          // 0..1
    public Double gangliaExpandUm;     // microns
    public Double overlapFrac;         // 0..1

    private final Path path;

    private GatSettings(Path path) { this.path = path; }

    public static GatSettings loadOrDefaults() {
        String env = System.getenv("GAT_SETTINGS");
        // Java 8 friendly “blank” check
        boolean hasEnv = env != null && !env.trim().isEmpty();
        Path p = hasEnv ? new File(env).toPath() : DEFAULT_PATH;

        GatSettings s = new GatSettings(p);
        s.load(); // harmless if file missing
        return s;
    }

    // -------- export-only API (use these from TuningTools) --------
    public Properties toProperties() {
        Properties pr = new Properties();
        put(pr, "huTrainingRescale", huTrainingRescale);
        put(pr, "huProb",            huProb);
        put(pr, "huNms",             huNms);
        put(pr, "subtypeProb",       subtypeProb);
        put(pr, "subtypeNms",        subtypeNms);
        put(pr, "gangliaExpandUm",   gangliaExpandUm);
        put(pr, "overlapFrac",       overlapFrac);
        return pr;
    }

    public void saveTo(Path dest) throws IOException {
        if (dest.getParent() != null) Files.createDirectories(dest.getParent());
        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(dest.toFile().toPath()), StandardCharsets.UTF_8)) {
            toProperties().store(w, "GAT tuning config");
        }
    }

    // convenience setters
    public void setHuTrainingRescale(double v){ this.huTrainingRescale = v; }
    public void setHuProb(double v){ this.huProb = v; }
    public void setSubtypeProb(double v){ this.subtypeProb = v; }
    public void setGangliaExpandUm(double v){ this.gangliaExpandUm = v; }
    public void setOverlapFrac(double v){ this.overlapFrac = v; }

    // ----------------- internal load (optional) -------------------
    private void load() {
        if (path == null || !Files.isRegularFile(path)) return;
        Properties pr = new Properties();
        try (Reader r = new InputStreamReader(
                Files.newInputStream(path.toFile().toPath()), StandardCharsets.UTF_8)) {
            pr.load(r);
        } catch (IOException ignored) { }
        huTrainingRescale = getDouble(pr, "huTrainingRescale");
        huProb            = getDouble(pr, "huProb");
        huNms             = getDouble(pr, "huNms");
        subtypeProb       = getDouble(pr, "subtypeProb");
        subtypeNms        = getDouble(pr, "subtypeNms");
        gangliaExpandUm   = getDouble(pr, "gangliaExpandUm");
        overlapFrac       = getDouble(pr, "overlapFrac");
    }

    private static void put(Properties pr, String k, Double v){
        if (v != null) pr.setProperty(k, Double.toString(v));
    }

    private static Double getDouble(Properties pr, String k){
        String s = pr.getProperty(k);
        if (s == null) return null;
        try { return Double.valueOf(s.trim()); } catch (Exception ignored){ return null; }
    }
}
