# todo

## In progress

 * `createCloudFile`
   * write arbitrary text to a file
 * make list files ignore leading slash - force all paths to be relative to root
 
## API

 * `copyToCloud`
   * add option to overwrite existing file 
   * add option to fail silently if the file already exists
   * add optional http headers (done for android)
   * ...think about return type, if any
 * `moveCloudFile`
 * `createCouldDirectory`
 * `copyFromCloud`
   * single file
   * entire directory
 * `deleteFromCloud`
   * single file
   * entire directory
 * `download`
   * single file
   * entire directory
 * check what happens (and fix) if a destination path contains non-filename safe characters ('#', '<', '$', '+', '%', '>', '!', '`', '&', '*', '‘', '|', '{', '?', '“', '=', '}', '/', ':', '\\', '@') [source](http://www.mtu.edu/umc/services/digital/writing/characters-avoid/).  _iOS only_
 * sensible & descriptive error messages for all error scenarios
 * `searchForCloudFiles`
 * give option to use public v's private folders
   * in android this is the difference between the 'app' folder and the 'root' folder
   * in iOS this might be solved with the iCluod key/value store
 
## ExampleApp

 * link to file in icloud drive
 * link to file in google drive
 * working examples for all of the above
 
## Other implementations
 
 * Dropbox
 * Windows
