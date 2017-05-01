package org.rncloudfs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
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
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.rncloudfs.RNCloudFsModule.TAG;

public class GoogleDriveApiClient {
    private final GoogleApiClient googleApiClient;
    private ReactApplicationContext reactContext;

    public GoogleDriveApiClient(GoogleApiClient googleApiClient, ReactApplicationContext reactContext) {
        this.googleApiClient = googleApiClient;
        this.reactContext = reactContext;
    }

    //see https://developers.google.com/drive/android/appfolder
    public DriveFolder appFolder() {
        return Drive.DriveApi.getAppFolder(googleApiClient);
    }

    public synchronized DriveFolder documentsFolder() {
        DriveFolder rootFolder = Drive.DriveApi.getRootFolder(googleApiClient);
        String applicationName = getApplicationName(reactContext);

        if(fileExists(rootFolder, applicationName)) {
            return folder(rootFolder, applicationName);
        } else {
            DriveFolder.DriveFolderResult folder = createFolder(rootFolder, applicationName);
            return folder.getDriveFolder();
        }
    }

    private static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    private DriveFolder.DriveFolderResult createFolder(DriveFolder parentFolder, String name) {
        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                .setTitle(name)
                .build();

        Log.i(TAG, "creating folder: " + name);
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

    private String listFiles(DriveFolder folder, List<String> pathParts, FileVisitor fileVisitor) throws NotFoundException {
        List<String> currentPath = new ArrayList<>();
        currentPath.add(".");
        listFiles(currentPath, folder, pathParts, fileVisitor);
        return TextUtils.join("/", currentPath);
    }

    private void listFiles(List<String> currentPath, DriveFolder folder, List<String> pathParts, FileVisitor fileVisitor) throws NotFoundException {
        if (pathParts.isEmpty()) {
            listFiles(folder, fileVisitor);
        } else {
            String pathName = pathParts.remove(0);

            if(pathName.equals("..")) {
                DriveApi.MetadataBufferResult result = folder.listParents(googleApiClient).await();
                try {
                    for (Metadata metadata : result.getMetadataBuffer()) {
                        if(metadata.isFolder()) {
                            currentPath.remove(currentPath.size() - 1);
                            listFiles(currentPath, metadata.getDriveId().asDriveFolder(), pathParts, fileVisitor);
                            return;
                        }
                    }
                    throw new IllegalStateException("No parent folder");
                } finally {
                    result.release();
                }
            }

            currentPath.add(pathName);

            DriveApi.MetadataBufferResult childrenBuffer = folder.listChildren(googleApiClient).await();
            try {
                for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                    if (metadata.isFolder() && pathName.equals(metadata.getTitle())) {
                        listFiles(currentPath, metadata.getDriveId().asDriveFolder(), pathParts, fileVisitor);
                        return;
                    }
                }

                throw new NotFoundException(pathName);
            } finally {
                childrenBuffer.release();
            }
        }
    }

    private void listFiles(DriveFolder folder, FileVisitor fileVisitor) {
        DriveApi.MetadataBufferResult childrenBuffer = folder.listChildren(googleApiClient).await();
        try {
            for (Metadata metadata : childrenBuffer.getMetadataBuffer()) {
                fileVisitor.fileMetadata(metadata);
            }
        } finally {
            childrenBuffer.release();
        }
    }

    public DriveFolder createFolders(DriveFolder parentFolder, List<String> pathParts) {
        if(pathParts.isEmpty())
            return parentFolder;

        String name = pathParts.remove(0);

        DriveFolder folder = folder(parentFolder, name);

        if (folder == null) {
            DriveFolder.DriveFolderResult result = createFolder(parentFolder, name);

            Log.i(TAG, "Created folder '" + name + "'");

            return createFolders(result.getDriveFolder(), pathParts);
        } else {
            Log.d(TAG, "Folder already exists '" + name + "'");

            return createFolders(folder, pathParts);
        }
    }

