package org.rncloudfs;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.rncloudfs.RNCloudFsModule.TAG;

public class CopyToGoogleDriveTask extends AsyncTask<RNCloudFsModule.SourceUri, Void, Void> {
    private final String outputPath;
    @Nullable
    private final String mimeType;
    private final Promise promise;
    private final GoogleDriveApiClient googleApiClient;

    public CopyToGoogleDriveTask(GoogleApiClient googleApiClient, String outputPath, @Nullable String mimeType, Promise promise) {
        this.outputPath = outputPath;
        this.mimeType = mimeType;
        this.promise = promise;
        this.googleApiClient = new GoogleDriveApiClient(googleApiClient);
    }

    @Override
    protected Void doInBackground(RNCloudFsModule.SourceUri... params) {
        List<String> parthParts = new ArrayList<>();
        for (String name : outputPath.split("/")) {
            if (name.trim().length() > 0) {
                parthParts.add(name);
            }
        }

        RNCloudFsModule.SourceUri sourceUri = params[0];
        try {
            DriveFolder rootFolder = googleApiClient.appFolder();
            createFileInFolders(rootFolder, parthParts, sourceUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write " + outputPath, e);
            promise.reject("Failed copy '" + sourceUri.uri + "' to " + outputPath, e);
        }

        return null;
    }

    private void createFileInFolders(DriveFolder parentFolder, List<String> pathParts, RNCloudFsModule.SourceUri sourceUri) {
        if (pathParts.size() > 1) {
            String name = pathParts.remove(0);

            DriveFolder folder = googleApiClient.folder(parentFolder, name);

            if (folder == null) {
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(name)
                        .build();

                DriveFolder.DriveFolderResult result = googleApiClient.createFolder(parentFolder, changeSet);

                Log.i(TAG, "Created folder '" + name + "'");

                createFileInFolders(result.getDriveFolder(), pathParts, sourceUri);
            } else {
                Log.d(TAG, "Folder already exists '" + name + "'");

                createFileInFolders(folder, pathParts, sourceUri);
            }

        } else {
            try {
                Result result = googleApiClient.createFileInFolder(parentFolder, sourceUri, pathParts.get(0), mimeType);
                if (!result.getStatus().isSuccess()) {
                    Log.e(TAG, "Failed to create new content");
                    promise.reject("Failed to create new content", "Failed to create new content");
                } else {
                    if (result instanceof DriveFolder.DriveFileResult) {
                        DriveFolder.DriveFileResult driveFileResult = (DriveFolder.DriveFileResult) result;
                        promise.resolve(driveFileResult.getDriveFile().getDriveId().toString());
                    } else {
                        throw new IllegalStateException("Should not get here");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to read " + sourceUri, e);
                promise.reject("Failed to read input", e);
            }
        }
    }

}
