package fastloader.profiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fastloader.profiler.LoadProfiler.Milestone;
import fastloader.profiler.TransformerProfiler.Slow;
import fastloader.profiler.TransformerProfiler.Timed;

/** Turns the recorded data into a human readable report plus a CSV with every section. */
final class ReportWriter {

    /**
     * FML pushes one ProgressManager bar per lifecycle event, with one step per mod. Bar titles are matched after
     * lowercasing and dropping non-letters, accepting both event descriptions and LoaderState names.
     */
    private static final String[][] MOD_PHASE_TITLES = { { "construction", "constructing" }, { "preinitialization" },
        { "initialization" }, { "postinitialization" }, { "loadcomplete", "available" } };
    private static final String[] MOD_PHASE_SHORT = { "Construct", "PreInit", "Init", "PostInit", "LoadDone" };
    private static final int PHASES = MOD_PHASE_SHORT.length;
    private static final int TOP = 40;

    private ReportWriter() {}

    static File write() throws IOException {
        File dir = LoadProfiler.outputDir;
        dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(LoadProfiler.initMillis));
        File report = new File(dir, "latest-report.txt");
        File csv = new File(dir, "latest-sections.csv");

        try (PrintWriter out = open(report)) {
            writeReport(out);
        }
        try (PrintWriter out = open(csv)) {
            out.println("depth,start_ms,duration_ms,self_ms,gc_ms,jit_ms,classes_loaded,thread,path");
            writeCsv(out, LoadProfiler.ROOT, 0);
        }
        // keep a history so runs can be compared after each optimisation
        File history = new File(dir, "history");
        history.mkdirs();
        Files.copy(
            report.toPath(),
            new File(history, "report-" + stamp + ".txt").toPath(),
            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(
            csv.toPath(),
            new File(history, "sections-" + stamp + ".csv").toPath(),
            StandardCopyOption.REPLACE_EXISTING);
        return report;
    }

