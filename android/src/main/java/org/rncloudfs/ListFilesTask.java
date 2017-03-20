package org.rncloudfs;

import android.os.AsyncTask;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.common.api.GoogleApiClient;

public class ListFilesTask extends AsyncTask<String, Object, Void> {
    private final GoogleApiClient googleApiClient;
    private Promise promise;

    public ListFilesTask(GoogleApiClient googleApiClient, Promise promise) {
        this.googleApiClient = googleApiClient;
        this.promise = promise;
    }

    @Override
    protected Void doInBackground(String... params) {
        GoogleDriveApiClient googleDriveApiClient = new GoogleDriveApiClient(googleApiClient);
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
