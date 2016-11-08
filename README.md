
# react-native-cloud-fs

A react-native library for reading and writing file with cloud based file systems.

Supported APIs: iCloud Drive (iOS), Google Drive (Android)

## Getting started

`$ npm install react-native-cloud-fs --save`

### Mostly automatic installation

`$ react-native link react-native-cloud-fs`

### Enable Google Drive API
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

// TODO: What do with the module?
RNCloudFs;
```
  