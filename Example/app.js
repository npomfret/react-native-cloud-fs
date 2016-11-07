'use strict';

import React, { Component } from 'react';
import {AppRegistry, TouchableOpacity, StyleSheet, Text, View} from 'react-native';
import RNFS from 'react-native-fs';
import RNCloudFs from 'react-native-cloud-fs';

export default class Example extends Component {
  constructor(props) {
    super(props);

    console.log("RNCloudFs",RNCloudFs);
    this.saveFile = this.saveFile.bind(this);
  }

  saveFile() {
    const tmpFilePath = RNFS.DocumentDirectoryPath + '/test.txt';

    RNFS.writeFile(tmpFilePath, 'This is a test file ' + new Date().toISOString(), 'utf8')
      .then(() => {
        return RNCloudFs.copyToICloud(tmpFilePath, "my-icloud-file.txt");
      })
      .catch((err) => {
        console.warn("failed", err);
      })
  }

  render() {
    return (
      <View style={styles.container}>
        <TouchableOpacity onPress={this.saveFile}><Text>press to save a file to icloud</Text></TouchableOpacity>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  }
});

AppRegistry.registerComponent('Example', () => Example);
