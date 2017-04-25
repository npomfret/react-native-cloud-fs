
# react-native-cloud-fs

A react-native library for reading and writing files to _iCloud Drive_ (iOS) and _Google Drive_ (Android).

[Getting started](./docs/getting-started.md)

## Usage
```javascript
import RNCloudFs from 'react-native-cloud-fs';
```

### fileExists (options)
Returns a promise which when resolved returns a boolean value indicating if the specified path already exists.

```javascript
const destinationPath = "foo-bar/docs/info.pdf";
const scope = 'visible';

RNCloudFs.fileExists({
  targetPath: destinationPath, 
  scope: scope
})
  .then((exists) => {
    console.log(exists ? "this file exists" : "this file does not exist");
  })
  .catch((err) => {
    console.warn("it failed", err);
  })
```
_targetPath_: a path

_scope_: determines if the user-visible documents (`visible`) or the app-visible documents (`hidden`) are searched for the specified path

### copyToCloud (options)
Copies the content of a file (or uri) to the target file system.  The files will appear in a either a user visible 
directory, or a dectory that only the app can see.  The directory is named after `destinationPath`.  The directory 
hierarchy for the destination path will be created if it doesn't already exist. If the target file already exists 
it a new filename is chosen and returned when the promise is resolved. 

```javascript
const sourceUri = {uri: 'https://foo.com/bar.pdf'};
const destinationPath = "foo-bar/docs/info.pdf";
const mimeType = null;
const scope = 'visible';

RNCloudFs.copyToCloud({
  sourcePath: sourceUri, 
  targetPath: destinationPath, 
  mimeType: mimeType, 
  scope: scope
})
  .then((path) => {
    console.log("it worked", path);
  })
  .catch((err) => {
    console.warn("it failed", err);
  })
```

_sourceUri_: object with any uri or an **absolute** file path and optional http headers, e.g:
 * `{path: '/foo/bar/file.txt'}`
 * `{uri: 'file://foo/bar/file.txt'}`
 * `{uri: 'http://www.files.com/foo/bar/file.txt', 'http-headers': {user: 'foo', password: 'bar'}}` (http-headers are android only)
 * `{uri: 'content://media/external/images/media/296'}` (android only)
 * `{uri: 'assets-library://asset/asset.JPG?id=106E99A1-4F6A-45A2-B320-B0AD4A8E8473&ext=JPG'}` (iOS only)
 
_targetPath_: a **relative** path including a filename under which the file will be placed, e.g:
 * `my-cloud-text-file.txt`
 * `foo/bar/my-cloud-text-file.txt`
 
_mimeType_:  a mime type to store the file with **or null** (android only) , e.g:
 * `text/plain`
 * `application/json`
 * `image/jpeg`

_scope_: a string to specify if the user can access the document (`visible`) or not (`hidden`)

### listFiles (options)
Lists files in a directory along with some file metadata.  The scope determines if the file listing takes place in the app folder or the public user documents folder.

```javascript
const path = "dirA/dirB";
const scope = 'hidden';

RNCloudFs.listFiles({targetPath: path, scope: scope})
  .then((res) => {
    console.log("it worked", res);
  })
  .catch((err) => {
    console.warn("it failed", err);
  })
```

_targetPath_: a path representing a folder to list files from

_scope_: a string to specify if the files are the user-visible documents (`visible`) or the app-visible documents (`hidden`)