    public boolean fileExists(boolean useDocumentsFolder, List<String> pathParts) {
        List<String> parentDirs = pathParts.size() > 1 ? pathParts.subList(0, pathParts.size() - 2) : new ArrayList<String>();
        final String filename = pathParts.get(pathParts.size() - 1);

        DriveFolder rootFolder = useDocumentsFolder ? documentsFolder() : appFolder();

        final AtomicBoolean found = new AtomicBoolean(false);

        try {
            listFiles(rootFolder, parentDirs, new FileVisitor() {
                @Override
                public void fileMetadata(Metadata metadata) {
                    if(!found.get()) {
                        String title = metadata.getTitle();
                        if(title.equals(filename))
                            found.set(true);
                    }
                }
            });

            return found.get();
        } catch (NotFoundException e) {
            return false;
        }
    }

    public void unregisterListener(GoogleApiClient.ConnectionCallbacks callbacks) {
        googleApiClient.unregisterConnectionCallbacks(callbacks);
    }

    private interface FileVisitor {
        void fileMetadata(Metadata metadata);
    }

    public String createFile(DriveFolder driveFolder, RNCloudFsModule.InputDataSource input, String filename) throws IOException {
        return createFile(driveFolder, input, filename, null);
    }

    public String createFile(DriveFolder driveFolder, RNCloudFsModule.InputDataSource input, String filename, String mimeType) throws IOException {
        int count = 1;

        String uniqueFilename = filename;
        while (fileExists(driveFolder, uniqueFilename)) {
            Log.w(TAG, "item already at location: " + filename);
            uniqueFilename = count + "." + filename;
            count++;
        }

        DriveApi.DriveContentsResult driveContentsResult = Drive.DriveApi.newDriveContents(googleApiClient).await();

        if (!driveContentsResult.getStatus().isSuccess()) {
            throw new IllegalStateException("cannot create file");
        }

        DriveContents driveContents = driveContentsResult.getDriveContents();
        OutputStream outputStream = driveContents.getOutputStream();
        input.copyToOutputStream(outputStream);
        outputStream.close();

        MetadataChangeSet.Builder builder = new MetadataChangeSet.Builder()
                .setTitle(uniqueFilename);

        if (mimeType != null) {
            builder.setMimeType(mimeType);
        }

        DriveFolder.DriveFileResult driveFileResult = driveFolder.createFile(googleApiClient, builder.build(), driveContents).await();

        if (!driveFileResult.getStatus().isSuccess()) {
            throw new IllegalStateException("cannot create file");
        }

        Log.i(TAG, "Created a file '" + uniqueFilename);
        return uniqueFilename;
    }

    public boolean fileExists(DriveFolder driveFolder, String filename) {
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

    public WritableMap listFiles(boolean useDocumentsFolder, List<String> paths) throws NotFoundException {
        WritableMap data = new WritableNativeMap();

        final WritableNativeArray files = new WritableNativeArray();

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

        DriveFolder parentFolder = useDocumentsFolder ? documentsFolder() : appFolder();

        String path = listFiles(parentFolder, paths, new FileVisitor() {
            @Override
            public void fileMetadata(Metadata metadata) {
                if (!metadata.isDataValid())
                    return;

                WritableNativeMap file = new WritableNativeMap();

                file.putBoolean("isDirectory", metadata.isFolder());
                file.putBoolean("isFile", !metadata.isFolder());
                file.putString("name", metadata.getTitle());
                file.putString("uri", metadata.getAlternateLink());
                file.putString("lastModified", simpleDateFormat.format(metadata.getModifiedDate()));
                file.putInt("size", (int) metadata.getFileSize());

                files.pushMap(file);
            }
        });

        data.putString("path", path);
        data.putArray("files", files);

        return data;
    }

    @NonNull
    public static List<String> resolve(String path) {
        List<String> names = new ArrayList<>();
        for (String pathPart : path.split("/")) {
            if (pathPart.equals(".") || pathPart.isEmpty()) {
                //ignore
            } else {
                names.add(pathPart);
            }
        }
        return names;
    }

    private static class NotFoundException extends Exception {
        public NotFoundException(String pathName) {
            super("not found: " + pathName);
        }
    }
}
