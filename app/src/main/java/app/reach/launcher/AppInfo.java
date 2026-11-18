package app.reach.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import java.text.Normalizer;

/** One launchable app, or the special "search the web" row. */
final class AppInfo {
    final String label;
    final ComponentName component; // null for the web-search row
    final String key;
    final String letter;
    final String webQuery;         // non-null only for the web-search row
    final Drawable icon;

    AppInfo(String label, ComponentName component, Drawable icon) {
        this.label = label;
        this.component = component;
        this.icon = icon;
        this.key = component.flattenToString();
        this.letter = letterFor(label);
        this.webQuery = null;
    }

    private AppInfo(String query, boolean unused) {
        this.label = "Search the web for “" + query + "”";
        this.component = null;
        this.icon = null;
        this.key = "";
        this.letter = "";
        this.webQuery = query;
    }

    static AppInfo webSearch(String query) {
        return new AppInfo(query, true);
    }

    /** A–Z for Latin labels (accents removed), "#" for everything else. */
    static String letterFor(String label) {
        if (label == null || label.isEmpty()) return "#";
        String first = Normalizer.normalize(label.substring(0, 1), Normalizer.Form.NFD);
        char c = Character.toUpperCase(first.charAt(0));
        return (c >= 'A' && c <= 'Z') ? String.valueOf(c) : "#";
    }
}