    private static PrintWriter open(File f) throws IOException {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- report

    private static void writeReport(PrintWriter out) {
        Section root = LoadProfiler.ROOT;
        long total = root.endNanos - LoadProfiler.jvmStartNanos();

        out.println("==================================================================================");
        out.println(" FastLoader startup report");
        out.println("==================================================================================");
        out.println(" Date:              " + new Date(LoadProfiler.initMillis));
        out.printf(" TOTAL (JVM start -> end of loading): %.2f s%n", total / 1e9);
        out.printf(" Profiled part (FastLoader coremod -> end): %.2f s%n", root.durationNanos() / 1e9);
        writeEnvironment(out);

        section(out, "1. Timeline (milestones)");
        out.println(" Each line: time since JVM start | time since previous milestone | milestone");
        out.println(" Large gaps between milestones are where the time goes; see the tree below for detail.");
        out.println();
        Milestone prev = null;
        for (Milestone m : LoadProfiler.MILESTONES) {
            long at = m.nanos - LoadProfiler.jvmStartNanos();
            long delta = prev == null ? 0 : m.nanos - prev.nanos;
            out.printf(" %9s | %9s | %s%n", ms(at), prev == null ? "" : "+" + ms(delta), m.name);
            prev = m;
        }

        section(out, "2. Load tree");
        out.printf(
            " Sections shorter than %d ms are folded. Columns: total | self | %% of total | GC | +classes%n",
            LoadProfiler.MIN_REPORT_NANOS / 1_000_000L);
        out.println(" 'self' is time not covered by a child section.");
        out.println();
        writeTree(out, root, 0, total);

        section(out, "3. Slowest individual steps (by self time)");
        List<Section> all = new ArrayList<>();
        collect(root, all);
        all.sort((a, b) -> Long.compare(b.selfNanos(), a.selfNanos()));
        int n = 0;
        for (Section s : all) {
            if (s == root || s.selfNanos() < 1_000_000L) continue;
            if (n++ >= TOP) break;
            out.printf(" %9s  %5.1f%%  %s%n", ms(s.selfNanos()), pct(s.selfNanos(), total), s.path());
        }

        writeModTable(out, all, total);
        writeResourceListeners(out, all);
        writeTransformers(out, total);
        writeJvm(out, total);
        writeHints(out, all, total);
    }

    private static void writeEnvironment(PrintWriter out) {
        try {
            RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
            out.println(
                " Java:              " + System.getProperty("java.version")
                    + " ("
                    + System.getProperty("java.vm.name")
                    + ", "
                    + System.getProperty("java.vendor")
                    + ")");
            out.println(" OS:                " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            out.println(
                " CPUs:              " + Runtime.getRuntime()
                    .availableProcessors());
            out.println(
                " Max heap (-Xmx):   " + Runtime.getRuntime()
                    .maxMemory() / (1024 * 1024) + " MB");
            out.println(" JVM arguments:     " + String.join(" ", rt.getInputArguments()));
        } catch (Throwable ignored) {}
        try {
            Class<?> loader = Class.forName("cpw.mods.fml.common.Loader");
            Object instance = loader.getMethod("instance")
                .invoke(null);
            List<?> mods = (List<?>) loader.getMethod("getActiveModList")
                .invoke(instance);
            out.println(" Active mods:       " + mods.size());
        } catch (Throwable ignored) {}
    }

    private static void writeTree(PrintWriter out, Section s, int depth, long total) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < depth; i++) pad.append("  ");
        out.printf(
            " %9s %9s %5.1f%% %7s %7s  %s%s%n",
            ms(s.durationNanos()),
            s.children == null ? "" : ms(s.selfNanos()),
            pct(s.durationNanos(), total),
            s.gcMillis() > 0 ? s.gcMillis() + "gc" : "",
            s.loadedClasses() > 0 ? "+" + s.loadedClasses() : "",
            pad,
            s.name + (s.isBar ? "  [" + s.barSteps + " steps]" : "")
                + (s.thread != null && !"Client thread".equals(s.thread) && !"main".equals(s.thread)
                ? "  [" + s.thread + "]"
                : ""));
        if (s.children == null) return;
        long foldedNanos = 0;
        int folded = 0;
        for (Section c : s.children) {
            if (c.durationNanos() < LoadProfiler.MIN_REPORT_NANOS) {
                foldedNanos += c.durationNanos();
                folded++;
            } else {
                writeTree(out, c, depth + 1, total);
            }
        }
        if (folded > 0) {
            out.printf(" %9s %9s %5.1f%% %7s %7s  %s  ... %d faster sections%n", ms(foldedNanos), "",
                pct(foldedNanos, total), "", "", pad, folded);
        }
    }

