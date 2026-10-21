package com.telegram.sessionmanager.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class TimeHelper {

    private TimeHelper() {}

    /**
     * Formats a Unix timestamp (in seconds) into a readable relative or absolute time string.
     */
    public static String formatLastActive(int timestampSeconds) {
        if (timestampSeconds <= 0) return "Unknown";

        long timeMillis = (long) timestampSeconds * 1000L;
        long nowMillis = System.currentTimeMillis();
        long diffSeconds = (nowMillis - timeMillis) / 1000L;

        if (diffSeconds < 90) {
            return "Active just now";
        } else if (diffSeconds < 3600) {
            long minutes = Math.max(1, diffSeconds / 60);
            return minutes + " min ago";
        } else if (diffSeconds < 86400) {
            long hours = diffSeconds / 3600;
            return hours + " hr ago";
        }

        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timeMillis);

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1) {
            return "Yesterday, " + timeFormat.format(new Date(timeMillis));
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        return dateFormat.format(new Date(timeMillis));
    }

    public static String formatFullDate(int timestampSeconds) {
        if (timestampSeconds <= 0) return "Unknown";
        long timeMillis = (long) timestampSeconds * 1000L;
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date(timeMillis));
    }
}
