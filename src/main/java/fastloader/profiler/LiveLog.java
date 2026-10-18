package fastloader.profiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Line-flushed log file, so the last lines survive a crash or a hang during loading. */
final class LiveLog {

    private PrintWriter out;

    LiveLog(File file) {
        this(file, false);
    }

    LiveLog(File file, boolean append) {
        try {
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8),
                true);
        } catch (Throwable t) {
            System.err.println("[FastLoader] Cannot open " + file + ": " + t);
        }
    }

    void line(String s) {
        if (out != null) out.println(s);
    }

    void close() {
        if (out != null) out.close();
        out = null;
    }
}
