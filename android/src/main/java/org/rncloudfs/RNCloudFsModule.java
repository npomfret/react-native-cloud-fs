package org.rncloudfs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static org.rncloudfs.GoogleDriveApiClient.resolve;

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.OnConnectionFailedListener, LifecycleEventListener, ActivityEventListener {
    public static final String TAG = "RNCloudFs";

    private static final int REQUEST_CODE_RESOLUTION = 3;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String DIALOG_ERROR = "dialog_error";
    private boolean isResolvingError = false;

    private final ReactApplicationContext reactContext;
    private GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
    }

    /**
     * android only method.  Just here to test the connection logic
     */
    @ReactMethod
    public void reset(final Promise promise) {
        final GoogleApiClient googleApiClient = this.googleApiClient;

        connect(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                googleApiClient.clearDefaultAccountAndReconnect();
                googleApiClient.unregisterConnectionCallbacks(this);
                promise.resolve(null);
            }

            @Override
            public void onConnectionSuspended(int i) {

            }
        });
    }

    @ReactMethod
    public void createFile(ReadableMap options, final Promise promise) {
        if (!options.hasKey("targetPath")) {
            promise.reject("error", "targetPath not specified");
        }
        final String path = options.getString("targetPath");

        if (!options.hasKey("content")) {
            promise.reject("error", "content not specified");
        }
        final String content = options.getString("content");

        final boolean useDocumentsFolder = options.hasKey("scope") ? options.getString("scope").toLowerCase().equals("visible") : true;

        connect(new CreateFileTask(path, promise, useDocumentsFolder, content));
    }

    @ReactMethod
    public void fileExists(ReadableMap options, final Promise promise) {
        if (!options.hasKey("targetPath")) {
            promise.reject("error", "targetPath not specified");
        }
        String path = options.getString("targetPath");

        boolean useDocumentsFolder = !options.hasKey("scope") || options.getString("scope").toLowerCase().equals("visible");

        connect(new FileExistsTask(useDocumentsFolder, path, promise));
    }

    @ReactMethod
    public void listFiles(ReadableMap options, final Promise promise) {
        if (!options.hasKey("targetPath")) {
            promise.reject("error", "targetPath not specified");
        }
        String path = options.getString("targetPath");

        boolean useDocumentsFolder = !options.hasKey("scope") || options.getString("scope").toLowerCase().equals("visible");

        connect(new ListFilesTask(useDocumentsFolder, path, promise));
    }

    /**
     * Copy the source into the google drive database
     */
    @ReactMethod
    public void copyToCloud(ReadableMap options, final Promise promise) {
        try {
            if (!options.hasKey("sourcePath")) {
                promise.reject("error", "sourcePath not specified");
            }
            ReadableMap source = options.getMap("sourcePath");
            String uriOrPath = source.hasKey("uri") ? source.getString("uri") : null;

            if (uriOrPath == null) {
                uriOrPath = source.hasKey("path") ? source.getString("path") : null;
            }

            if (uriOrPath == null) {
                promise.reject("no path", "no source uri or path was specified");
                return;
            }

            if (!options.hasKey("targetPath")) {
                promise.reject("error", "targetPath not specified");
            }
            final String destinationPath = options.getString("targetPath");

            String mimeType = null;
            if (options.hasKey("mimetype")) {
                mimeType = options.getString("mimetype");
            }

            boolean useDocumentsFolder = options.hasKey("scope") ? options.getString("scope").toLowerCase().equals("visible") : true;

            SourceUri sourceUri = new SourceUri(uriOrPath, source.hasKey("headers") ? source.getMap("headers") : null);

            String actualMimeType;
            if (mimeType == null) {
                actualMimeType = guessMimeType(uriOrPath);
            } else {
                actualMimeType = null;
            }

            connect(new CopyToGoogleDriveTask(
                    sourceUri,
                    destinationPath,
                    actualMimeType,
                    promise,
                    new GoogleDriveApiClient(this.googleApiClient, reactContext),
                    useDocumentsFolder)
            );

        } catch (Exception e) {
            Log.e(TAG, "Failed to copy", e);
            promise.reject("Failed to copy", e);
        }
    }

    @Nullable
    private static String guessMimeType(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        } else {
            return null;
        }
    }

    @Override
    public void onHostResume() {
        if (this.googleApiClient != null)
            googleApiClient.connect();
    }

    @Override
    public void onHostPause() {
        if (this.googleApiClient != null)
            this.googleApiClient.disconnect();
    }

    @Override
    public void onHostDestroy() {
        if (this.googleApiClient != null)
            this.googleApiClient.disconnect();
    }

    @Override
    public void onNewIntent(Intent intent) {
        System.out.println("RNCloudFsModule.onNewIntent");
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK)
            this.googleApiClient.connect();
        else if (requestCode == REQUEST_RESOLVE_ERROR) {
            isResolvingError = false;

            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!googleApiClient.isConnecting() && !googleApiClient.isConnected()) {
                    googleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (isResolvingError) {
            // Already attempting to resolve an error.
        } else if (result.hasResolution()) {
            try {
                isResolvingError = true;
                result.startResolutionForResult(getCurrentActivity(), REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                googleApiClient.connect();
            }
        } else {
            showErrorDialog(result.getErrorCode());
            isResolvingError = true;
        }
    }

    private void showErrorDialog(int errorCode) {
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);

        dialogFragment.setArguments(args);
        dialogFragment.show(getCurrentActivity().getFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        isResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            Activity activity = getActivity();

            //todo - call back to onDialogDismissed
        }
    }

    public interface InputDataSource {
        void copyToOutputStream(OutputStream output) throws IOException;
    }

    public class SourceUri implements InputDataSource {
        public final String uri;
        @Nullable
        private final ReadableMap httpHeaders;

        private SourceUri(String uri, @Nullable ReadableMap httpHeaders) {
            this.uri = uri;
            this.httpHeaders = httpHeaders;
        }

        private InputStream read() throws IOException {
            if (uri.startsWith("/") || uri.startsWith("file:/")) {
                String path = uri.replaceFirst("^file\\:/+", "/");
                File file = new File(path);
                return new FileInputStream(file);
            } else if (uri.startsWith("content://")) {
                return RNCloudFsModule.this.reactContext.getContentResolver().openInputStream(Uri.parse(uri));
            } else {
                HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();

                if (httpHeaders != null) {
                    ReadableMapKeySetIterator readableMapKeySetIterator = httpHeaders.keySetIterator();
                    while (readableMapKeySetIterator.hasNextKey()) {
                        String key = readableMapKeySetIterator.nextKey();
                        if (key == null)
                            continue;
                        String value = httpHeaders.getString(key);
                        if (value == null)
                            continue;
                        conn.setRequestProperty(key, value);
                    }
                }

                conn.setRequestMethod("GET");

                return conn.getInputStream();
            }
        }

        public void copyToOutputStream(OutputStream output) throws IOException {
            InputStream input = read();
            if (input == null)
                throw new IllegalStateException("Cannot read " + uri);

            try {
                byte[] buffer = new byte[256];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            } finally {
                input.close();
            }
        }
    }

    @NonNull
    private void connect(GoogleApiClient.ConnectionCallbacks listener) {
        if (googleApiClient == null) {
            synchronized (this) {
                googleApiClient = new GoogleApiClient.Builder(reactContext)
                        .addApi(Drive.API)
                        .addScope(Drive.SCOPE_FILE)
                        .addScope(Drive.SCOPE_APPFOLDER)
                        .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(@Nullable Bundle bundle) {
                                Log.i(TAG, "Google client API connected");
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                //what to do here??
                                Log.w(TAG, "Google client API suspended: " + i);
                            }
                        })
                        .addOnConnectionFailedListener(this)
                        .build();
            }
        }

        this.googleApiClient.registerConnectionCallbacks(listener);
        this.googleApiClient.connect();
    }

    @Override
    public String getName() {
        return "RNCloudFs";
    }

    private class ListFilesTask implements GoogleApiClient.ConnectionCallbacks {
        private final boolean useDocumentsFolder;
        private final String path;
        private final Promise promise;

        public ListFilesTask(boolean useDocumentsFolder, String path, Promise promise) {
            this.useDocumentsFolder = useDocumentsFolder;
            this.path = path;
            this.promise = promise;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            final GoogleDriveApiClient googleDriveApiClient = new GoogleDriveApiClient(RNCloudFsModule.this.googleApiClient, reactContext);

            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        WritableMap data = googleDriveApiClient.listFiles(useDocumentsFolder, resolve(path));
                        promise.resolve(data);
                    } catch (Exception e) {
                        promise.reject("error", e);
                    }
                }
            });

            googleDriveApiClient.unregisterListener(this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.w(TAG, "Google client API suspended: " + i);
        }
    }

    private class CreateFileTask implements GoogleApiClient.ConnectionCallbacks {
        private final String path;
        private final Promise promise;
        private final boolean useDocumentsFolder;
        private final String content;

        public CreateFileTask(String path, Promise promise, boolean useDocumentsFolder, String content) {
            this.path = path;
            this.promise = promise;
            this.useDocumentsFolder = useDocumentsFolder;
            this.content = content;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            final GoogleDriveApiClient googleDriveApiClient = new GoogleDriveApiClient(RNCloudFsModule.this.googleApiClient, reactContext);

            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {

                        List<String> pathParts = resolve(path);
                        if (pathParts.size() == 0) {
                            promise.reject("error", "no filename specified");
                            return;
                        }

                        DriveFolder parentFolder = useDocumentsFolder ? googleDriveApiClient.documentsFolder() : googleDriveApiClient.appFolder();
                        if (pathParts.size() > 1) {
                            List<String> parentDirs = pathParts.subList(0, pathParts.size() - 1);
                            parentFolder = googleDriveApiClient.createFolders(parentFolder, parentDirs);
                        }

                        String filename = pathParts.get(pathParts.size() - 1);

                        String outputFilename = googleDriveApiClient.createFile(parentFolder, new InputDataSource() {
                            @Override
                            public void copyToOutputStream(OutputStream output) throws IOException {
                                output.write(content.getBytes("UTF-8"));
                            }
                        }, filename);

                        promise.resolve(outputFilename);
                    } catch (Exception e) {
                        promise.reject("error", e);
                    }

                }
            });

            googleDriveApiClient.unregisterListener(this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.w(TAG, "Google client API suspended: " + i);
        }
    }

    private class FileExistsTask implements GoogleApiClient.ConnectionCallbacks {
        private final boolean useDocumentsFolder;
        private final String path;
        private final Promise promise;

        public FileExistsTask(boolean useDocumentsFolder, String path, Promise promise) {
            this.useDocumentsFolder = useDocumentsFolder;
            this.path = path;
            this.promise = promise;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            final GoogleDriveApiClient googleDriveApiClient = new GoogleDriveApiClient(RNCloudFsModule.this.googleApiClient, reactContext);

            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean fileExists = googleDriveApiClient.fileExists(useDocumentsFolder, resolve(path));
                        promise.resolve(fileExists);
                    } catch (Exception e) {
                        promise.reject("error", e);
                    }
                }
            });

            googleDriveApiClient.unregisterListener(this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.w(TAG, "Google client API suspended: " + i);
        }
    }
}
