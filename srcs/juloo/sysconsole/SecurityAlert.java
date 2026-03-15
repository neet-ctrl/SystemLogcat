package juloo.sysconsole;

public class SecurityAlert {

    public static final int TYPE_CAMERA     = 0;
    public static final int TYPE_MIC        = 1;
    public static final int TYPE_LOCATION   = 2;
    public static final int TYPE_INSTALL    = 3;
    public static final int TYPE_BACKGROUND = 4;
    public static final int TYPE_HIGH_RISK  = 5;

    public static final int SEV_INFO     = 0;
    public static final int SEV_WARNING  = 1;
    public static final int SEV_CRITICAL = 2;

    public int    type;
    public int    severity;
    public String title;
    public String description;
    public String packageName;
    public String appName;
    public long   timestamp;
    public boolean dismissed;

    public SecurityAlert(int type, int severity, String title, String desc,
                         String pkg, String app, long ts) {
        this.type        = type;
        this.severity    = severity;
        this.title       = title;
        this.description = desc;
        this.packageName = pkg;
        this.appName     = app;
        this.timestamp   = ts;
    }

    public int iconChar() {
        switch (type) {
            case TYPE_CAMERA:     return 0x1F4F7;
            case TYPE_MIC:        return 0x1F3A4;
            case TYPE_LOCATION:   return 0x1F4CD;
            case TYPE_INSTALL:    return 0x1F4E6;
            case TYPE_BACKGROUND: return 0x23F3;
            default:              return 0x26A0;
        }
    }

    public int severityColor() {
        switch (severity) {
            case SEV_CRITICAL: return 0xFFE63946;
            case SEV_WARNING:  return 0xFFFF9F1C;
            default:           return 0xFF00A896;
        }
    }

    public String typeLabel() {
        switch (type) {
            case TYPE_CAMERA:     return "Camera";
            case TYPE_MIC:        return "Microphone";
            case TYPE_LOCATION:   return "Location";
            case TYPE_INSTALL:    return "App Install";
            case TYPE_BACKGROUND: return "Background";
            default:              return "Risk";
        }
    }
}
