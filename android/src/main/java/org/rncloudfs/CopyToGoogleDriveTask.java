package org.rncloudfs;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.drive.DriveFolder;

import java.io.IOException;
import java.util.List;

import static org.rncloudfs.GoogleDriveApiClient.resolve;
import static org.rncloudfs.RNCloudFsModule.TAG;

public class CopyToGoogleDriveTask extends AsyncTask<RNCloudFsModule.SourceUri, Void, Void> {
    private final String outputPath;
    @Nullable
    private final String mimeType;
    private final Promise promise;
    private final GoogleDriveApiClient googleApiClient;
    private final boolean useDocumentsFolder;

    public CopyToGoogleDriveTask(String outputPath, @Nullable String mimeType, Promise promise, GoogleDriveApiClient googleDriveApiClient, boolean useDocumentsFolder) {
        this.outputPath = outputPath;
        this.mimeType = mimeType;
        this.promise = promise;
        this.googleApiClient = googleDriveApiClient;
        this.useDocumentsFolder = useDocumentsFolder;
    }

    @Override
    protected Void doInBackground(RNCloudFsModule.SourceUri... params) {
        List<String> pathParts = resolve(outputPath);

        RNCloudFsModule.SourceUri sourceUri = params[0];
        try {
            DriveFolder rootFolder = useDocumentsFolder ? googleApiClient.documentsFolder() : googleApiClient.appFolder();
            createFileInFolders(rootFolder, pathParts, sourceUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write " + outputPath, e);
            promise.reject("Failed copy '" + sourceUri.uri + "' to " + outputPath, e);
        }

        return null;
    }

    private void createFileInFolders(DriveFolder parentFolder, List<String> pathParts, RNCloudFsModule.SourceUri sourceUri) {
        if (pathParts.size() > 1)
            parentFolder = googleApiClient.createFolders(parentFolder, pathParts.subList(0, pathParts.size() - 1));

        try {
            Result result = googleApiClient.createFile(parentFolder, sourceUri, pathParts.get(0), mimeType);
            if (!result.getStatus().isSuccess()) {
                Log.e(TAG, "Failed to create new content");
                promise.reject("Failed to create new content", "Failed to create new content");
            } else {
                if (result instanceof DriveFolder.DriveFileResult) {
                    promise.resolve(TextUtils.join("/", pathParts));
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
