
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

RCT_EXPORT_METHOD(copyToICloud:(NSString *)sourceUri :(NSString *)targetRelativePath
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSFileManager* fileManager = [NSFileManager defaultManager];

    if (![fileManager fileExistsAtPath:sourceUri isDirectory:nil]) {
        NSLog(@"source file does not exist %@", sourceUri);
        return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: no such file or directory, open '%@'", sourceUri], nil);
    }
    
    NSURL *sourceURL = [NSURL fileURLWithPath:sourceUri];

    [self rootDirectoryForICloud:^(NSURL *ubiquityURL) {
        if (ubiquityURL) {
            NSURL* targetFile = [ubiquityURL URLByAppendingPathComponent:targetRelativePath];
            NSLog(@"Target file: %@", targetFile.path);
            
            NSURL *dir = [targetFile URLByDeletingLastPathComponent];
            NSLog(@"Target dir: %@", dir.path);
            if (![fileManager fileExistsAtPath:dir.path isDirectory:nil]) {
                [fileManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
            }

            NSError *error;
            if ([fileManager setUbiquitous:YES itemAtURL:sourceURL destinationURL:targetFile error:&error]) {
                
                resolve(@{
                          @"path": targetFile.absoluteString,
                          });
            } else {
                NSLog(@"Error occurred: %@", error);
                NSString *codeWithDomain = [NSString stringWithFormat:@"E%@%zd", error.domain.uppercaseString, error.code];
                reject(codeWithDomain, error.localizedDescription, error);
            }
        } else {
            NSLog(@"Could not retrieve a ubiquityURL");
            return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: could not copy to iCloud drive '%@'", sourceUri], nil);
        }
    }];
}

- (void)rootDirectoryForICloud:(void (^)(NSURL *))completionHandler {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSFileManager* fileManager = [NSFileManager defaultManager];
        NSURL *rootDirectory = [[fileManager URLForUbiquityContainerIdentifier:nil] URLByAppendingPathComponent:@"Documents"];
        
        if (rootDirectory) {
            if (![fileManager fileExistsAtPath:rootDirectory.path isDirectory:nil]) {
                NSLog(@"Creating documents directory: %@", rootDirectory.path);
                [fileManager createDirectoryAtURL:rootDirectory withIntermediateDirectories:YES attributes:nil error:nil];
            }
        }
        
        dispatch_async(dispatch_get_main_queue(), ^{
            completionHandler(rootDirectory);
        });
    });
}

- (NSURL *)localPathForResource:(NSString *)resource ofType:(NSString *)type {
    NSString *documentsDirectory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0];
    NSString *resourcePath = [[documentsDirectory stringByAppendingPathComponent:resource] stringByAppendingPathExtension:type];
    return [NSURL fileURLWithPath:resourcePath];
}

@end
