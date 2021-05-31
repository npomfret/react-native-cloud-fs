package org.rncloudfs;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.Nullable;

import android.util.Log;
import android.webkit.MimeTypeMap;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class RNCloudFsModule extends ReactContextBaseJavaModule implements GoogleApiClient.OnConnectionFailedListener, LifecycleEventListener, ActivityEventListener {
    public static final String TAG = "RNCloudFs";
    private static final int REQUEST_CODE_SIGN_IN = 1;
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 2;
    private static final int REQUEST_AUTHORIZATION = 11;
    private DriveServiceHelper mDriveServiceHelper;

    private Promise signInPromise;
    private @Nullable Promise mPendingPromise; // we use it for calling again copyToCloud after obtaining authorisation
    private @Nullable ReadableMap mPendingOptions;
    private static final String COPY_TO_CLOUD = "CopyToCloud";
    private static final String LIST_FILES = "ListFiles";
    private @Nullable String mPendingOperation = null;
    private final ReactApplicationContext reactContext;
    private GoogleApiClient googleApiClient;

    public RNCloudFsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "RNCloudFs";
    }

    /**
     * android only method.  Just here to test the connection logic
     */
    @ReactMethod
    public void reset(final Promise promise) {

    }

    @ReactMethod
    public void fileExists(ReadableMap options, final Promise promise) {
        if (mDriveServiceHelper != null) {
            String fileId = options.getString("fileId");
            Log.d(TAG, "Reading file " + fileId);

            mDriveServiceHelper.checkIfFileExists(fileId)
                    .addOnSuccessListener(exists -> {
                        promise.resolve(exists);

                    })
                    .addOnFailureListener(exception ->{
                        try{
                            UserRecoverableAuthIOException e = (UserRecoverableAuthIOException)exception;
                            this.reactContext.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION, null);
                        } catch(Exception e){
                            Log.e(TAG, "Couldn't read file.", exception);
                            promise.reject(exception);
                        }
                    });
        }
    }

    @ReactMethod
    public void deleteFromCloud(ReadableMap options, final Promise promise) {
        if (mDriveServiceHelper != null) {
            String fileId = options.getString("id");
            Log.d(TAG, "Deleting file " + fileId);

            mDriveServiceHelper.deleteFile(fileId)
                    .addOnSuccessListener(deleted -> {
                        promise.resolve(deleted);

                    })
                    .addOnFailureListener(exception ->{
                        try{
                            UserRecoverableAuthIOException e = (UserRecoverableAuthIOException)exception;
                            this.reactContext.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION, null);
                        } catch(Exception e){
                            Log.e(TAG, "Couldn't delete file.", exception);
                            promise.reject(exception);
                        }
                    });
        }
    }

    @ReactMethod
    public void loginIfNeeded(final Promise promise){
        if (mDriveServiceHelper == null) {
            // Check for already logged in account
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this.reactContext);

            // Not signed in - request sign in
            if (account == null) {
                this.signInPromise = promise;
                requestSignIn();
            } else {
                // Restore mDriveServiceHelper
                // Use the authenticated account to sign in to the Drive service.
                GoogleAccountCredential credential =
                        GoogleAccountCredential.usingOAuth2(
                                this.reactContext, Collections.singleton(DriveScopes.DRIVE_APPDATA));
                credential.setSelectedAccount(account.getAccount());
                Drive googleDriveService =
                        new Drive.Builder(
                                AndroidHttp.newCompatibleTransport(),
                                new GsonFactory(),
                                credential)
                                .build();

                // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                // Its instantiation is required before handling any onClick actions.
                mDriveServiceHelper = new DriveServiceHelper(googleDriveService);
                promise.resolve(true);
            }
        }  else {
            promise.resolve(true);
        }
    }

    @ReactMethod
    public void listFiles(ReadableMap options, final Promise promise) {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Querying for files.");
            WritableArray files = new WritableNativeArray();
            WritableMap result = new WritableNativeMap();

            boolean useDocumentsFolder = options.hasKey("scope") ? options.getString("scope").toLowerCase().equals("visible") : true;
            try {

                mDriveServiceHelper.queryFiles(useDocumentsFolder)
                        .addOnSuccessListener(fileList -> {
                            for (File file : fileList.getFiles()) {
                                WritableMap fileInfo = new WritableNativeMap();
                                fileInfo.putString("name", file.getName());
                                fileInfo.putString("id", file.getId());
                                fileInfo.putString("lastModified", file.getModifiedTime().toString());
                                files.pushMap(fileInfo);
                            }
                            result.putArray("files", files);
                            promise.resolve(result);
                            clearPendingOperations();
                        })
                        .addOnFailureListener(exception -> {
                            clearPendingOperations();
                            Log.e(TAG, "Unable to query files: " + exception.getCause().getMessage());
                            try {
                                UserRecoverableAuthIOException e = (UserRecoverableAuthIOException) exception;
                                mPendingPromise = promise;
                                mPendingOptions = options;
                                mPendingOperation = LIST_FILES;
                                this.reactContext.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION, null);
                            } catch (Exception e) {
                                throw e;
                            }
                        });
            } catch (Exception exception) {
                try {
                    ExecutionException e = (ExecutionException) exception;
                    mPendingPromise = promise;
                    mPendingOptions = options;
                    mPendingOperation = LIST_FILES;
                    Intent intent = ((UserRecoverableAuthIOException) e.getCause()).getIntent();
                    this.reactContext.startActivityForResult(intent, REQUEST_AUTHORIZATION, null);
                } catch (Exception e) {
                    promise.reject(exception);
                    Log.e(TAG, "Unable to query files: " + exception.getCause().getMessage());
                }
            }
        }
    }

    /**
     * Copy the source into the google drive database
     */
    @ReactMethod
    public void copyToCloud(ReadableMap options, final Promise promise) throws ExecutionException, InterruptedException {
        if(mDriveServiceHelper != null){
            if (!options.hasKey("sourcePath")) {
                promise.reject("error", "sourcePath not specified");
            }
            ReadableMap source = options.getMap("sourcePath");
            String uriOrPath = source.hasKey("uri") ? source.getString("uri") : null;

            if (uriOrPath == null) {
                uriOrPath = source.hasKey("path") ? source.getString("path") : null;
            }

            if (uriOrPath == null) {
                promise.reject("no path", "no source uri or path was specified");
                return;
            }

            if (!options.hasKey("targetPath")) {
                promise.reject("error", "targetPath not specified");
            }
            final String destinationPath = options.getString("targetPath");

            String mimeType = null;
            if (options.hasKey("mimetype")) {
                mimeType = options.getString("mimetype");
            }

            boolean useDocumentsFolder = options.hasKey("scope") ? options.getString("scope").toLowerCase().equals("visible") : true;

            String actualMimeType;
            if (mimeType == null) {
                actualMimeType = guessMimeType(uriOrPath);
            } else {
                actualMimeType = null;
            }

            try {
                mDriveServiceHelper.saveFile(uriOrPath, destinationPath, actualMimeType, useDocumentsFolder)
                        .addOnSuccessListener(fileId -> {
                            Log.d(TAG, "Saving " + fileId);
                            promise.resolve(fileId);
                            clearPendingOperations();
                        }).addOnFailureListener(exception -> {
                            clearPendingOperations();
                            try {
                                UserRecoverableAuthIOException e = (UserRecoverableAuthIOException) exception;
                                this.reactContext.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION, null);
                            } catch (Exception e) {
                                Log.e(TAG, "Couldn't create file.", exception);
                            } finally {
                                promise.reject(exception);
                            }
                        });
            } catch (Exception exception) {
                try {
                    ExecutionException e = (ExecutionException) exception;
                    mPendingPromise = promise;
                    mPendingOptions = options;
                    mPendingOperation = COPY_TO_CLOUD;
                    Intent intent = ((UserRecoverableAuthIOException) e.getCause()).getIntent();
                    this.reactContext.startActivityForResult(intent, REQUEST_AUTHORIZATION, null);
                } catch (Exception e) {
                    promise.reject(exception);
                    Log.e(TAG, "Couldn't create file.", exception);
                }
            }
        }
    }

    @Nullable
    private static String guessMimeType(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        } else {
            return null;
        }
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {

    }

    @Override
    public void onNewIntent(Intent intent) {
        System.out.println("RNCloudFsModule.onNewIntent");
    }

    public void clearPendingOperations(){
        mPendingOperation = null;
        mPendingPromise = null;
        mPendingOptions = null;
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, resultData);
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                // The Task returned from this call is always completed, no need to attach
                // a listener.
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // we want to make the operation again after obtaining right permissions
                    if (mPendingOperation != null) {
                        final String copiedPendingOperation = mPendingOperation;
                        reactContext.runOnNativeModulesQueueThread(() -> {
                            mPendingOperation = null;
                            switch (copiedPendingOperation) {
                                case COPY_TO_CLOUD:
                                    try {
                                        copyToCloud(mPendingOptions, mPendingPromise);
                                    } catch (ExecutionException | InterruptedException ignore) {
                                        mPendingPromise.reject(ignore);
                                        clearPendingOperations();
                                    }
                                    break;
                                case LIST_FILES:
                                    try {
                                        listFiles(mPendingOptions, mPendingPromise);
                                    } catch (Exception e) {
                                        mPendingPromise.reject(e);
                                        clearPendingOperations();
                                    }
                                    break;
                            }
                        });
                    }
                }
        }

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {

    }

    /**
     * Copy the source into the google drive database
     */

    /**
     * Starts a sign-in activity using {@link #REQUEST_CODE_SIGN_IN}.
     */
    @ReactMethod
    public void requestSignIn() {
        Log.d(TAG, "Requesting sign-in");

        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this.reactContext, signInOptions);

        // The result of the sign-in Intent is handled in onActivityResult.
        this.reactContext.startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN, null);
    }

    @ReactMethod
    public void logout(Promise promise) {

        Log.d(TAG, "Requesting sign-in");

        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this.reactContext, signInOptions);
        client.signOut()
                .addOnSuccessListener( result -> {
                    promise.resolve(true);
                })
                .addOnFailureListener(exception ->{
                    Log.e(TAG, "Couldn't log out.", exception);
                    promise.reject(exception);
                });
    }

    /**
     * Handles the {@code result} of a completed sign-in activity initiated from {@link
     * #requestSignIn()} ()}.
     */
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount googleAccount = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Signed in as " + googleAccount.getEmail());

            // Use the authenticated account to sign in to the Drive service.
            GoogleAccountCredential credential =
                    GoogleAccountCredential.usingOAuth2(
                            this.reactContext, Collections.singleton(DriveScopes.DRIVE_APPDATA));
            credential.setSelectedAccount(googleAccount.getAccount());
            Drive googleDriveService =
                    new Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            new GsonFactory(),
                            credential)
                            .build();

            // The DriveServiceHelper encapsulates all REST API and SAF functionality.
            // Its instantiation is required before handling any onClick actions.
            mDriveServiceHelper = new DriveServiceHelper(googleDriveService);

            if(this.signInPromise != null){
                this.signInPromise.resolve(true);
                this.signInPromise = null;
            }
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

    /**
     * Retrieves the title and content of a file identified by {@code fileId} and populates the UI.
     */
    @ReactMethod
    public void getGoogleDriveDocument(String fileId, Promise promise) {
        if (mDriveServiceHelper != null) {
            Log.d(TAG, "Reading file " + fileId);

            mDriveServiceHelper.readFile(fileId)
                    .addOnSuccessListener(content -> {
                        promise.resolve(content);

                    })
                    .addOnFailureListener(exception ->{
                        try{
                            UserRecoverableAuthIOException e = (UserRecoverableAuthIOException)exception;
                            this.reactContext.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION, null);
                        } catch(Exception e){
                            Log.e(TAG, "Couldn't read file.", exception);
                            promise.reject(exception);
                        }
                    });
        }
    }


}
