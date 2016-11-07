
using ReactNative.Bridge;
using System;
using System.Collections.Generic;
using Windows.ApplicationModel.Core;
using Windows.UI.Core;

namespace Com.Reactlibrary.RNCloudFs
{
    /// <summary>
    /// A module that allows JS to share data.
    /// </summary>
    class RNCloudFsModule : NativeModuleBase
    {
        /// <summary>
        /// Instantiates the <see cref="RNCloudFsModule"/>.
        /// </summary>
        internal RNCloudFsModule()
        {

        }

        /// <summary>
        /// The name of the native module.
        /// </summary>
        public override string Name
        {
            get
            {
                return "RNCloudFs";
            }
        }
    }
}
