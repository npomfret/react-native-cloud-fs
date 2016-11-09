'use strict';

import React, {Component} from "react";
import {AppRegistry, TouchableOpacity, StyleSheet, Text, View, TextInput, Platform, CameraRoll} from "react-native";
import RNFS from "react-native-fs";
import RNCloudFs from "react-native-cloud-fs";

export default class RNCloudFSExample extends Component {
  constructor(props) {
    super(props);

    this._saveFile = this._saveFile.bind(this);

    this.state = {
      tmpFilePath: "",
      filename: "",
      imageFilename: "",
      imagePath: ""
    }
  }

  componentDidMount() {
    const tmpFilePath = RNFS.DocumentDirectoryPath + '/test.txt';

    RNFS.writeFile(tmpFilePath, 'This is a test file ' + new Date().toISOString(), 'utf8')
      .then(() => {
        this.setState({
          tmpFilePath: tmpFilePath,
          filename: "my-file.txt"
        })
      });

    RNCloudFSExample._getPhotosFromCameraRoll(1)
      .then((res) => {
        console.log("res", res);
        if (res.edges.length > 0) {
          this.setState({
            imagePath: res.edges[0].node.image.uri,
            imageFilename: res.edges[0].node.image.filename
          });
        }
      })
  }

  static _getPhotosFromCameraRoll(count, after) {
    const fetchParams = {
      first: count,
      groupTypes: "SavedPhotos",
      assetType: "Photos"
    };

    if (after) {
      fetchParams.after = after;
    }

    if (Platform.OS === "android") {
      delete fetchParams.groupTypes;
    }

    return CameraRoll.getPhotos(fetchParams);
  }

  _saveFile(sourcePath, targetPath) {
    return RNCloudFs.copyToCloud(sourcePath, targetPath, null)
      .then((res) => {
        console.log("it worked", res);
      })
      .catch((err) => {
        console.warn("it failed", err);
      })
  }

  render() {
    return (
      <View style={{flex: 1, alignItems: 'center', padding: 8}}>
        <Text style={[styles.heading, {marginVertical: 16}]}>Copy URL to cloud</Text>

        <Container
          saveFile={this._saveFile}
          sourcePath={this.state.tmpFilePath}
          targetPath={"absolute-path-demo/" + this.state.filename}
          heading="absolute path"/>

        <Container
          saveFile={this._saveFile}
          sourcePath={"file:/" + this.state.tmpFilePath}
          targetPath={"file-url-demo/" + this.state.filename}
          heading="file url"/>

        <Container
          saveFile={this._saveFile}
          sourcePath={"https://raw.githubusercontent.com/npomfret/react-native-cloud-fs/master/README.md"}
          targetPath={"web-url-demo/README.md"}
          heading="url"/>

        <Container
          saveFile={this._saveFile}
          sourcePath={this.state.imagePath}
          targetPath={"image-demo/" + this.state.imageFilename}
          heading="internal url"/>
      </View>
    );
  }
}

class Container extends Component {
  static propTypes = {
    sourcePath: React.PropTypes.string.isRequired,
    targetPath: React.PropTypes.string.isRequired,
    heading: React.PropTypes.string.isRequired,
    saveFile: React.PropTypes.func.isRequired,
  };

  render() {
    return <View style={styles.container}>
      <View style={{flex: 1}}>
        <Text style={styles.heading}>{this.props.heading}</Text>
        <TextInput style={styles.url} value={this.props.sourcePath}/>
        <View style={{alignItems: 'center'}}>
          <View style={{flexDirection: 'row', justifyContent: 'center'}}>
            <TouchableOpacity onPress={() => this.props.saveFile(this.props.sourcePath, this.props.targetPath)}><Text style={styles.button}>save to cloud</Text></TouchableOpacity>
          </View>
          <Text style={[styles.heading, {fontStyle: 'italic'}]}>({this.props.targetPath})</Text>
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
  url: {
    height: 20,
    borderColor: 'gray',
    borderWidth: 1,
    fontSize: 8,
    paddingHorizontal: 2,
    color: 'blue'
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

