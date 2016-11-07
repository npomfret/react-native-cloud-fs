
# react-native-cloud-fs

## Getting started

`$ npm install react-native-cloud-fs --save`

### Mostly automatic installation

`$ react-native link react-native-cloud-fs`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-cloud-fs` and add `RNCloudFs.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNCloudFs.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.reactlibrary.RNCloudFsPackage;` to the imports at the top of the file
  - Add `new RNCloudFsPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-cloud-fs'
  	project(':react-native-cloud-fs').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-cloud-fs/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-cloud-fs')
  	```

#### Windows
[Read it! :D](https://github.com/ReactWindows/react-native)

1. In Visual Studio add the `RNCloudFs.sln` in `node_modules/react-native-cloud-fs/windows/RNCloudFs.sln` folder to their solution, reference from their app.
2. Open up your `MainPage.cs` app
  - Add `using Cl.Json.RNCloudFs;` to the usings at the top of the file
  - Add `new RNCloudFsPackage()` to the `List<IReactPackage>` returned by the `Packages` method
      

## Usage
```javascript
import RNCloudFs from 'react-native-cloud-fs';

// TODO: What do with the module?
RNCloudFs;
```
  