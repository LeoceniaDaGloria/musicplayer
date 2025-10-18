// TimeUtils.java
package com.example.musicplayer.utils;

import java.util.concurrent.TimeUnit;

public class TimeUtils {

    public static String formatDuration(long duration) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static String formatDuration(int duration) {
        int minutes = duration / 60000;
        int seconds = (duration % 60000) / 1000;
        return String.format("%02d:%02d", minutes, seconds);
    }
}