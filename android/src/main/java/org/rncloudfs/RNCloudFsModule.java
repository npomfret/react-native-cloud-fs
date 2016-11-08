package org.rncloudfs;

import android.content.IntentSender;
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
import com.google.android.gms.drive.Drive;

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "RNCloudFs";
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private final ReactApplicationContext reactContext;
    private final GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        googleApiClient = new GoogleApiClient.Builder(this.reactContext)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @ReactMethod
    public void copyToICloud(String sourceUri, String targetRelativePath, Promise promise) {

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