package org.rncloudfs;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CopyToGoogleDriveTask extends AsyncTask<RNCloudFsModule.SourceUri, Void, Void> {
    private final DriveFolder rootFolder;
    private String targetFolder;
    @Nullable
    private final String mimeType;
    private final Promise promise;
    private GoogleApiClient googleApiClient;

    public CopyToGoogleDriveTask(final GoogleApiClient googleApiClient, DriveFolder rootFolder, String targetFolder, @Nullable String mimeType, Promise promise) {
        this.rootFolder = rootFolder;
        this.targetFolder = targetFolder;
        this.mimeType = mimeType;
        this.promise = promise;
        this.googleApiClient = googleApiClient;
    }

    @Override
    protected Void doInBackground(RNCloudFsModule.SourceUri... params) {
        List<String> names = new ArrayList<>();
        for (String name : targetFolder.split("/")) {
            if(name.trim().length() > 0) {
                names.add(name);
            }
        }

        for (RNCloudFsModule.SourceUri sourceUri : params) {
            createFileInFolders(rootFolder, names, sourceUri, mimeType, promise);
        }
        return null;
    }

    private void createFileInFolders(DriveFolder parentFolder, final List<String> pathParts, final RNCloudFsModule.SourceUri sourceUri, @Nullable final String mimeType, final Promise promise) {
        if (pathParts.size() > 1) {
            final String name = pathParts.remove(0);

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(name)
                    .build();

            parentFolder.createFolder(googleApiClient, changeSet)
                    .setResultCallback(new ResultCallback<DriveFolder.DriveFolderResult>() {
                        @Override
                        public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                            Log.d(RNCloudFsModule.TAG, "Created folder '" + name + "'");

                            createFileInFolders(result.getDriveFolder(), pathParts, sourceUri, mimeType, promise);
                        }
                    });
        } else {
            createFileInFolder(parentFolder, sourceUri, pathParts.get(0), mimeType, promise);
        }
    }

    private void createFileInFolder(final DriveFolder driveFolder, final RNCloudFsModule.SourceUri sourceUri, final String filename, @Nullable final String mimeType, final Promise promise) {
        Drive.DriveApi.newDriveContents(googleApiClient)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                    @Override
                    public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(RNCloudFsModule.TAG, "Failed to create new content");
                            promise.reject("Failed to create new content", "Failed to create new content");
                            return;
                        }

                        DowloadTask dowloadTask = new DowloadTask(result.getDriveContents(), filename, driveFolder);
                        dowloadTask.execute(sourceUri);
                    }
                });
    }

    private class DowloadTask extends AsyncTask<RNCloudFsModule.SourceUri, Void, Void> {

        private DriveContents driveContents;
        private String filename;
        private DriveFolder driveFolder;

        private DowloadTask(DriveContents driveContents, String filename, DriveFolder driveFolder) {
            this.driveContents = driveContents;
            this.filename = filename;
            this.driveFolder = driveFolder;
        }

        @Override
        protected Void doInBackground(RNCloudFsModule.SourceUri... params) {
            for (RNCloudFsModule.SourceUri sourceUri : params) {
                try {
                    OutputStream outputStream = driveContents.getOutputStream();
                    sourceUri.copyToOutputStream(outputStream);
                    outputStream.close();

                    MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder()
                            .setTitle(filename);

                    if (mimeType != null) {
                        builder.setMimeType(mimeType);
                    }

                    driveFolder.createFile(googleApiClient, builder.build(), driveContents)
                            .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                @Override
                                public void onResult(@NonNull DriveFolder.DriveFileResult driveFileResult) {
                                    System.out.println("Created a file with content: " + driveFileResult.getDriveFile().getDriveId());
                                    promise.resolve(driveFileResult.getDriveFile().toString());
                                }
                            });
                } catch (Exception e) {
                    Log.e(RNCloudFsModule.TAG, "Failed to read " + sourceUri, e);
                    promise.reject("Failed to read input", e);
                }
            }

            return null;
        }
    }
}
