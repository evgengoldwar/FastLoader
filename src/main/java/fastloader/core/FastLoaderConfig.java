package fastloader.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import net.minecraft.launchwrapper.Launch;

/**
 * Settings readable from the coremod phase, before Forge's config system exists: {@code config/fastloader.properties},
 * overridden by {@code -Dfastloader.<key>=...} JVM arguments.
 */
public final class FastLoaderConfig {

    private static final String[][] DEFAULTS = {
        { "profiler", "true", "Write the startup profile to logs/fastloader/" },
        { "prefetchClassBytes", "true",
            "Read the classes the previous startup needed from the mod jars on background threads, so the game does"
                + " not have to look them up and read them on the main thread" },
        { "prefetchThreads", "0", "Threads for prefetchClassBytes, 0 = automatic" },
        { "preloadClasses", "false",
            "EXPERIMENTAL: fully load (transform and define, without initialising) the classes of the previous"
                + " startup on a background thread from mod construction on. Bigger gain, but coremods that"
                + " behave differently depending on when a class is loaded can break" }, };

    private static Properties props;

    private FastLoaderConfig() {}

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static synchronized String get(String key) {
        String sys = System.getProperty("fastloader." + key);
        if (sys != null) return sys;
        if (props == null) load();
        String v = props.getProperty(key);
        if (v != null) return v;
        for (String[] d : DEFAULTS) if (d[0].equals(key)) return d[1];
        return "";
    }

    public static File gameDir() {
        return Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
    }

    /** Cache directory for data kept between launches. */
    public static File dataDir() {
        File dir = new File(gameDir(), "fastloader");
        dir.mkdirs();
        return dir;
    }

    private static void load() {
        props = new Properties();
        File file = new File(new File(gameDir(), "config"), "fastloader.properties");
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (Throwable t) {
                System.err.println("[FastLoader] Cannot read " + file + ": " + t);
            }
        }
        boolean missing = false;
        for (String[] d : DEFAULTS) if (!props.containsKey(d[0])) missing = true;
        if (missing) write(file);
    }

    private static void write(File file) {
        file.getParentFile()
            .mkdirs();
        try (PrintWriter out = new PrintWriter(
            new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            out.println("# FastLoader settings. Each can be overridden with a JVM argument -Dfastloader.<key>=<value>");
            for (String[] d : DEFAULTS) {
                out.println();
                out.println("# " + d[2]);
                out.println(d[0] + "=" + props.getProperty(d[0], d[1]));
            }
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot write " + file + ": " + t);
        }
    }
}
