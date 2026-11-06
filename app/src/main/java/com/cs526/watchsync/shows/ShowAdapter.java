package com.cs526.watchsync.shows;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cs526.watchsync.R;
import com.cs526.watchsync.model.Show;
import com.cs526.watchsync.util.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** One list design for every list of shows. Each screen decides subtitle / button / progress in bind(). */
public class ShowAdapter extends RecyclerView.Adapter<ShowAdapter.VH> {

    public interface Binder {
        void bind(Show show, VH holder);
    }

    public interface Listener {
        void on(Show show);
    }

    private final List<Show> items = new ArrayList<>();
    private final Binder binder;
    private Listener onClick;
    private Listener onLongClick;
    private Listener onAction;

    public ShowAdapter(Binder binder) {
        this.binder = binder;
    }

    public void setItems(List<Show> shows) {
        items.clear();
        items.addAll(shows);
        notifyDataSetChanged();
    }

    public List<Show> getItems() {
        return items;
    }

    public void setOnClick(Listener l) { onClick = l; }
    public void setOnLongClick(Listener l) { onLongClick = l; }
    public void setOnAction(Listener l) { onAction = l; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_show, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Show s = items.get(position);
        h.title.setText(s.title);
        h.subtitle.setText(s.summary());
        h.progress.setVisibility(View.GONE);
        h.action.setVisibility(View.GONE);
        h.action.setEnabled(true);
        Ui.poster(h.poster, s.posterPath);
        if (binder != null) binder.bind(s, h);

        h.itemView.setOnClickListener(v -> { if (onClick != null) onClick.on(s); });
        h.itemView.setOnLongClickListener(v -> {
            if (onLongClick == null) return false;
            onLongClick.on(s);
            return true;
        });
        h.action.setOnClickListener(v -> { if (onAction != null) onAction.on(s); });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final ImageView poster;
        public final TextView title;
        public final TextView subtitle;
        public final LinearProgressIndicator progress;
        public final MaterialButton action;

        VH(View v) {
            super(v);
            poster = v.findViewById(R.id.ivPoster);
            title = v.findViewById(R.id.tvTitle);
            subtitle = v.findViewById(R.id.tvSubtitle);
            progress = v.findViewById(R.id.pbProgress);
            action = v.findViewById(R.id.btnAction);
        }

        /** Shows the progress bar for a show. */
        public void showProgress(Show s) {
            int max = s.movie() ? 1 : Math.max(1, s.totalEpisodes);
            progress.setMax(max);
            progress.setProgress(Math.min(s.progress, max));
            progress.setVisibility(View.VISIBLE);
        }

        public void showAction(String text) {
            action.setText(text);
            action.setVisibility(View.VISIBLE);
        }
    }
}