    private static void writeModTable(PrintWriter out, List<Section> all, long total) {
        Map<String, long[]> perMod = new LinkedHashMap<>();
        for (Section s : all) {
            if (s.parent == null || !s.parent.isBar) continue;
            int phase = modPhase(s.parent.barTitle);
            if (phase < 0) continue;
            long[] row = perMod.computeIfAbsent(s.name, k -> new long[PHASES + 1]);
            row[phase] += s.durationNanos();
            row[PHASES] += s.durationNanos();
        }
        if (perMod.isEmpty()) return;

        section(out, "4. Mods: time spent in each FML lifecycle event handler");
        out.print(String.format(" %-40s", "Mod"));
        for (String p : MOD_PHASE_SHORT) out.printf(" %9s", p);
        out.printf(" %9s %6s%n", "TOTAL", "%");
        List<Map.Entry<String, long[]>> rows = new ArrayList<>(perMod.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[PHASES], a.getValue()[PHASES]));
        long sum = 0;
        int n = 0;
        for (Map.Entry<String, long[]> e : rows) {
            long[] r = e.getValue();
            sum += r[PHASES];
            if (n++ >= TOP) continue;
            out.printf(" %-40s", truncate(e.getKey(), 40));
            for (int i = 0; i < PHASES; i++) out.printf(" %9s", r[i] == 0 ? "-" : ms(r[i]));
            out.printf(" %9s %5.1f%%%n", ms(r[PHASES]), pct(r[PHASES], total));
        }
        if (rows.size() > TOP) out.printf(" ... and %d more mods%n", rows.size() - TOP);
        out.printf(" All mod event handlers together: %s (%.1f%% of total)%n", ms(sum), pct(sum, total));
    }

    private static int modPhase(String title) {
        if (title == null) return -1;
        String t = title.toLowerCase()
            .replaceAll("[^a-z]", "");
        for (int i = 0; i < MOD_PHASE_TITLES.length; i++) {
            if (Arrays.asList(MOD_PHASE_TITLES[i])
                .contains(t)) return i;
        }
        return -1;
    }

    private static void writeResourceListeners(PrintWriter out, List<Section> all) {
        Map<String, long[]> perListener = new LinkedHashMap<>();
        int reloads = 0;
        for (Section s : all) {
            if (s.isBar && "Loading Resource".equals(s.barTitle)) reloads++;
            if (s.parent == null || !s.parent.isBar || !"Loading Resource".equals(s.parent.barTitle)) continue;
            long[] row = perListener.computeIfAbsent(s.name, k -> new long[2]);
            row[0] += s.durationNanos();
            row[1]++;
        }
        if (perListener.isEmpty()) return;
        section(out, "5. Resource reload listeners (summed over " + reloads + " resource reload(s))");
        out.println(" Every full resource reload runs all of these. Vanilla does one reload at startup, mods");
        out.println(" that call refreshResources() again add a whole extra round.");
        out.println();
        List<Map.Entry<String, long[]>> rows = new ArrayList<>(perListener.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        int n = 0;
        for (Map.Entry<String, long[]> e : rows) {
            if (n++ >= TOP) break;
            out.printf(" %9s  x%-3d %s%n", ms(e.getValue()[0]), e.getValue()[1], e.getKey());
        }
    }

    private static void writeTransformers(PrintWriter out, long total) {
        section(out, "6. ASM class transformers (coremods)");
        if (!TransformerProfiler.ENABLED) {
            out.println(" Disabled (-Dfastloader.profiler.transformers=false)");
            return;
        }
        if (TransformerProfiler.failure != null) {
            out.println(" Unavailable: " + TransformerProfiler.failure);
            return;
        }
        out.println(" Only classes loaded after the transformers were wrapped are counted (from the first FML");
        out.println(" loading bar / Minecraft.startGame). Mixin is not included. 'changed' = classes modified.");
        out.println();
        List<Timed> list;
        synchronized (TransformerProfiler.class) {
            list = new ArrayList<>(TransformerProfiler.TIMED);
        }
        list.sort((a, b) -> Long.compare(b.nanos.get(), a.nanos.get()));
        long sum = 0;
        out.printf(" %9s %6s %8s %8s  %s%n", "time", "%", "calls", "changed", "transformer");
        for (Timed t : list) {
            sum += t.nanos.get();
            out.printf(
                " %9s %5.1f%% %8d %8d  %s%n",
                ms(t.nanos.get()),
                pct(t.nanos.get(), total),
                t.calls.get(),
                t.changed.get(),
                t.name);
        }
        out.printf(" All wrapped transformers together: %s (%.1f%% of total)%n", ms(sum), pct(sum, total));

        List<Slow> slow;
        synchronized (TransformerProfiler.SLOWEST) {
            slow = new ArrayList<>(TransformerProfiler.SLOWEST);
        }
        if (slow.isEmpty()) return;
        slow.sort((a, b) -> Long.compare(b.nanos, a.nanos));
        out.println();
        out.println(" Slowest single class transformations:");
        for (Slow s : slow) out.printf(" %9s  %s  <-  %s%n", ms(s.nanos), s.className, s.transformer);
    }

    private static void writeJvm(PrintWriter out, long total) {
        section(out, "7. JVM");
        long[] end = LoadProfiler.ROOT.endStats;
        out.printf(" GC time:            %s (%.1f%% of total)%n", ms(end[0] * 1_000_000L), pct(end[0] * 1_000_000L, total));
        try {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                out.printf("   %-30s %6d collections, %s%n", gc.getName(), gc.getCollectionCount(),
                    ms(gc.getCollectionTime() * 1_000_000L));
            }
        } catch (Throwable ignored) {}
        out.printf(" JIT compile time:   %s (all compiler threads, runs in parallel)%n", ms(end[1] * 1_000_000L));
        out.println(" Classes loaded:     " + end[2]);
        out.println(" Heap used at end:   " + end[3] / (1024 * 1024) + " MB");
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage();
            out.println(" Heap committed:     " + heap.getCommitted() / (1024 * 1024) + " MB");
        } catch (Throwable ignored) {}
    }

    private static void writeHints(PrintWriter out, List<Section> all, long total) {
        section(out, "8. Hints");
        long[] end = LoadProfiler.ROOT.endStats;
        int hints = 0;
        if (end[0] * 1_000_000L > total / 10) {
            hints++;
            out.println(" * GC took more than 10% of startup. Give the game more heap (-Xmx / -Xms equal),");
            out.println("   or try a different collector (-XX:+UseG1GC on Java 8, -XX:+UseZGC / Shenandoah on 17+).");
        }
        long transformerSum = 0;
        for (Timed t : TransformerProfiler.TIMED) transformerSum += t.nanos.get();
        if (transformerSum > total / 10) {
            hints++;
            out.println(" * Class transformers took more than 10% of startup. Look at section 6: transformers");
            out.println("   with many calls but few 'changed' should filter by class name before parsing bytes.");
        }
        Set<String> seen = new HashSet<>();
        for (Section s : all) {
            if (s.isBar && "Loading Resource".equals(s.barTitle) && !seen.add(s.barTitle)) {
                hints++;
                out.println(" * Resources were reloaded more than once during startup. Every extra reload re-runs");
                out.println("   all listeners of section 5 (textures, sounds, fonts...). Find the caller in the tree.");
                break;
            }
        }
        for (Section s : all) {
            if (s.name.startsWith("Texture atlas") && s.durationNanos() > total / 10) {
                hints++;
                out.println(" * Texture stitching is a big part of startup (" + s.name + ", " + ms(s.durationNanos())
                    + "). Lower mipmap levels or a faster stitcher help here.");
            }
        }
        if (hints == 0) out.println(" Nothing obvious. Start from section 3 and the biggest gaps in section 1.");
    }

    // ---------------------------------------------------------------- csv

    private static void writeCsv(PrintWriter out, Section s, int depth) {
        out.printf(
            "%d,%d,%.3f,%.3f,%d,%d,%d,%s,%s%n",
            depth,
            (s.startNanos - LoadProfiler.jvmStartNanos()) / 1_000_000L,
            s.durationNanos() / 1e6,
            s.selfNanos() / 1e6,
            s.gcMillis(),
            s.jitMillis(),
            s.loadedClasses(),
            csvEscape(s.thread),
            csvEscape(s.path()));
        if (s.children != null) for (Section c : s.children) writeCsv(out, c, depth + 1);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    // ---------------------------------------------------------------- helpers

    private static void collect(Section s, List<Section> into) {
        into.add(s);
        if (s.children != null) for (Section c : s.children) collect(c, into);
    }

    private static void section(PrintWriter out, String title) {
        out.println();
        out.println("----------------------------------------------------------------------------------");
        out.println(" " + title);
        out.println("----------------------------------------------------------------------------------");
    }

    private static String ms(long nanos) {
        return String.format("%d ms", nanos / 1_000_000L);
    }

    private static double pct(long part, long whole) {
        return whole <= 0 ? 0 : part * 100.0 / whole;
    }

    private static String truncate(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len - 1) + "~";
    }
}
