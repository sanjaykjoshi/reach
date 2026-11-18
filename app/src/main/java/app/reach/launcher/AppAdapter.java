package app.reach.launcher;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Rows for the letter panel and the search results. */
final class AppAdapter extends BaseAdapter {

    private final Context context;
    private final float dp;
    private List<AppInfo> items = new ArrayList<>();
    private boolean alignEnd;

    AppAdapter(Context context) {
        this.context = context;
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    void setItems(List<AppInfo> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    void setAlignEnd(boolean alignEnd) {
        this.alignEnd = alignEnd;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final class Holder {
        ImageView icon;
        TextView label;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        LinearLayout row;
        if (convertView == null) {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(24), px(11), px(24), px(11));

            h = new Holder();
            h.icon = new ImageView(context);
            row.addView(h.icon, new LinearLayout.LayoutParams(px(36), px(36)));

            h.label = new TextView(context);
            h.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            h.label.setTextColor(Color.WHITE);
            h.label.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(px(18));
            row.addView(h.label, lp);
            row.setTag(h);
        } else {
            row = (LinearLayout) convertView;
            h = (Holder) row.getTag();
        }

        // Left-handed mode mirrors the row so it hugs the right edge.
        row.setLayoutDirection(alignEnd ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        h.label.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);

        AppInfo app = items.get(position);
        h.label.setText(app.label);
        if (app.webQuery != null) {
            h.icon.setImageResource(android.R.drawable.ic_menu_search);
            h.icon.setColorFilter(Color.WHITE);
            h.label.setTextColor(0xFF8AB4F8);
        } else {
            h.icon.clearColorFilter();
            h.icon.setImageDrawable(app.icon);
            h.label.setTextColor(Color.WHITE);
        }
        return row;
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
