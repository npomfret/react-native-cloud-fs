# todo

## In progress
 
## API

 * make android listFiles return lastModifiedDate in iso format
 * `copyToCloud`
   * add option to overwrite existing file 
   * add option to fail silently if the file already exists
   * add optional http headers (done for android)
   * ...think about return type, if any
 * `moveFile` / `renameFile`
 * `createCouldDirectory`
 * `copyFromCloud`
   * single file
   * to local file system
   * put (to rest endpoint)
   * post (mutipart form post)
 * `deleteFromCloud`
   * single file
   * entire directory
 * check what happens (and fix) if a destination path contains non-filename safe characters ('#', '<', '$', '+', '%', '>', '!', '`', '&', '*', '‘', '|', '{', '?', '“', '=', '}', '/', ':', '\\', '@') [source](http://www.mtu.edu/umc/services/digital/writing/characters-avoid/).  _iOS only_
 * sensible & descriptive error messages for all error scenarios
 * `searchForCloudFiles`
 * give option to use public v's private folders
   * in android this is the difference between the 'app' folder and the 'root' folder
   * in iOS this might be solved with the iCloud key/value store
 
## ExampleApp

 * link to file in icloud drive
 * link to file in google drive
 * working examples for all of the above
 
## Other implementations
 
 * Dropbox
 * Windows
