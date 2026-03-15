package juloo.sysconsole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Identifies banking/finance apps and analyzes their permission risk.
 *
 * Banking apps are high-value targets. An app that:
 *  - Has accessibility service + can overlay other apps  → credential phishing
 *  - Can read SMS while banking app is present           → OTP interception
 *  - Can take screenshots or screen record               → credential capture
 *  - Has known banking package name + dangerous perms    → suspicious banking clone
 */
public class BankingRiskAnalyzer {

    public static class BankingRisk {
        public boolean isBankingApp;
        public boolean hasCriticalRisk;
        public List<String> riskWarnings = new ArrayList<>();
        public int riskBonus;
    }

    // Well-known banking & finance app package prefixes
    private static final Set<String> BANKING_PKG_PREFIXES = new HashSet<>(Arrays.asList(
            "com.chase", "com.bofa", "com.wellsfargo", "com.citibank",
            "com.usbank", "com.capitalone", "com.discover", "com.american.express",
            "com.barclays", "com.hsbc", "com.lloydsbank", "com.natwest",
            "com.santander", "com.td.canada", "com.rbc", "com.bmo",
            "com.scotiabank", "com.cibc", "net.bnpparibas", "com.societegenerale",
            "com.paypal", "com.venmo", "com.cashapp", "com.zelle",
            "com.wise", "com.revolut", "com.monzo", "com.starlingbank",
            "com.n26", "piuk.blockchain", "com.coinbase", "com.binance",
            "com.kraken", "io.metamask", "com.robinhood", "com.etrade",
            "com.fidelity", "com.schwab", "com.vanguard",
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay",
            "com.android.systemui"
    ));

    // Finance-related keywords in app names
    private static final String[] BANKING_NAME_KEYWORDS = {
        "bank", "banking", "finance", "wallet", "pay", "cash", "crypto",
        "invest", "trading", "stock", "credit", "debit", "loan", "transfer",
        "money", "wealth", "savings", "account", "card"
    };

    public static BankingRisk analyze(AppSecurityInfo app,
                                      boolean otherAppHasSms,
                                      boolean otherAppHasAccessibility,
                                      boolean otherAppHasOverlay) {
        BankingRisk result = new BankingRisk();

        result.isBankingApp = isBankingApp(app);

        if (result.isBankingApp) {
            // Banking app with dangerous permissions itself
            if (app.hasAccessibility) {
                result.riskWarnings.add(
                        "Banking app has Accessibility service — unusual and risky");
                result.riskBonus += 25;
                result.hasCriticalRisk = true;
            }
            if (app.hasOverlay && app.hasSms) {
                result.riskWarnings.add(
                        "Banking app can overlay UI and read SMS — extreme risk");
                result.riskBonus += 30;
                result.hasCriticalRisk = true;
            }
        }

        // Non-banking app threat to banking apps
        if (!result.isBankingApp && !app.isSystemApp) {
            if (app.hasOverlay && app.hasAccessibility) {
                result.riskWarnings.add(
                        "Can overlay banking apps and read screen input — password theft risk");
                result.riskBonus += 35;
                result.hasCriticalRisk = true;
            }
            if (app.hasSms && app.hasInternet) {
                result.riskWarnings.add(
                        "Can intercept OTP codes from bank SMS and forward them remotely");
                result.riskBonus += 25;
                result.hasCriticalRisk = true;
            }
            if (app.hasOverlay && app.hasInternet) {
                result.riskWarnings.add(
                        "Can show fake login screens over banking apps (overlay phishing)");
                result.riskBonus += 20;
            }
        }

        return result;
    }

    /**
     * Identify known banking/finance apps.
     */
    public static boolean isBankingApp(AppSecurityInfo app) {
        String pkgLower  = app.packageName.toLowerCase();
        String nameLower = app.appName.toLowerCase();

        for (String prefix : BANKING_PKG_PREFIXES) {
            if (pkgLower.startsWith(prefix)) return true;
        }
        for (String kw : BANKING_NAME_KEYWORDS) {
            if (nameLower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Generate an overall OTP-interception risk warning if:
     * - A non-system app has SMS + Internet permission
     * - AND at least one banking app is installed
     */
    public static boolean isOtpInterceptionRisk(AppSecurityInfo app,
                                                 List<AppSecurityInfo> allApps) {
        if (app.isSystemApp || !app.hasSms || !app.hasInternet) return false;
        for (AppSecurityInfo other : allApps) {
            if (isBankingApp(other)) return true;
        }
        return false;
    }

    /**
     * Generate a banking-overlay phishing risk if any installed app
     * has overlay permission and a banking app is also installed.
     */
    public static boolean isOverlayPhishingRisk(AppSecurityInfo app,
                                                  List<AppSecurityInfo> allApps) {
        if (app.isSystemApp || !app.hasOverlay || !app.hasInternet) return false;
        for (AppSecurityInfo other : allApps) {
            if (!other.packageName.equals(app.packageName) && isBankingApp(other)) return true;
        }
        return false;
    }
}
