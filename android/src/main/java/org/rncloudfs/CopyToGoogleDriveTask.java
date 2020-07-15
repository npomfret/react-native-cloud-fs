package org.rncloudfs;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.DriveFolder;

import java.io.IOException;
import java.util.List;

import static org.rncloudfs.GoogleDriveApiClient.resolve;
import static org.rncloudfs.RNCloudFsModule.TAG;

public class CopyToGoogleDriveTask implements GoogleApiClient.ConnectionCallbacks {
    private RNCloudFsModule.SourceUri sourceUri;
    private final String outputPath;
    @Nullable
    private final String mimeType;
    private final Promise promise;
    private final GoogleDriveApiClient googleApiClient;
    private final boolean useDocumentsFolder;

    public CopyToGoogleDriveTask(RNCloudFsModule.SourceUri sourceUri, String outputPath, @Nullable String mimeType, Promise promise, GoogleDriveApiClient googleDriveApiClient, boolean useDocumentsFolder) {
        this.sourceUri = sourceUri;
        this.outputPath = outputPath;
        this.mimeType = mimeType;
        this.promise = promise;
        this.googleApiClient = googleDriveApiClient;
        this.useDocumentsFolder = useDocumentsFolder;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                List<String> pathParts = resolve(outputPath);

                try {
                    DriveFolder rootFolder = useDocumentsFolder ? googleApiClient.documentsFolder() : googleApiClient.appFolder();
                    createFileInFolders(rootFolder, pathParts, sourceUri);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write " + outputPath, e);
                    promise.reject("Failed copy '" + sourceUri.uri + "' to " + outputPath, e);
                }
            }
        });

        googleApiClient.unregisterListener(this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private void createFileInFolders(DriveFolder parentFolder, List<String> pathParts, RNCloudFsModule.SourceUri sourceUri) {
        if (pathParts.size() > 1)
            parentFolder = googleApiClient.createFolders(parentFolder, pathParts.subList(0, pathParts.size() - 1));

        try {
            String fileName = googleApiClient.createFile(parentFolder, sourceUri, pathParts.get(0), mimeType);
            promise.resolve(fileName);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create file from " + sourceUri, e);
            promise.reject("Failed to read input", e);
        }
    }
}
