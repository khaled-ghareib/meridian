package com.meridian.finance;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Keeps bank/wallet text messages until the app reads them.
 * Only messages that mention an amount in Egyptian pounds are kept; everything stays on the phone.
 */
final class SmsStore {

    private SmsStore() { }

    private static final String INBOX = "sms_inbox.json";
    private static final int MAX_KEPT = 300;
    private static final Object LOCK = new Object();

    /** A money amount in EGP, written in English or Arabic, next to a number. */
    private static final Pattern MONEY = Pattern.compile(
            "(?i)((?<![a-z])(egp|l\\.?e\\.?)(?![a-z])|جنيه|ج\\.?\\s?م)");
    private static final Pattern DIGIT = Pattern.compile("[0-9٠-٩]");

    static boolean looksFinancial(String body) {
        return body != null && MONEY.matcher(body).find() && DIGIT.matcher(body).find();
    }

    /** Adds one message to the waiting list (called when an SMS arrives, even if the app is closed). */
    static void add(Context ctx, String sender, String body, long time) {
        if (!looksFinancial(body)) return;
        synchronized (LOCK) {
            try {
                JSONArray list = readArray(ctx);
                JSONObject o = new JSONObject();
                o.put("sender", sender == null ? "" : sender);
                o.put("body", body);
                o.put("time", time);
                list.put(o);
                while (list.length() > MAX_KEPT) list.remove(0);
                write(ctx, list.toString());
            } catch (Exception ignored) { }
        }
    }

    /** Returns everything waiting and empties the list. */
    static String take(Context ctx) {
        synchronized (LOCK) {
            try {
                JSONArray list = readArray(ctx);
                new File(ctx.getFilesDir(), INBOX).delete();
                return list.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    /** Reads bank-looking messages already in the phone's inbox from the last {@code days} days. */
    static String recent(Context ctx, int days) {
        JSONArray out = new JSONArray();
        long cutoff = System.currentTimeMillis() - days * 86400000L;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"),
                    new String[]{"address", "body", "date"}, "date > ?",
                    new String[]{String.valueOf(cutoff)}, "date ASC");
            if (c != null) {
                while (c.moveToNext() && out.length() < 1000) {
                    String body = c.getString(1);
                    if (!looksFinancial(body)) continue;
                    JSONObject o = new JSONObject();
                    o.put("sender", c.getString(0) == null ? "" : c.getString(0));
                    o.put("body", body);
                    o.put("time", c.getLong(2));
                    out.put(o);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out.toString();
    }

    private static JSONArray readArray(Context ctx) throws Exception {
        File f = new File(ctx.getFilesDir(), INBOX);
        if (!f.exists()) return new JSONArray();
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            String s = buf.toString("UTF-8").trim();
            return s.isEmpty() ? new JSONArray() : new JSONArray(s);
        }
    }

    private static void write(Context ctx, String json) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), INBOX))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
