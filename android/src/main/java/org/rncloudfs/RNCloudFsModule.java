package org.rncloudfs;

import android.content.Context;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "RNCloudFs";
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private final ReactApplicationContext reactContext;
    private GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        googleApiClient = new GoogleApiClient.Builder(reactContext)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addScope(Drive.SCOPE_APPFOLDER)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        googleApiClient.connect();
    }

    @ReactMethod
    public void copyToCloud(final String sourceUri, final String destinationPath, @Nullable String mimeType, final Promise promise) {
        if(mimeType == null) {
            mimeType = guessMimeType(sourceUri);
        }

        String folder = getApplicationName(reactContext) + "/" + destinationPath;

        List<String> names = new ArrayList<>();
        for (String name : folder.split("/")) {
            if(name.trim().length() > 0) {
                names.add(name);
            }
        }

        createFileInFolders(Drive.DriveApi.getRootFolder(googleApiClient), names, new SourceUri(sourceUri), mimeType, promise);
    }

    private void createFileInFolders(DriveFolder parentFolder, final List<String> pathParts, final SourceUri sourceUri, @Nullable final String mimeType, final Promise promise) {
        if(pathParts.size() > 1) {
            final String name = pathParts.remove(0);

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(name)
                    .build();

            parentFolder.createFolder(googleApiClient, changeSet)
                    .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                        @Override
                        public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                            Log.d(TAG, "Created folder '" + name + "'");

                            createFileInFolders(result.getDriveFolder(), pathParts, sourceUri, mimeType, promise);
                        }
                    });
        } else {
            createFileInFolder(parentFolder, sourceUri, pathParts.get(0), mimeType, promise);
        }
    }

    private void createFileInFolder(final DriveFolder driveFolder, final SourceUri sourceUri, final String filename, @Nullable final String mimeType, final Promise promise) {
        Drive.DriveApi.newDriveContents(googleApiClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.e(TAG, "Failed to create new content");
                    promise.reject("Failed to create new content", "Failed to create new content");
                    return;
                }

                try {
                    DriveContents driveContents = result.getDriveContents();

                    OutputStream outputStream = driveContents.getOutputStream();
                    sourceUri.copyToOutputStream(outputStream);
                    outputStream.close();

                    MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder()
                            .setTitle(filename);

                    if(mimeType != null) {
                        builder.setMimeType(mimeType);
                    }

                    driveFolder.createFile(googleApiClient, builder.build(), driveContents)
                            .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                @Override
                                public void onResult(@NonNull DriveFolder.DriveFileResult driveFileResult) {
                                    System.out.println("Created a file with content: " + driveFileResult.getDriveFile().getDriveId());
                                }
                            });

                    promise.resolve(null);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to read " + sourceUri, e);
                    promise.reject("Failed to read input", e);
                }
            }
        });
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

    private class SourceUri {
        private final String sourceUri;

        private SourceUri(String sourceUri) {
            this.sourceUri = sourceUri;
        }

        private InputStream read() throws IOException {
            if (sourceUri.startsWith("/") || sourceUri.startsWith("file:/")) {
                String path = sourceUri.replaceFirst("^file\\:/+", "/");
                File file = new File(path);
                return new FileInputStream(file);
            } else if (sourceUri.startsWith("content://")) {
                return RNCloudFsModule.this.reactContext.getContentResolver().openInputStream(Uri.parse(sourceUri));
            } else {
                URLConnection urlConnection = new URL(sourceUri).openConnection();
                return urlConnection.getInputStream();
            }
        }

        private void copyToOutputStream(OutputStream output) throws IOException {
            InputStream input = read();
            if(input == null)
                throw new IllegalStateException("Cannot read " + sourceUri);

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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "API client connected.");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());

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

    @Override
    public String getName() {
        return "RNCloudFs";
    }

}