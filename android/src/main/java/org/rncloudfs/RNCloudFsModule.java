package org.rncloudfs;

import android.content.Context;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "RNCloudFs";
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private final ReactApplicationContext reactContext;
    private GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    public void copyToICloud(final String sourceUri, final String targetRelativePath, final Promise promise) {
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(getCurrentActivity())
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            googleApiClient.connect();
        }

        final String folder = getApplicationName(reactContext) + "/" + targetRelativePath;

        try {
            int i = folder.lastIndexOf('/');
            String dir = folder.substring(0, i);

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(dir)
                    .build();

            Drive.DriveApi.getRootFolder(googleApiClient)
                    .createFolder(googleApiClient, changeSet)
                    .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                        @Override
                        public void onResult(@NonNull DriveFolder.DriveFolderResult driveFolderResult) {
                            DriveFolder driveFolder = driveFolderResult.getDriveFolder();

                            createFileInFolder(driveFolder, sourceUri, promise, folder);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Failed to save file", e);
            promise.reject("failed", e.getMessage());
        }
    }

    private void createFileInFolder(final DriveFolder driveFolder, final String sourceUri, final Promise promise, final String targetRelativePath) {
        Drive.DriveApi.newDriveContents(googleApiClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    Log.i(TAG, "Failed to create new contents.");
                    return;
                }

                Log.i(TAG, "New contents created.");

                try {
                    InputStream inputStream = read(sourceUri);
                    if (inputStream == null) {
                        promise.reject("Failed to read input", sourceUri);
                        return;
                    }

                    DriveContents driveContents = result.getDriveContents();
                    OutputStream outputStream = driveContents.getOutputStream();

                    copyInputStreamToOutputStream(inputStream, outputStream);
                    outputStream.close();
                    inputStream.close();

                    int i = targetRelativePath.lastIndexOf('/');
                    String filename;
                    if (i < 0) {
                        filename = targetRelativePath;
                    } else {
                        filename = targetRelativePath.substring(i + 1);
                    }

                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                            .setTitle(filename)
                            .setMimeType("text/plain")
                            .setStarred(true)
                            .build();

                    driveFolder.createFile(googleApiClient, changeSet, driveContents)
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
                    return;
                }
            }
        });
    }

    private static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    private InputStream read(String sourceUri) throws IOException {
        final InputStream inputStream;
        if (sourceUri.startsWith("/") || sourceUri.startsWith("file:/")) {
            String path = sourceUri.replaceFirst("^file\\:/+", "/");
            File file = new File(path);
            inputStream = new FileInputStream(file);
        } else if (sourceUri.startsWith("content://")) {
            inputStream = RNCloudFsModule.this.reactContext.getContentResolver().openInputStream(Uri.parse(sourceUri));
        } else {
            URLConnection urlConnection = new URL(sourceUri).openConnection();
            inputStream = urlConnection.getInputStream();
        }
        return inputStream;
    }

    private static void copyInputStreamToOutputStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[256];
        int bytesRead = 0;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
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