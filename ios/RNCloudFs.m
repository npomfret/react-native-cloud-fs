
#import "RNCloudFs.h"
#import <UIKit/UIKit.h>
#import "RCTBridgeModule.h"
#import "RCTEventDispatcher.h"
#import "RCTUtils.h"
#import <AssetsLibrary/AssetsLibrary.h>

@implementation RNCloudFs

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(copyToCloud:(NSDictionary *)source :(NSString *)destinationPath :(NSString *)mimeType
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {

    // mimeType is ignored for iOS

    NSString *sourceUri = [source objectForKey:@"uri"];
    if(!sourceUri) {
        sourceUri = [source objectForKey:@"path"];
    }

    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSFileManager* fileManager = [NSFileManager defaultManager];

        if([sourceUri hasPrefix:@"assets-library"]){
            ALAssetsLibrary *library = [[ALAssetsLibrary alloc] init];

            [library assetForURL:[NSURL URLWithString:sourceUri] resultBlock:^(ALAsset *asset) {

                ALAssetRepresentation *rep = [asset defaultRepresentation];

                Byte *buffer = (Byte*)malloc(rep.size);
                NSUInteger buffered = [rep getBytes:buffer fromOffset:0.0 length:rep.size error:nil];
                NSData *data = [NSData dataWithBytesNoCopy:buffer length:buffered freeWhenDone:YES];

                if (data) {
                    NSString *filename = [sourceUri lastPathComponent];
                    NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:filename];
                    [data writeToFile:tempFile atomically:YES];
                    [self moveToICloud:tempFile :destinationPath resolver:resolve rejecter:reject];
                } else {
                    NSLog(@"source file does not exist %@", sourceUri);
                    return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: failed to copy asset '%@'", sourceUri], nil);
                }
            } failureBlock:^(NSError *error) {
                NSLog(@"source file does not exist %@", sourceUri);
                return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: no such file or directory, open '%@'", sourceUri], nil);
            }];
        } else if ([sourceUri hasPrefix:@"file:/"] || [sourceUri hasPrefix:@"/"]) {
            NSError *error;
            NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:@"^file:/+" options:NSRegularExpressionCaseInsensitive error:&error];
            NSString *modifiedSourceUri = [regex stringByReplacingMatchesInString:sourceUri options:0 range:NSMakeRange(0, [sourceUri length]) withTemplate:@"/"];

            if ([fileManager fileExistsAtPath:modifiedSourceUri isDirectory:nil]) {
                NSURL *sourceURL = [NSURL fileURLWithPath:modifiedSourceUri];

                // todo: figure out how to *copy* to icloud drive
                // ...setUbiquitous will move the file instead of copying it, so as a work around lets copy it to a tmp file first
                NSString *filename = [sourceUri lastPathComponent];
                NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:filename];
                NSError *error;
                [fileManager copyItemAtPath:sourceURL toPath:tempFile error:&error];
                [self moveToICloud:tempFile :destinationPath resolver:resolve rejecter:reject];
            } else {
                NSLog(@"source file does not exist %@", sourceUri);
                return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: no such file or directory, open '%@'", sourceUri], nil);
            }
        } else {
            NSURL *url = [NSURL URLWithString:sourceUri];
            NSData *urlData = [NSData dataWithContentsOfURL:url];

            if (urlData) {
                NSString *filename = [sourceUri lastPathComponent];
                NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:filename];
                [urlData writeToFile:tempFile atomically:YES];
                [self moveToICloud:tempFile :destinationPath resolver:resolve rejecter:reject];
            } else {
                NSLog(@"source file does not exist %@", sourceUri);
                return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: cannot download '%@'", sourceUri], nil);
            }
        }
    });
}

- (void) moveToICloud:(NSString *)tempFile :(NSString *)destinationPath
                      resolver:(RCTPromiseResolveBlock)resolve
                      rejecter:(RCTPromiseRejectBlock)reject {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSLog(@"Moving file %@ to %@", tempFile, destinationPath);

        NSFileManager* fileManager = [NSFileManager defaultManager];

        [self rootDirectoryForICloud:^(NSURL *ubiquityURL) {
            if (ubiquityURL) {
                NSURL* targetFile = [ubiquityURL URLByAppendingPathComponent:destinationPath];
                NSLog(@"Target file: %@", targetFile.path);

                NSURL *dir = [targetFile URLByDeletingLastPathComponent];
                if (![fileManager fileExistsAtPath:dir.path isDirectory:nil]) {
                    [fileManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
                }

                NSError *error;
                if ([fileManager setUbiquitous:YES itemAtURL:[NSURL fileURLWithPath:tempFile] destinationURL:targetFile error:&error]) {
                    return resolve(@{@"path": targetFile.absoluteString});
                } else {
                    NSLog(@"Error occurred: %@", error);
                    NSString *codeWithDomain = [NSString stringWithFormat:@"E%@%zd", error.domain.uppercaseString, error.code];
                    return reject(codeWithDomain, error.localizedDescription, error);
                }
            } else {
                NSLog(@"Could not retrieve a ubiquityURL");
                return reject(@"ENOENT", [NSString stringWithFormat:@"ENOENT: could not copy to iCloud drive '%@'", tempFile.absolutePath], nil);
            }
        }];
    });
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
