package juloo.keyboard2.devconsole;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class DevConsoleLog {

    public static final String LEVEL_DEBUG = "DEBUG";
    public static final String LEVEL_INFO  = "INFO";
    public static final String LEVEL_WARN  = "WARN";
    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_LOG   = "LOG";

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.US);

    public final int    id;
    public final String level;
    public final String message;
    public final String source;
    public final String timestamp;
    public final String metadata;

    public DevConsoleLog(String level, String message, String source, String metadata) {
        this.id        = ID_COUNTER.getAndIncrement();
        this.level     = level;
        this.message   = message;
        this.source    = source;
        this.metadata  = metadata;
        this.timestamp = DATE_FMT.format(new Date());
    }

    public DevConsoleLog(int id, String level, String message, String source,
                         String timestamp, String metadata) {
        this.id        = id;
        this.level     = level;
        this.message   = message;
        this.source    = source;
        this.timestamp = timestamp;
        this.metadata  = metadata;
    }

    public String toLogLine() {
        return "[" + timestamp + "] " + level.toUpperCase() + " [" + source + "]: " + message;
    }
}
