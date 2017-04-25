# todo

## In progress
 
## API

 * send event when the files change
 * add method: `exists`
 * add method: `moveFile` / `renameFile`
 * add method: `createCouldDirectory`
 * add method: `copyFromCloud`
   * single file
   * to local file system
   * put (to rest endpoint)
   * post (mutipart form post)
 * add method: `deleteFromCloud`
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
 * add method: `searchForCloudFiles`
 
## Other implementations
 
 * Dropbox
 * google drive on iOS
