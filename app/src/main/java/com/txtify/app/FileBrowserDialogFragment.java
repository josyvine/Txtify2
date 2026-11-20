package com.txtify.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileBrowserDialogFragment extends DialogFragment {

    public interface FileBrowserCallbacks {
        void onFilesAdded(ArrayList<Uri> files);
        void onBrowserClosed();
    }

    private static final int REQUEST_CODE_PICK_BROWSE_FOLDER = 201;
    private static final String PREFS_NAME = "TxtifyPrefs";
    private static final String KEY_BROWSER_ROOT_URI = "browserRootFolderUri";

    private RecyclerView recyclerView;
    private TextView currentPathText;
    private TextView emptyFolderText;
    private ImageButton upButton;
    private ImageButton addSelectedButton;
    private Button selectAllButton;
    private Button clearSelectionButton;
    private Button closeButton;
    private Button pasteSearchButton;

    private DocumentFile currentDirectory;
    private final Stack<DocumentFile> directoryHistory = new Stack<>();
    private FileBrowserCallbacks callbacks;
    private FileBrowserAdapter adapter;

    // Master set of selected file URIs owned by the fragment
    private final Set<Uri> selectedFiles = new HashSet<>();

    private ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static FileBrowserDialogFragment newInstance() {
        return new FileBrowserDialogFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FileBrowserCallbacks) {
            callbacks = (FileBrowserCallbacks) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement FileBrowserCallbacks");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        executorService = Executors.newSingleThreadExecutor();
        return inflater.inflate(R.layout.fragment_file_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null) {
            getDialog().setTitle("Select and Drag Files");
        }

        recyclerView = view.findViewById(R.id.recycler_view_files);
        currentPathText = view.findViewById(R.id.text_current_path);
        emptyFolderText = view.findViewById(R.id.text_empty_folder);
        upButton = view.findViewById(R.id.button_up_directory);
        addSelectedButton = view.findViewById(R.id.button_add_selected);
        selectAllButton = view.findViewById(R.id.button_select_all);
        clearSelectionButton = view.findViewById(R.id.button_clear_selection);
        closeButton = view.findViewById(R.id.button_close_browser);
        pasteSearchButton = view.findViewById(R.id.button_paste_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Allow tapping the path bar anytime to switch the base folder
        currentPathText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestFolderAccess();
            }
        });

        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!directoryHistory.isEmpty()) {
                    navigateTo(directoryHistory.pop(), false);
                } else if (currentDirectory != null && currentDirectory.getParentFile() != null) {
                    navigateTo(currentDirectory.getParentFile(), false);
                } else {
                    Toast.makeText(getContext(), "At root folder. Tap path to switch folder.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        addSelectedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callbacks != null && !selectedFiles.isEmpty()) {
                    callbacks.onFilesAdded(new ArrayList<>(selectedFiles));
                    if (adapter != null) {
                        adapter.clearSelection();
                    }
                } else {
                    Toast.makeText(getContext(), "No files selected.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        selectAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapter != null) {
                    adapter.selectAllFiles();
                }
            }
        });

        clearSelectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapter != null) {
                    adapter.clearSelection();
                }
            }
        });

        if (pasteSearchButton != null) {
            pasteSearchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPasteSearchDialog();
                }
            });
        }

        checkAndOpenRootFolder();
    }

    /**
     * Shows the paste dialog where user pastes file names list.
     * Launches a recursive search starting from the current directory.
     */
    private void showPasteSearchDialog() {
        if (currentDirectory == null) {
            Toast.makeText(getContext(), "Please select a parent directory first.", Toast.LENGTH_SHORT).show();
            return;
        }

        PasteListDialogFragment dialog = PasteListDialogFragment.newInstance();
        dialog.setOnListPastedListener(new PasteListDialogFragment.OnListPastedListener() {
            @Override
            public void onListPasted(Set<String> parsedFileNames) {
                new RecursiveSearchTask(currentDirectory, parsedFileNames).execute();
            }
        });
        dialog.show(getChildFragmentManager(), "paste_search_dialog");
    }

    /**
     * AsyncTask to recursively traverse all subfolders under currentDirectory
     * and select any matching files automatically.
     */
    private class RecursiveSearchTask extends AsyncTask<Void, Void, List<Uri>> {
        private final DocumentFile parentDir;
        private final Set<String> targetNames;
        private ProgressDialog progressDialog;

        public RecursiveSearchTask(DocumentFile parentDir, Set<String> targetNames) {
            this.parentDir = parentDir;
            this.targetNames = targetNames;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage("Searching subfolders for matching files...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected List<Uri> doInBackground(Void... voids) {
            List<Uri> matchedFiles = new ArrayList<>();
            searchFolderRecursively(parentDir, targetNames, matchedFiles);
            return matchedFiles;
        }

        private void searchFolderRecursively(DocumentFile dir, Set<String> targets, List<Uri> result) {
            if (dir == null || !dir.exists() || !dir.isDirectory() || !dir.canRead()) {
                return;
            }

            DocumentFile[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (DocumentFile file : files) {
                if (file.isDirectory()) {
                    searchFolderRecursively(file, targets, result);
                } else if (file.isFile()) {
                    String name = file.getName();
                    if (name != null && FileNameParserUtils.matchesAny(name, targets)) {
                        result.add(file.getUri());
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(List<Uri> matchedFiles) {
            super.onPostExecute(matchedFiles);
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (matchedFiles.isEmpty()) {
                Toast.makeText(getContext(), "No matching files found in subfolders.", Toast.LENGTH_LONG).show();
            } else {
                selectedFiles.addAll(matchedFiles);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(getContext(), "Found & selected " + matchedFiles.size() + " file(s) across subfolders.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (callbacks != null) {
            callbacks.onBrowserClosed();
        }
    }

    private void navigateTo(final DocumentFile directory, boolean addToHistory) {
        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            Toast.makeText(getContext(), "Cannot access this folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (addToHistory && currentDirectory != null) {
            directoryHistory.push(currentDirectory);
        }

        currentDirectory = directory;
        String dirName = directory.getName();
        currentPathText.setText(dirName != null ? dirName + " (tap to switch)" : "Selected Folder");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                DocumentFile[] filesArray = directory.listFiles();
                final List<DocumentFile> files = (filesArray != null)
                        ? new ArrayList<>(Arrays.asList(filesArray))
                        : new ArrayList<DocumentFile>();

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || getContext() == null) return;

                        if (files.isEmpty()) {
                            emptyFolderText.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            emptyFolderText.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }

                        adapter = new FileBrowserAdapter(
                                getContext(),
                                files,
                                selectedFiles,
                                new FileBrowserAdapter.OnItemClickListener() {
                                    @Override
                                    public void onItemClick(DocumentFile file) {
                                        if (file.isDirectory()) {
                                            navigateTo(file, true);
                                        }
                                    }
                                },
                                new FileBrowserAdapter.OnDragStartListener() {
                                    @Override
                                    public void onDragStarted() {
                                        dismiss();
                                    }
                                }
                        );

                        recyclerView.setAdapter(adapter);
                    }
                });
            }
        });
    }

    private void checkAndOpenRootFolder() {
        Context context = getContext();
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedUri = prefs.getString(KEY_BROWSER_ROOT_URI, null);

        if (savedUri != null) {
            try {
                DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(savedUri));
                if (root != null && root.isDirectory() && root.canRead()) {
                    navigateTo(root, false);
                    return;
                }
            } catch (Exception ignored) {
            }
            prefs.edit().remove(KEY_BROWSER_ROOT_URI).apply();
        }

        requestFolderAccess();
    }

    private void requestFolderAccess() {
        Toast.makeText(getContext(), "Select a folder to browse.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_BROWSE_FOLDER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_BROWSE_FOLDER) {
            Context context = getContext();
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && context != null) {
                Uri treeUri = data.getData();
                try {
                    context.getContentResolver().takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_BROWSER_ROOT_URI, treeUri.toString())
                        .apply();

                DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
                if (root != null && root.isDirectory()) {
                    directoryHistory.clear();
                    navigateTo(root, false);
                } else {
                    Toast.makeText(context, "Could not open selected folder.", Toast.LENGTH_LONG).show();
                    if (currentDirectory == null) dismiss();
                }
            } else if (currentDirectory == null) {
                Toast.makeText(getContext(), "Please select a folder to browse files.", Toast.LENGTH_LONG).show();
                dismiss();
            }
        }
    }
}