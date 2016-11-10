# todo

 * release initial version to NPM
 
## API

 * `copyToCloud`
   * add option to overwrite existing file 
   * add optional http headers
   * ...think about return type, if any
 * fix android implementation so it disconnects and reconnects (see 'Listening to LifeCycle events' in http://facebook.github.io/react-native/docs/native-modules-android.html)
 * `createCloudFile`
   * write arbitrary text to a file
 * `moveCloudFile`
 * `createCouldDirectory`
 * `copyFromCloud`
   * single file
   * entire directory
 * `deleteFromCloud`
   * single file
   * entire directory
 * `listCloudFiles`
   * get metadata for file (isDirectory, size, content-type, downloaded etc)
 * `download`
   * single file
   * entire directory
 * check what happens (and fix) if a destination path contains non-filename safe characters ('#', '<', '$', '+', '%', '>', '!', '`', '&', '*', '‘', '|', '{', '?', '“', '=', '}', '/', ':', '\\', '@') [source](http://www.mtu.edu/umc/services/digital/writing/characters-avoid/).  _iOS only_
 * sensible & descriptive error messages for all error scenarios
 
## ExampleApp

 * link to file in icloud drive
 * link to file in google drive
 * working examples for all of the above
 
## Other implementations
 
 * drop box
 * windows