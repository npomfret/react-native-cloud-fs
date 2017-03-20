package org.rncloudfs;

import android.os.AsyncTask;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;

public class ListFilesTask extends AsyncTask<String, Object, Void> {
    private final Promise promise;
    private final GoogleDriveApiClient googleDriveApiClient;

    public ListFilesTask(Promise promise, GoogleDriveApiClient googleDriveApiClient) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.promise = promise;
    }

    @Override
    protected Void doInBackground(String... params) {
        String path = params[0];
        try {
            WritableMap data = googleDriveApiClient.listFiles(path);
            promise.resolve(data);
        } catch (Exception e) {
            promise.reject("error", e);
        }
        return null;
    }
}
