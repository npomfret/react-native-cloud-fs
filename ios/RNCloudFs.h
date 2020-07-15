
#if __has_include(<React/RCTBridgeModule.h>)
  #import <React/RCTBridgeModule.h>
#else
  #import "RCTBridgeModule.h"
#endif

@interface RNCloudFs : NSObject <RCTBridgeModule>
@property (nonatomic, strong) NSMetadataQuery *query;
@end
