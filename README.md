
# react-native-cloud-fs

A react-native library for reading and writing files to _iCloud Drive_ (iOS) and _Google Drive_ (Android).

[Getting started](./docs/getting-started.md)

## Usage
```javascript
import RNCloudFs from 'react-native-cloud-fs';
```

### copyToCloud
Copies the content of a uri or file to a file in the target file system.  The files will appear in a directory named after the containing app followed by the `destinationPath`.  The directory hierarchy for the destination path will be created if it doesn't already exist. If the target file already exists it will not be overwritten, an error will be thrown. 

```javascript
const sourceUri = {uri: 'https://foo.com/bar.pdf'};
const destinationPath = "foo-bar/docs/info.pdf";
const mimeType = null;

RNCloudFs.copyToCloud(sourceUri, destinationPath, mimeType)
  .then((res) => {
    console.log("it worked", res);
  })
  .catch((err) => {
    console.warn("it failed", err);
  })
```

_sourceUri_: object with any uri or an **absolute** file path, e.g:
 * `{path: '/foo/bar/file.txt'}`
 * `{uri: 'file://foo/bar/file.txt'}`
 * `{uri: 'http://www.files.com/foo/bar/file.txt'}`
 * `{uri: 'content://media/external/images/media/296'}` (android only)
 * `{uri: 'assets-library://asset/asset.JPG?id=106E99A1-4F6A-45A2-B320-B0AD4A8E8473&ext=JPG'}` (iOS only)
 
_destinationPath_: a **relative** path including a filename under which the file will be placed, e.g:
 * `my-cloud-text-file.txt`
 * `foo/bar/my-cloud-text-file.txt`
 
_mimeType_:  a mime type to store the file with **or null** (android only) , e.g:
 * `text/plain`
 * `application/json`
 * `image/jpeg`

### listFiles
Lists files in a directory along with some file metadata

```javascript
const path = "dirA/dirB";

RNCloudFs.listFiles(path)
  .then((res) => {
    console.log("it worked", res);
  })
  .catch((err) => {
    console.warn("it failed", err);
  })
```

_path_: a path representing a directory/folder.  On iOS the root folder is the app folder. On Android the root folder is the _My Drive_ folder.