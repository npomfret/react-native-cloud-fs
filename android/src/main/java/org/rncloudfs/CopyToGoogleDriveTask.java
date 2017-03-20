package org.rncloudfs;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.rncloudfs.RNCloudFsModule.TAG;

public class CopyToGoogleDriveTask extends AsyncTask<RNCloudFsModule.SourceUri, Void, Void> {
    private final String outputPath;
    @Nullable
    private final String mimeType;
    private final Promise promise;
    private final GoogleApiClient googleApiClient;

    public CopyToGoogleDriveTask(GoogleApiClient googleApiClient, String outputPath, @Nullable String mimeType, Promise promise) {
        this.outputPath = outputPath;
        this.mimeType = mimeType;
        this.promise = promise;
        this.googleApiClient = googleApiClient;
    }

    @Override
    protected Void doInBackground(RNCloudFsModule.SourceUri... params) {
        List<String> parthParts = new ArrayList<>();
        for (String name : outputPath.split("/")) {
            if(name.trim().length() > 0) {
                parthParts.add(name);
            }
        }

        RNCloudFsModule.SourceUri sourceUri = params[0];
        try {
            DriveFolder rootFolder = Drive.DriveApi.getRootFolder(googleApiClient);
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

            DriveFolder folder = folder(parentFolder, name);

            if(folder == null) {
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(name)
                        .build();

                DriveFolder.DriveFolderResult result = parentFolder.createFolder(googleApiClient, changeSet).await();

                Log.i(TAG, "Created folder '" + name + "'");

                createFileInFolders(result.getDriveFolder(), pathParts, sourceUri);
            } else {
                Log.d(TAG, "Folder already exists '" + name + "'");

                createFileInFolders(folder, pathParts, sourceUri);
            }

        } else {
            createFileInFolder(parentFolder, sourceUri, pathParts.get(0));
        }
    }

    @Nullable
    private DriveFolder folder(DriveFolder parentFolder, String name) {
        Metadata metadata = find(parentFolder, name);
        return metadata != null && metadata.isFolder() ? metadata.getDriveId().asDriveFolder() : null;
    }

    @Nullable
    private Metadata find(DriveFolder parentFolder, String name) {
        DriveApi.MetadataBufferResult childrenBuffer = parentFolder.listChildren(googleApiClient).await();
        MetadataBuffer metadataBuffer = childrenBuffer.getMetadataBuffer();
        for (Metadata metadata : metadataBuffer) {
            if(metadata.getTitle().equals(name)) {
                return metadata;
            }
        }
        return null;
    }

    private void createFileInFolder(DriveFolder driveFolder, RNCloudFsModule.SourceUri sourceUri, String filename) {
        Metadata metadata = find(driveFolder, filename);
        if(metadata != null) {
            Log.w(TAG, "item already at location: " + metadata);
            throw new IllegalStateException("Item already exists at " + outputPath);
        }

        DriveApi.DriveContentsResult result = Drive.DriveApi.newDriveContents(googleApiClient).await();

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

            if (mimeType != null) {
                builder.setMimeType(mimeType);
            }

            DriveFolder.DriveFileResult driveFileResult = driveFolder.createFile(googleApiClient, builder.build(), driveContents).await();
            Log.i(TAG, "Created a file '" + filename + "' with content: " + driveFileResult.getDriveFile().getDriveId());
            promise.resolve(driveFileResult.getDriveFile().getDriveId().toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + sourceUri, e);
            promise.reject("Failed to read input", e);
        }
    }
}
