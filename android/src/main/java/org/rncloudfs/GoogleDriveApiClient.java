package org.rncloudfs;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.rncloudfs.RNCloudFsModule.TAG;

public class GoogleDriveApiClient {
    private final GoogleApiClient googleApiClient;

    public GoogleDriveApiClient(GoogleApiClient googleApiClient) {
        this.googleApiClient = googleApiClient;
    }

    public DriveFolder appFolder() {
        return Drive.DriveApi.getAppFolder(googleApiClient);
    }

    public DriveFolder.DriveFolderResult createFolder(DriveFolder parentFolder, MetadataChangeSet changeSet) {
        return parentFolder.createFolder(googleApiClient, changeSet).await();
    }

    @Nullable
    public DriveFolder folder(DriveFolder parentFolder, String name) {
        DriveApi.MetadataBufferResult childrenBuffer = parentFolder.listChildren(googleApiClient).await();//maybe queryChildren would be much better
        try {
            for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                if (metadata.getTitle().equals(name)) {
                    return metadata.isFolder() ? metadata.getDriveId().asDriveFolder() : null;
                }
            }
        } finally {
            childrenBuffer.release();
        }
        return null;
    }

    public void listFiles(DriveFolder parentFolder, List<String> pathParts, FileVisitor fileVisitor) {
        if (pathParts.isEmpty()) {
            listFiles(parentFolder, fileVisitor);
        } else {
            String pathName = pathParts.remove(0);

            DriveApi.MetadataBufferResult childrenBuffer = parentFolder.listChildren(googleApiClient).await();
            try {
                for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                    if (metadata.isFolder() && pathName.equals(metadata.getTitle())) {
                        listFiles(metadata.getDriveId().asDriveFolder(), pathParts, fileVisitor);
                        return;
                    }
                }

                throw new IllegalStateException("not found: " + pathName);
            } finally {
                childrenBuffer.release();
            }
        }
    }

    public void listFiles(DriveFolder folder, FileVisitor fileVisitor) {
        DriveApi.MetadataBufferResult childrenBuffer = folder.listChildren(googleApiClient).await();
        try {
            for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                fileVisitor.fileMetadata(metadata);
            }
        } finally {
            childrenBuffer.release();
        }
    }

    interface FileVisitor {
        void fileMetadata(Metadata metadata);
    }

    public Result createFileInFolder(DriveFolder driveFolder, RNCloudFsModule.SourceUri sourceUri, String filename, String mimeType) throws IOException {
        if (fileExistsIn(driveFolder, filename)) {
            Log.w(TAG, "item already at location: " + filename);
            throw new IllegalStateException("Item already exists: " + filename);
        }

        DriveApi.DriveContentsResult result = Drive.DriveApi.newDriveContents(googleApiClient).await();

        if (!result.getStatus().isSuccess()) {
            return result;
        }

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
        return driveFileResult;
    }

    public boolean fileExistsIn(DriveFolder driveFolder, String filename) {
        DriveApi.MetadataBufferResult childrenBuffer = driveFolder.listChildren(googleApiClient).await();
        try {
            for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                if (metadata.getTitle().equals(filename))
                    return true;
            }
            return false;
        } finally {
            childrenBuffer.release();
        }
    }

    public WritableMap listFiles(String path) {
        WritableMap data = new WritableNativeMap();
        data.putString("path", path);

        final WritableNativeArray files = new WritableNativeArray();

        listFiles(appFolder(), resolve(path), new FileVisitor() {
            @Override
            public void fileMetadata(Metadata metadata) {
                if (!metadata.isDataValid())
                    return;

                WritableNativeMap file = new WritableNativeMap();
                file.putBoolean("isDirectory", metadata.isFolder());
                file.putBoolean("isFile", !metadata.isFolder());
                file.putString("name", metadata.getTitle());
                file.putString("path", metadata.getDriveId().toString());
                file.putInt("size", (int) metadata.getFileSize());

                files.pushMap(file);
            }
        });

        data.putArray("files", files);

        return data;
    }

    @NonNull
    private static List<String> resolve(String path) {
        List<String> names = new ArrayList<>();
        for (String pathPart : path.split("/")) {
            if (pathPart.equals(".")) {
                //ignore
            } else if (pathPart.equals("..")) {
                names.remove(names.size() - 1);
            } else {
                names.add(pathPart);
            }
        }
        return names;
    }
}
