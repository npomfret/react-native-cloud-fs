package org.rncloudfs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.facebook.react.bridge.WritableNativeMap;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.app.Activity.RESULT_OK;

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.OnConnectionFailedListener, LifecycleEventListener, ActivityEventListener {
    public static final String TAG = "RNCloudFs";
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private final ReactApplicationContext reactContext;
    private GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
    }

    @ReactMethod
    public void listFiles(String path, Promise promise) {
        GoogleApiClient googleApiClient = onClientConnected();
        googleApiClient.blockingConnect();

        new ListFilesTask(googleApiClient, promise).execute(path);
    }

    /**
     * Copy the source into the google drive database using
     *
     * @param source          contains a string keyed by 'uri' (or 'path') along with an optional map ('http-headers') containing http headers
     * @param destinationPath a relative path under which the file will be stored
     * @param mimeType        an optional mime-type for the database, if null a guess will be made
     */
    @ReactMethod
    public void copyToCloud(ReadableMap source, String destinationPath, @Nullable String mimeType, Promise promise) {
        try {
            String uriOrPath = source.hasKey("uri") ? source.getString("uri") : null;

            if (uriOrPath == null) {
                uriOrPath = source.hasKey("path") ? source.getString("path") : null;
            }

            if (uriOrPath == null) {
                promise.reject("no path", "no source uri or path was specified");
                return;
            }

            SourceUri sourceUri = new SourceUri(uriOrPath, source.hasKey("http-headers") ? source.getMap("http-headers") : null);

            String actualMimeType;
            if (mimeType == null) {
                actualMimeType = guessMimeType(uriOrPath);
            } else {
                actualMimeType = null;
            }

            String folder = getApplicationName(reactContext) + "/" + destinationPath;

            GoogleApiClient googleApiClient = onClientConnected();
            googleApiClient.blockingConnect();

            // needs to be an async task because it may do some network access
            CopyToGoogleDriveTask copyToGoogleDriveTask = new CopyToGoogleDriveTask(
                    googleApiClient,
                    folder,
                    actualMimeType,
                    promise
            );

            copyToGoogleDriveTask.execute(sourceUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy", e);
            promise.reject("Failed to copy", e);
        }
    }

    private GoogleApiClient onClientConnected() {
        if (googleApiClient == null) {
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
        return googleApiClient;
    }

    private static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK)
            this.googleApiClient.connect();
    }

    // copied from BaseActivityEventListener
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        this.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.w(TAG, "Google client API connection failed: " + result.toString());

        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this.getCurrentActivity(), result.getErrorCode(), 0).show();
            return;
        }

        try {
            result.startResolutionForResult(this.getCurrentActivity(), REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    public class SourceUri {
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
    private static WritableNativeMap fileToMap(File file) {
        WritableNativeMap fileMap = new WritableNativeMap();
        fileMap.putString("name", file.getName());
        fileMap.putString("path", file.getAbsolutePath());
        fileMap.putBoolean("isDirectory", file.isDirectory());
        fileMap.putBoolean("isFile", file.isFile());
        fileMap.putInt("size", (int) file.length());
        return fileMap;
    }

    @Override
    public String getName() {
        return "RNCloudFs";
    }
}
