# todo

## In progress
 
## API

 * `exists`
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
 * option to save images to 
   * ios photos
   * android clound images..?
 * `copyToCloud`
   * add option to overwrite existing file 
   * add option to fail silently if the file already exists
   * add optional http headers (done for android)
 * check what happens (and fix) if a destination path contains non-filename safe characters ('#', '<', '$', '+', '%', '>', '!', '`', '&', '*', '‘', '|', '{', '?', '“', '=', '}', '/', ':', '\\', '@') [source](http://www.mtu.edu/umc/services/digital/writing/characters-avoid/).  _iOS only_
 * sensible & descriptive error messages for all error scenarios
 * `searchForCloudFiles`
 * give option to use public v's private folders
   * in android this is the difference between the 'app' folder and the 'root' folder
   * in iOS this might be solved with the iCloud key/value store
 
## Other implementations
 
 * Dropbox
 * google drive on iOS
