
# react-native-cloud-fs

A react-native library for reading and writing files using cloud based file systems.

Supported APIs: iCloud Drive (iOS), Google Drive (Android)

## Getting started

`$ npm install react-native-cloud-fs --save`

`$ react-native link react-native-cloud-fs`

### iOS

On the device, make sure iCloud Drive is enabled.

In xcode...

 * Add the following to `ios/app-name/Info.plist` (replacing _app-name_ and _package-name_ as appropriate):

```
<key>NSUbiquitousContainers</key>
<dict>
    <key>iCloud.package-name</key>
    <dict>
        <key>NSUbiquitousContainerIsDocumentScopePublic</key>
        <true/>
        <key>NSUbiquitousContainerSupportedFolderLevels</key>
        <string>One</string>
        <key>NSUbiquitousContainerName</key>
        <string>app-name</string>
    </dict>
</dict>
```

 * Enable iCloud:

![alt tag](docs/xcode.png)

### Android
Enable Google Drive API:
  - Create a [new project](https://console.developers.google.com/apis/dashboard) for your app (if you don't already have one)
    - Under `Credentials`, choose `New Credentials` > `OAth client ID`
      - Choose `Configure consent screen`
        - enter a product name
        - save it
      - Choose `Application type` > `Android`
        - enter a name
        - enter your SHA1 fingerprint (use the keytool to find it, eg: `keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -list -v`)
        - enter your package name
        - click `create` and make a note of your OAuth client id
  - Click Library, choose `Drive API` and enable it
    - Click `Drive UI Integration`
      - add the mandatory icons
      - under `Drive integration` > `Authentication` > `Automatically show OAuth 2.0 consent screen when users open my application from Google Drive` enter your OAuth client ID 
  
Here's a [video](https://www.youtube.com/watch?v=RezC1XP6jcs&feature=youtu.be&t=3m55s) of someone doing a similar thing for the Google Drive API demo.

## Usage
```javascript
import RNCloudFs from 'react-native-cloud-fs';
```

### copyToCloud
Copies the content of the sourceUri to a file in the could file system.

```javascript
RNCloudFs.copyToCloud(sourceUri, destinationPath, mimeType)
  .then((res) => {
    console.log("it worked", res);
  })
  .catch((err) => {
    console.warn("it failed", res);
  })
```

_sourceUri_: any uri or **absolute** file path, e.g:
 * `/foo/bar/file.txt`
 * `file://foo/bar/file.txt`
 * `http://www.files.com/foo/bar/file.txt`
 * `content://media/external/images/media/296` (android only)
 * (todo) `assets-library://asset/asset.JPG?id=106E99A1-4F6A-45A2-B320-B0AD4A8E8473&ext=JPG` (iOS only)
 
_destinationPath_: a **relative** path including a filename under which the file will be placed, e.g:
 * `my-cloud-text-file.txt`
 * `foo/bar/my-cloud-text-file.txt`
 
_mimeType_:  a mime type to store the file with **or null** (android only) 
 * `text/plain`
 * `application/json`
 * `image/jpeg`
 