
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

RCT_EXPORT_METHOD(createFile:(NSString *)destinationPath content:(NSString *)content
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{

        NSString *filename = [destinationPath lastPathComponent];
        NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:filename];
        NSError *error;
        
        [content writeToFile:content atomically:YES encoding:NSUTF8StringEncoding error:nil];
        
        [self moveToICloud:tempFile :destinationPath resolver:resolve rejecter:reject];
    });
}

RCT_EXPORT_METHOD(listFiles:(NSString *)destinationPath
                      resolver:(RCTPromiseResolveBlock)resolve
                      rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        
        NSFileManager* fileManager = [NSFileManager defaultManager];
        NSError *error = nil;

        NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
        [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ssZZZ"];
        
        [self rootDirectoryForICloud:^(NSURL *ubiquityURL) {
            if (ubiquityURL) {
                NSURL* dir = [ubiquityURL URLByAppendingPathComponent:destinationPath];
                NSString* dirPath = [dir.path stringByStandardizingPath];
                
                NSMutableArray<NSString *> *output = [NSMutableArray new];
                
                NSError *error = nil;
                NSArray *contents = [fileManager contentsOfDirectoryAtPath:dir error:&error];
                if(error) {
                    return reject(@"error", error.description, nil);
                }
                
                [contents enumerateObjectsUsingBlock:^(id object, NSUInteger idx, BOOL *stop) {
                    NSString *path = [dirPath stringByAppendingPathComponent:object];

                    NSError *error = nil;
                    NSDictionary *attributes = [fileManager attributesOfItemAtPath:path error:&error];
                    if(error) {
                        NSLog(@"problem getting attributes for %@", path);
                        //skip this one
                    }
                    
                    NSFileAttributeType type = [attributes objectForKey:NSFileType];
                    
                    bool isDir = type == NSFileTypeDirectory;
                    bool isFile = type == NSFileTypeRegular;
                    
                    if(!isDir && !isFile)
                        return;
                    
                    NSDate* modDate = [attributes objectForKey:NSFileModificationDate];
                    
                    [output addObject:@{
                                        @"name": object,
                                        @"path": path,
                                        @"size": [attributes objectForKey:NSFileSize],
                                        @"lastModified": [dateFormatter stringFromDate:modDate],
                                        @"isDirectory": @(isDir),
                                        @"isFile": @(isFile)
                                        }];
                }];
                
                if (error) {
                    return reject(@"error", [NSString stringWithFormat:@"could not copy to iCloud drive '%@'", destinationPath], error);
                }
                
                return resolve(@{@"files": output, @"path": dirPath});
                
            } else {
                NSLog(@"Could not retrieve a ubiquityURL");
                return reject(@"error", [NSString stringWithFormat:@"could not copy to iCloud drive '%@'", destinationPath], nil);
            }
        }];
    });
    
}

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
                    return reject(@"error", [NSString stringWithFormat:@"failed to copy asset '%@'", sourceUri], nil);
                }
            } failureBlock:^(NSError *error) {
                NSLog(@"source file does not exist %@", sourceUri);
                return reject(@"error", error.description, nil);
            }];
        } else if ([sourceUri hasPrefix:@"file:/"] || [sourceUri hasPrefix:@"/"]) {
            NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:@"^file:/+" options:NSRegularExpressionCaseInsensitive error:nil];
            NSString *modifiedSourceUri = [regex stringByReplacingMatchesInString:sourceUri options:0 range:NSMakeRange(0, [sourceUri length]) withTemplate:@"/"];
            
            if ([fileManager fileExistsAtPath:modifiedSourceUri isDirectory:nil]) {
                NSURL *sourceURL = [NSURL fileURLWithPath:modifiedSourceUri];
                
                // todo: figure out how to *copy* to icloud drive
                // ...setUbiquitous will move the file instead of copying it, so as a work around lets copy it to a tmp file first
                NSString *filename = [sourceUri lastPathComponent];
                NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:filename];

                NSError *error;
                [fileManager copyItemAtPath:sourceURL toPath:tempFile error:&error];
                if(error) {
                    return reject(@"error", error.description, nil);
                }
                
                [self moveToICloud:tempFile :destinationPath resolver:resolve rejecter:reject];
            } else {
                NSLog(@"source file does not exist %@", sourceUri);
                return reject(@"error", [NSString stringWithFormat:@"no such file or directory, open '%@'", sourceUri], nil);
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
                return reject(@"error", [NSString stringWithFormat:@"cannot download '%@'", sourceUri], nil);
            }
        }
    });
}

- (void) moveToICloud:(NSString *)tempFile :(NSString *)destinationPath
             resolver:(RCTPromiseResolveBlock)resolve
             rejecter:(RCTPromiseRejectBlock)reject {
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSString * destPath = destinationPath;
        while ([destPath hasPrefix:@"/"]) {
            destPath = [destPath substringFromIndex:1];
        }
        
        NSLog(@"Moving file %@ to %@", tempFile, destPath);
        
        NSFileManager* fileManager = [NSFileManager defaultManager];
        
        [self rootDirectoryForICloud:^(NSURL *ubiquityURL) {
            if (ubiquityURL) {
                NSURL* targetFile = [ubiquityURL URLByAppendingPathComponent:destPath];
                NSLog(@"Target file: %@", targetFile.path);
                
                NSURL *dir = [targetFile URLByDeletingLastPathComponent];
                if (![fileManager fileExistsAtPath:dir.path]) {
                    [fileManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
                }
                
                NSError *error;
                if ([fileManager setUbiquitous:YES itemAtURL:[NSURL fileURLWithPath:tempFile] destinationURL:targetFile error:&error]) {
                    return resolve(@{@"path": targetFile.absoluteString});
                } else {
                    NSLog(@"Error occurred: %@", error);
                    return reject(@"error", error.description, nil);
                }
            } else {
                NSLog(@"Could not retrieve a ubiquityURL");
                return reject(@"error", [NSString stringWithFormat:@"could not copy to iCloud drive '%@'", tempFile.absolutePath], nil);
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
