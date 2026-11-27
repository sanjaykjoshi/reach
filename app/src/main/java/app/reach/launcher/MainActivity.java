package app.reach.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF8AB4F8;
    private static final String K_FAVS = "favourites";
    private static final String K_HIDDEN = "hidden";
    private static final String K_LEFT = "left_handed";
    private static final String K_SEEDED = "seeded";

    private enum Mode { NONE, LETTER, SEARCH }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Drawable> iconCache = Collections.synchronizedMap(new HashMap<>());

    private SharedPreferences prefs;
    private float dp;
    private volatile boolean loading;

    private List<AppInfo> everyApp = new ArrayList<>(); // including hidden
    private List<AppInfo> apps = new ArrayList<>();     // visible only

    private FrameLayout root;
    private LinearLayout favContainer;
    private TextView searchPill;
    private FrameLayout overlay;
    private LinearLayout panel;
    private TextView panelTitle;
    private EditText searchField;
    private ListView list;
    private AppAdapter adapter;
    private AlphabetBar bar;

    private Mode mode = Mode.NONE;
    private String currentLetter = "";

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("reach", MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;

        buildUi();
        setContentView(root);
        goEdgeToEdge();
        applyHandedness();
        loadApps();
        if (!isDefaultHome() && !prefs.getBoolean("asked_home", false)) {
            prefs.edit().putBoolean("asked_home", true).apply();
            ui.postDelayed(this::requestDefaultHome, 600);
        }
    }

    private static final int REQ_HOME = 41;

    private boolean isDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                return rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
            }
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** Shows Android's own "Set Reach as your home app?" prompt, or the settings page. */
    private void requestDefaultHome() {
        if (isDefaultHome()) {
            toast("Reach is already your home app");
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        openHomeSettings();
    }

    private void openHomeSettings() {
        toast("Choose Reach as your Home app");
        if (!launch(new Intent(Settings.ACTION_HOME_SETTINGS))) {
            launch(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Some phones skip the prompt silently; fall back to the settings page.
        if (requestCode == REQ_HOME && !isDefaultHome()) openHomeSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps(); // picks up installs, uninstalls and renamed apps
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeOverlay();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeOverlay(); // home button pressed while already home
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // A launcher never closes itself; back only dismisses panels.
        if (mode != Mode.NONE) closeOverlay();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0x55000000);

        // Home: clock at top, favourites and search near the thumb.
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(px(28), px(36), px(28), px(20));
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setOnClickListener(v -> launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
        home.addView(clock, wrap());

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        date.setTextColor(0xCCFFFFFF);
        date.setOnClickListener(v -> launch(Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)));
        home.addView(date, wrap());

        home.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        favContainer = new LinearLayout(this);
        favContainer.setOrientation(LinearLayout.VERTICAL);
        home.addView(favContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchPill = new TextView(this);
        searchPill.setText("Search");
        searchPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        searchPill.setTextColor(0xB3FFFFFF);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setBackground(pill(0x33FFFFFF));
        searchPill.setPadding(px(20), px(14), px(20), px(14));
        Drawable mag = getDrawable(android.R.drawable.ic_menu_search);
        if (mag != null) {
            mag = mag.mutate();
            mag.setTint(0xB3FFFFFF);
            mag.setBounds(0, 0, px(22), px(22));
            searchPill.setCompoundDrawablesRelative(mag, null, null, null);
            searchPill.setCompoundDrawablePadding(px(12));
        }
        searchPill.setOnClickListener(v -> openSearch());
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = px(20);
        home.addView(searchPill, pillLp);

        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dy) > px(60) && Math.abs(vy) > Math.abs(vx)) {
                    if (dy > 0) expandNotifications();
                    else openSearch();
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                showSettings();
            }
        });
        home.setOnTouchListener((v, e) -> gestures.onTouchEvent(e));

        // Overlay: letter panel or search, with everything at the bottom.
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xF2101114);
        overlay.setVisibility(View.GONE);
        overlay.setOnClickListener(v -> closeOverlay());
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adapter = new AppAdapter(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setStackFromBottom(true); // rows sit just above your thumb
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onRowTapped(adapter.getItem(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            AppInfo app = adapter.getItem(pos);
            if (app.component != null) showAppMenu(app);
            return true;
        });
        GestureDetector emptyTap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (list.pointToPosition((int) e.getX(), (int) e.getY()) == ListView.INVALID_POSITION) {
                    closeOverlay();
                    return true;
                }
                return false;
            }
        });
        list.setOnTouchListener((v, e) -> {
            emptyTap.onTouchEvent(e);
            return false;
        });
        panel.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        panelTitle = new TextView(this);
        panelTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        panelTitle.setTypeface(Typeface.DEFAULT_BOLD);
        panelTitle.setTextColor(ACCENT);
        panelTitle.setPadding(px(24), px(4), px(24), px(8));
        panel.addView(panelTitle, wrap());

        searchField = new EditText(this);
        searchField.setHint("Search apps or the web");
        searchField.setHintTextColor(0x80FFFFFF);
        searchField.setTextColor(Color.WHITE);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchField.setBackground(pill(0x26FFFFFF));
        searchField.setPadding(px(20), px(14), px(20), px(14));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (mode == Mode.SEARCH) filter(s.toString());
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            // Hardware Enter fires down + up; act once.
            if (event != null && event.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
            launchBestMatch();
            return true;
        });
        LinearLayout.LayoutParams sfLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sfLp.setMargins(px(16), px(8), px(16), px(12));
        panel.addView(searchField, sfLp);

        // The A–Z strip goes on top of everything so it works while a panel is open.
        bar = new AlphabetBar(this);
        bar.setListener(this::showLetter);
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                px(130), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.BOTTOM);
        barLp.bottomMargin = px(12);
        root.addView(bar, barLp);

        // Keep the strip in the lower ~62% of the screen, within thumb reach.
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (mode == Mode.SEARCH) return; // keyboard is up; don't squash the strip
            int avail = (b - t) - root.getPaddingTop() - root.getPaddingBottom();
            int h = Math.round(avail * 0.62f);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (h > 0 && lp.height != h) {
                lp.height = h;
                ui.post(() -> bar.setLayoutParams(lp));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets in = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                l = in.left; t = in.top; r = in.right; b = in.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });
    }

    private void applyHandedness() {
        boolean left = prefs.getBoolean(K_LEFT, false);
        bar.setLeftHanded(left);
        FrameLayout.LayoutParams barLp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        barLp.gravity = (left ? Gravity.START : Gravity.END) | Gravity.BOTTOM;
        bar.setLayoutParams(barLp);

        // Keep content clear of the strip.
        panel.setPadding(left ? px(64) : 0, px(24), left ? 0 : px(64), 0);
        LinearLayout.LayoutParams pillLp = (LinearLayout.LayoutParams) searchPill.getLayoutParams();
        pillLp.setMarginStart(left ? px(40) : 0);
        pillLp.setMarginEnd(left ? 0 : px(40));
        searchPill.setLayoutParams(pillLp);
        panelTitle.setGravity(left ? Gravity.END : Gravity.START);

        adapter.setAlignEnd(left);
        renderFavourites();
    }

    private void renderFavourites() {
        favContainer.removeAllViews();
        boolean left = prefs.getBoolean(K_LEFT, false);
        List<AppInfo> favs = favouriteApps();

        if (favs.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(apps.isEmpty() ? "" :
                    "Long-press any app to pin it here.\nSlide along the letters on the edge to find apps.");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            hint.setTextColor(0x99FFFFFF);
            hint.setGravity(left ? Gravity.END : Gravity.START);
            favContainer.addView(hint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (AppInfo app : favs) {
            TextView row = new TextView(this);
            row.setText(app.label);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            row.setTextColor(Color.WHITE);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(0, px(10), 0, px(10));
            row.setGravity((left ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            Drawable icon = copyOf(app.icon);
            if (icon != null) {
                icon.setBounds(0, 0, px(32), px(32));
                if (left) row.setCompoundDrawablesRelative(null, null, icon, null);
                else row.setCompoundDrawablesRelative(icon, null, null, null);
                row.setCompoundDrawablePadding(px(16));
            }
            row.setOnClickListener(v -> launchApp(app));
            row.setOnLongClickListener(v -> {
                showAppMenu(app);
                return true;
            });
            favContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<AppInfo> out = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
                for (ResolveInfo ri : found) {
                    if (ri.activityInfo == null) continue;
                    if (getPackageName().equals(ri.activityInfo.packageName)) continue;
                    ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                    String key = cn.flattenToString();
                    // Include the app's last update time so updated apps get their new icon.
                    long updated = 0;
                    try {
                        updated = pm.getPackageInfo(ri.activityInfo.packageName, 0).lastUpdateTime;
                    } catch (Exception ignored) {
                    }
                    String iconKey = key + "@" + updated;
                    Drawable icon = iconCache.get(iconKey);
                    if (icon == null) {
                        try {
                            icon = ri.loadIcon(pm);
                        } catch (Exception e) {
                            icon = pm.getDefaultActivityIcon();
                        }
                        iconCache.put(iconKey, icon);
                    }
                    CharSequence cs = ri.loadLabel(pm);
                    String label = cs == null ? ri.activityInfo.packageName : cs.toString().trim();
                    if (label.isEmpty()) label = ri.activityInfo.packageName;
                    out.add(new AppInfo(label, cn, icon));
                }
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                Collections.sort(out, (a, b) -> collator.compare(a.label, b.label));
            } catch (Exception ignored) {
                // keep whatever we had
            }
            ui.post(() -> {
                loading = false;
                if (!out.isEmpty()) {
                    everyApp = out;
                    applyApps();
                }
            });
        }).start();
    }

    private void applyApps() {
        Set<String> hidden = new HashSet<>(getList(K_HIDDEN));
        List<AppInfo> visible = new ArrayList<>();
        for (AppInfo a : everyApp) if (!hidden.contains(a.key)) visible.add(a);
        apps = visible;

        seedFavouritesOnce();

        TreeSet<String> letters = new TreeSet<>();
        boolean other = false;
        for (AppInfo a : apps) {
            if ("#".equals(a.letter)) other = true;
            else letters.add(a.letter);
        }
        List<String> strip = new ArrayList<>(letters);
        if (other) strip.add("#");
        bar.setLetters(strip.toArray(new String[0]));

        renderFavourites();
        if (mode == Mode.LETTER) showLetter(currentLetter);
        else if (mode == Mode.SEARCH) filter(searchField.getText().toString());
    }

    /** First run: pin the phone's own dialer, messages, camera and browser. */
    private void seedFavouritesOnce() {
        if (prefs.getBoolean(K_SEEDED, false) || apps.isEmpty()) return;
        List<String> keys = new ArrayList<>();
        Intent[] probes = {
                new Intent(Intent.ACTION_DIAL),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
                new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        };
        PackageManager pm = getPackageManager();
        for (Intent probe : probes) {
            try {
                ResolveInfo ri = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if ("android".equals(pkg)) continue; // the "choose an app" screen
                for (AppInfo a : apps) {
                    if (a.component.getPackageName().equals(pkg) && !keys.contains(a.key)) {
                        keys.add(a.key);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        putList(K_FAVS, keys);
        prefs.edit().putBoolean(K_SEEDED, true).apply();
    }

    private List<AppInfo> favouriteApps() {
        Map<String, AppInfo> byKey = new HashMap<>();
        for (AppInfo a : apps) byKey.put(a.key, a);
        List<AppInfo> out = new ArrayList<>();
        for (String k : getList(K_FAVS)) {
            AppInfo a = byKey.get(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    // ---------------------------------------------------------------- panels

    private void showLetter(String letter) {
        currentLetter = letter;
        boolean wasSearch = mode == Mode.SEARCH;
        mode = Mode.LETTER;
        if (wasSearch) hideKeyboard();
        List<AppInfo> items = new ArrayList<>();
        for (AppInfo a : apps) if (letter.equals(a.letter)) items.add(a);
        panelTitle.setText(letter);
        panelTitle.setVisibility(View.VISIBLE);
        searchField.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        adapter.setItems(items);
        list.setSelection(0);
    }

    private void openSearch() {
        mode = Mode.SEARCH;
        overlay.setVisibility(View.VISIBLE);
        bar.setVisibility(View.GONE);
        panelTitle.setVisibility(View.GONE);
        searchField.setVisibility(View.VISIBLE);
        searchField.setText("");
        filter("");
        searchField.requestFocus();
        searchField.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    private void closeOverlay() {
        if (overlay == null) return;
        if (mode == Mode.SEARCH) hideKeyboard();
        mode = Mode.NONE;
        overlay.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE);
        searchField.setText("");
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        searchField.clearFocus();
    }

    /** Best matches end up at the bottom, right above the search box. */
    private void filter(String raw) {
        String q = raw.trim().toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            adapter.setItems(new ArrayList<>(apps));
            list.setSelection(0);
            return;
        }
        List<AppInfo> starts = new ArrayList<>();
        List<AppInfo> words = new ArrayList<>();
        List<AppInfo> contains = new ArrayList<>();
        for (AppInfo a : apps) {
            String l = a.label.toLowerCase(Locale.getDefault());
            if (l.startsWith(q)) starts.add(a);
            else if (l.contains(" " + q)) words.add(a);
            else if (l.contains(q)) contains.add(a);
        }
        List<AppInfo> ranked = new ArrayList<>(starts);
        ranked.addAll(words);
        ranked.addAll(contains);

        List<AppInfo> shown = new ArrayList<>();
        shown.add(AppInfo.webSearch(raw.trim()));
        for (int i = ranked.size() - 1; i >= 0; i--) shown.add(ranked.get(i));
        adapter.setItems(shown);
        list.setSelection(shown.size() - 1);
    }

    private void launchBestMatch() {
        int n = adapter.getCount();
        if (n == 0 || searchField.getText().toString().trim().isEmpty()) return;
        onRowTapped(adapter.getItem(n - 1));
    }

    private void onRowTapped(AppInfo app) {
        if (app.webQuery != null) {
            Intent web = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, app.webQuery);
            if (!launch(web)) {
                launch(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + Uri.encode(app.webQuery))));
            }
        } else {
            launchApp(app);
        }
    }

    // ---------------------------------------------------------------- menus

    private void showAppMenu(AppInfo app) {
        List<String> favs = getList(K_FAVS);
        int index = favs.indexOf(app.key);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (index < 0) {
            labels.add("Pin to home");
            actions.add(() -> { favs.add(app.key); saveFavs(favs); });
        } else {
            labels.add("Unpin from home");
            actions.add(() -> { favs.remove(app.key); saveFavs(favs); });
            if (index > 0) {
                labels.add("Move up");
                actions.add(() -> { Collections.swap(favs, index, index - 1); saveFavs(favs); });
            }
            if (index < favs.size() - 1) {
                labels.add("Move down");
                actions.add(() -> { Collections.swap(favs, index, index + 1); saveFavs(favs); });
            }
        }
        labels.add("Hide from app list");
        actions.add(() -> {
            List<String> hidden = getList(K_HIDDEN);
            if (!hidden.contains(app.key)) hidden.add(app.key);
            putList(K_HIDDEN, hidden);
            favs.remove(app.key);
            putList(K_FAVS, favs);
            applyApps();
            toast("Hidden. Long-press the home screen to unhide.");
        });
        labels.add("App info");
        actions.add(() -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.component.getPackageName()))));
        labels.add("Uninstall");
        actions.add(() -> launch(new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + app.component.getPackageName()))));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void saveFavs(List<String> favs) {
        putList(K_FAVS, favs);
        renderFavourites();
    }

    private void showSettings() {
        boolean left = prefs.getBoolean(K_LEFT, false);
        int hiddenCount = getList(K_HIDDEN).size();
        String[] items = {
                "Left-handed mode: " + (left ? "On" : "Off"),
                "Hidden apps (" + hiddenCount + ")",
                isDefaultHome() ? "Reach is your home app \u2713" : "Make Reach your home app",
                "Phone settings",
                "How to use Reach",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Reach")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            prefs.edit().putBoolean(K_LEFT, !left).apply();
                            applyHandedness();
                            break;
                        case 1:
                            showHiddenApps();
                            break;
                        case 2:
                            requestDefaultHome();
                            break;
                        case 3:
                            launch(new Intent(Settings.ACTION_SETTINGS));
                            break;
                        default:
                            showHelp();
                    }
                })
                .show();
    }

    private void showHiddenApps() {
        List<String> hidden = getList(K_HIDDEN);
        List<AppInfo> hiddenApps = new ArrayList<>();
        for (AppInfo a : everyApp) if (hidden.contains(a.key)) hiddenApps.add(a);
        if (hiddenApps.isEmpty()) {
            toast("No hidden apps");
            return;
        }
        String[] labels = new String[hiddenApps.size()];
        boolean[] checked = new boolean[hiddenApps.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = hiddenApps.get(i).label;
            checked[i] = true;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Untick to show again")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Done", (d, w) -> {
                    List<String> keep = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) keep.add(hiddenApps.get(i).key);
                    }
                    putList(K_HIDDEN, keep);
                    applyApps();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("How to use Reach")
                .setMessage("• Slide your thumb along the letters on the edge to jump to apps.\n\n"
                        + "• Swipe up, or tap Search, to type. Press Go to open the top result.\n\n"
                        + "• Swipe down to open notifications.\n\n"
                        + "• Long-press an app to pin, reorder, hide or uninstall it.\n\n"
                        + "• Long-press empty space for settings.\n\n"
                        + "• Tap the clock for alarms, the date for your calendar.\n\n"
                        + "Reach has no internet permission: no ads, no tracking.")
                .setPositiveButton("Got it", null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private void launchApp(AppInfo app) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        launch(i);
    }

    private boolean launch(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            toast("Couldn't open that");
            return false;
        }
    }

    private void expandNotifications() {
        try {
            @SuppressWarnings("WrongConstant")
            Object service = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(service);
        } catch (Throwable ignored) {
            // Some phones block this; nothing else to do.
        }
    }

    private Drawable copyOf(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(getResources()).mutate() : d;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(28));
        return g;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private List<String> getList(String key) {
        String s = prefs.getString(key, "");
        if (s == null || s.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    private void putList(String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join("\n", values)).apply();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
