package com.txtify.app;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    private final List<DocumentFile> files;
    private final Context context;
    private final OnItemClickListener clickListener;
    private final OnDragStartListener dragListener;

    // Master selection set of URIs from FileBrowserDialogFragment
    private final Set<Uri> selectedFiles;
    private final int selectionColor;

    // Lightweight metadata cache to keep SAF sorting and scrolling fast
    private final Map<Uri, String> nameCache = new HashMap<>();
    private final Map<Uri, Boolean> dirCache = new HashMap<>();

    public interface OnItemClickListener {
        void onItemClick(DocumentFile file);
    }

    public interface OnDragStartListener {
        void onDragStarted();
    }

    public FileBrowserAdapter(Context context, List<DocumentFile> files, Set<Uri> selectedFiles, OnItemClickListener clickListener, OnDragStartListener dragListener) {
        this.context = context;
        this.files = files;
        this.selectedFiles = selectedFiles;
        this.clickListener = clickListener;
        this.dragListener = dragListener;
        this.selectionColor = 0x5000ACC1;

        for (DocumentFile doc : this.files) {
            Uri uri = doc.getUri();
            String name = doc.getName();
            nameCache.put(uri, name != null ? name : "");
            dirCache.put(uri, doc.isDirectory());
        }

        Collections.sort(this.files, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile f1, DocumentFile f2) {
                boolean d1 = isDir(f1);
                boolean d2 = isDir(f2);
                if (d1 && !d2) {
                    return -1;
                } else if (!d1 && d2) {
                    return 1;
                } else {
                    return getFileName(f1).compareToIgnoreCase(getFileName(f2));
                }
            }
        });
    }

    private String getFileName(DocumentFile doc) {
        Uri uri = doc.getUri();
        String cached = nameCache.get(uri);
        if (cached == null) {
            cached = doc.getName();
            if (cached == null) cached = "";
            nameCache.put(uri, cached);
        }
        return cached;
    }

    private boolean isDir(DocumentFile doc) {
        Uri uri = doc.getUri();
        Boolean cached = dirCache.get(uri);
        if (cached == null) {
            cached = doc.isDirectory();
            dirCache.put(uri, cached);
        }
        return cached;
    }

    // Adds all files (not directories) from the current list to the master set
    public void selectAllFiles() {
        for (DocumentFile file : files) {
            if (!isDir(file)) {
                selectedFiles.add(file.getUri());
            }
        }
        notifyDataSetChanged();
    }

    // Clears the entire master selection set
    public void clearSelection() {
        selectedFiles.clear();
        notifyDataSetChanged();
    }

    // Selects files in current directory list matching the target names
    public int selectMatchingFiles(Set<String> targetNames) {
        if (targetNames == null || targetNames.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (DocumentFile file : files) {
            if (!isDir(file) && FileNameParserUtils.matchesAny(getFileName(file), targetNames)) {
                selectedFiles.add(file.getUri());
                count++;
            }
        }
        notifyDataSetChanged();
        return count;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentFile file = files.get(position);
        holder.bind(file, clickListener, dragListener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon_file_type);
            name = itemView.findViewById(R.id.text_file_name);
        }

        public void bind(final DocumentFile file, final OnItemClickListener clickListener, final OnDragStartListener dragListener) {
            final Uri fileUri = file.getUri();
            final boolean directory = isDir(file);
            name.setText(getFileName(file));

            if (directory) {
                icon.setImageResource(R.drawable.ic_menu_browse);
            } else {
                icon.setImageResource(R.drawable.ic_file_generic);
            }

            if (selectedFiles.contains(fileUri)) {
                itemView.setBackgroundColor(selectionColor);
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (directory) {
                        clickListener.onItemClick(file);
                    } else {
                        if (selectedFiles.contains(fileUri)) {
                            selectedFiles.remove(fileUri);
                        } else {
                            selectedFiles.add(fileUri);
                        }
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(pos);
                        }
                    }
                }
            });

            if (!directory) {
                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        List<Uri> urisToDrag = new ArrayList<>();

                        if (!selectedFiles.isEmpty() && selectedFiles.contains(fileUri)) {
                            urisToDrag.addAll(selectedFiles);
                        } else {
                            urisToDrag.add(fileUri);
                        }

                        if (urisToDrag.isEmpty()) {
                            return false;
                        }

                        StringBuilder uriClip = new StringBuilder();
                        for (int i = 0; i < urisToDrag.size(); i++) {
                            uriClip.append(urisToDrag.get(i).toString());
                            if (i < urisToDrag.size() - 1) {
                                uriClip.append("\n");
                            }
                        }

                        ClipData.Item item = new ClipData.Item(uriClip.toString());
                        ClipData dragData = new ClipData(
                                "file_paths",
                                new String[]{"text/plain"},
                                item
                        );

                        View.DragShadowBuilder myShadow = new View.DragShadowBuilder(itemView);
                        v.startDragAndDrop(dragData, myShadow, null, View.DRAG_FLAG_GLOBAL);

                        if (dragListener != null) {
                            dragListener.onDragStarted();
                        }

                        return true;
                    }
                });
            } else {
                itemView.setOnLongClickListener(null);
            }
        }
    }
}