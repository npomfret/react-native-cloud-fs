
#import "RNCloudFs.h"
#import <UIKit/UIKit.h>
#import "RCTBridgeModule.h"
#import "RCTEventDispatcher.h"
#import "RCTUtils.h"

@implementation RNCloudFs

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(copyToICloud:(NSString *)sourceUri :(NSString *)targetPath :(RCTResponseSenderBlock)callback)
{
    NSURL *sourceURL = [NSURL fileURLWithPath:sourceUri];
    NSURL *destinationURL = [self resolveICloudPath:targetPath];
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSFileManager *fileManager = [[NSFileManager alloc] init];
        [self createDirectionOfFileURL:destinationURL];
        NSError *err = nil;
        [fileManager copyItemAtURL:sourceURL toURL:destinationURL error:&err];
        
        if(err==nil) {
            callback(@[@NO, [destinationURL absoluteString]]);
        }else {
            callback(@[ [err localizedDescription] ]);
        }
    });
}

- (NSURL *)resolveICloudPath: (NSString *)path {
    return [[self baseICloudUrl] URLByAppendingPathComponent:path];
}

- (void)createDirectionOfFileURL:(NSURL *)fileUrl {
    NSFileManager *fileManager = [[NSFileManager alloc] init];
    NSURL *dir = [fileUrl URLByDeletingLastPathComponent];
    [fileManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
}

- (NSURL *)baseICloudUrl {
    NSString *bundleIdentifier = [[NSBundle mainBundle] bundleIdentifier];
    NSString *icloudID = [NSString stringWithFormat:@"iCloud.%@", bundleIdentifier];
    return [[NSFileManager defaultManager] URLForUbiquityContainerIdentifier:icloudID];
}

@end
