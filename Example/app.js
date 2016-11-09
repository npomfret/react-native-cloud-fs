'use strict';

import React, {Component} from "react";
import {AppRegistry, TouchableOpacity, StyleSheet, Text, View, TextInput} from "react-native";
import RNFS from "react-native-fs";
import RNCloudFs from "react-native-cloud-fs";

export default class RNCloudFSExample extends Component {
  constructor(props) {
    super(props);

    this._saveFile = this._saveFile.bind(this);

    this.state = {
      tmpFilePath: ""
    }
  }

  componentDidMount() {
    const tmpFilePath = RNFS.DocumentDirectoryPath + '/test.txt';

    RNFS.writeFile(tmpFilePath, 'This is a test file ' + new Date().toISOString(), 'utf8')
      .then(() => {
        this.setState({tmpFilePath: tmpFilePath})
      })
  }

  _saveFile(path) {
    return RNCloudFs.copyToCloud(path, "folder-a/folder-b/my-file." + new Date().toISOString() + ".txt", null)
      .then((res) => {
        console.log("it worked", res);
      })
      .catch((err) => {
        console.warn("it failed", err);
      })
  }

  render() {
    return (
      <View style={{flex: 1, justifyContent: 'center', alignItems: 'center', padding: 8}}>
        <Container saveFile={this._saveFile} path={this.state.tmpFilePath} heading="absolute path"/>
        <Container saveFile={this._saveFile} path={"file:/" + this.state.tmpFilePath} heading="file url" />
        <Container saveFile={this._saveFile} path={"https://raw.githubusercontent.com/npomfret/react-native-cloud-fs/master/README.md"} heading="url" />
      </View>
    );
  }
}

class Container extends Component {
  static propTypes = {
    path: React.PropTypes.string.isRequired,
    saveFile: React.PropTypes.func.isRequired,
  };

  render() {
    return <View style={styles.container}>
      <View style={{flex: 1}}>
        <Text style={styles.heading}>{this.props.heading}</Text>
        <TextInput style={{height: 20, borderColor: 'gray', borderWidth: 1, fontSize: 10, paddingHorizontal: 2}} value={this.props.path}/>
        <View style={{flexDirection: 'row', justifyContent: 'center'}}>
          <TouchableOpacity onPress={() => this.props.saveFile(this.props.path)}><Text style={styles.button}>save to cloud</Text></TouchableOpacity>
        </View>
      </View>
    </View>
  }
}

const styles = StyleSheet.create({
  container: {
    borderWidth: 1,
    borderColor: 'grey',
    borderRadius: 4,
    marginBottom: 8,
    flexDirection: 'row',
    padding: 4
  },
  heading: {
    fontSize: 12,
    textAlign: 'left'
  },
  button: {
    margin: 2,
    padding: 2,
    borderWidth: 1,
    fontSize: 10,
    borderRadius: 4,
    overflow: 'hidden',
    backgroundColor: 'black',
    color: 'white',
    fontWeight: '600'
  }
});

AppRegistry.registerComponent('RNCloudFSExample', () => RNCloudFSExample);

