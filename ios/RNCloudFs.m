
#import "RNCloudFs.h"
#import <UIKit/UIKit.h>
#if __has_include(<React/RCTBridgeModule.h>)
  #import <React/RCTBridgeModule.h>
#else
  #import "RCTBridgeModule.h"
#endif
#import "RCTEventDispatcher.h"
#import "RCTUtils.h"
#import <AssetsLibrary/AssetsLibrary.h>
#import "RCTLog.h"

@implementation RNCloudFs



- (dispatch_queue_t)methodQueue
{
    return dispatch_queue_create("RNCloudFs.queue", DISPATCH_QUEUE_SERIAL);
}

RCT_EXPORT_MODULE()

//see https://developer.apple.com/library/content/documentation/General/Conceptual/iCloudDesignGuide/Chapters/iCloudFundametals.html
  
RCT_EXPORT_METHOD(isAvailable:(RCTPromiseResolveBlock)resolve
                rejecter:(RCTPromiseRejectBlock)reject) {

    NSURL *ubiquityURL = [self icloudDirectory];
    if(ubiquityURL != nil){
        return resolve(@YES);
    }
    return resolve(@NO);
}

RCT_EXPORT_METHOD(createFile:(NSDictionary *) options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {

    NSString *destinationPath = [options objectForKey:@"targetPath"];
    NSString *content = [options objectForKey:@"content"];
    NSString *scope = [options objectForKey:@"scope"];
    bool documentsFolder = !scope || [scope caseInsensitiveCompare:@"visible"] == NSOrderedSame;

    NSString *tempFile = [NSTemporaryDirectory() stringByAppendingPathComponent:[[NSUUID UUID] UUIDString]];

    NSError *error;
    [content writeToFile:tempFile atomically:YES encoding:NSUTF8StringEncoding error:&error];
    if(error) {
        return reject(@"error", error.description, nil);
    }

    [self moveToICloudDirectory:documentsFolder :tempFile :destinationPath :resolve :reject];
}

RCT_EXPORT_METHOD(fileExists:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {

    NSString *destinationPath = [options objectForKey:@"targetPath"];
    NSString *scope = [options objectForKey:@"scope"];
    bool documentsFolder = !scope || [scope caseInsensitiveCompare:@"visible"] == NSOrderedSame;

    NSFileManager* fileManager = [NSFileManager defaultManager];

    NSURL *ubiquityURL = documentsFolder ? [self icloudDocumentsDirectory] : [self icloudDirectory];

    if (ubiquityURL) {
        NSURL* dir = [ubiquityURL URLByAppendingPathComponent:destinationPath];
        NSString* dirPath = [dir.path stringByStandardizingPath];

        bool exists = [fileManager fileExistsAtPath:dirPath];

        return resolve(@(exists));
    } else {
        RCTLogTrace(@"Could not retrieve a ubiquityURL");
        return reject(@"error", [NSString stringWithFormat:@"could access iCloud drive '%@'", destinationPath], nil);
    }
}

RCT_EXPORT_METHOD(listFiles:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {

    NSString *destinationPath = [options objectForKey:@"targetPath"];
    NSString *scope = [options objectForKey:@"scope"];
    bool documentsFolder = !scope || [scope caseInsensitiveCompare:@"visible"] == NSOrderedSame;

    NSFileManager* fileManager = [NSFileManager defaultManager];

    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    [dateFormatter setDateFormat:@"yyyy-MM-dd'T'HH:mm:ssZZZ"];

    NSURL *ubiquityURL = documentsFolder ? [self icloudDocumentsDirectory] : [self icloudDirectory];

    if (ubiquityURL) {
        NSURL* target = [ubiquityURL URLByAppendingPathComponent:destinationPath];

        NSMutableArray<NSDictionary *> *fileData = [NSMutableArray new];

        NSError *error = nil;

        BOOL isDirectory;
        [fileManager fileExistsAtPath:[target path] isDirectory:&isDirectory];

        NSURL *dirPath;
        NSArray *contents;
        if(isDirectory) {
            contents = [fileManager contentsOfDirectoryAtPath:[target path] error:&error];
            dirPath = target;
        } else {
            contents = @[[target lastPathComponent]];
            dirPath = [target URLByDeletingLastPathComponent];
        }

        if(error) {
            return reject(@"error", error.description, nil);
        }

        [contents enumerateObjectsUsingBlock:^(id object, NSUInteger idx, BOOL *stop) {
            NSURL *fileUrl = [dirPath URLByAppendingPathComponent:object];

            NSError *error;
            NSDictionary *attributes = [fileManager attributesOfItemAtPath:[fileUrl path] error:&error];
            if(error) {
                RCTLogTrace(@"problem getting attributes for %@", [fileUrl path]);
                //skip this one
                return;
            }

            NSFileAttributeType type = [attributes objectForKey:NSFileType];

            bool isDir = type == NSFileTypeDirectory;
            bool isFile = type == NSFileTypeRegular;

            if(!isDir && !isFile)
                return;

            NSDate* modDate = [attributes objectForKey:NSFileModificationDate];

            NSURL *shareUrl = [fileManager URLForPublishingUbiquitousItemAtURL:fileUrl expirationDate:nil error:&error];

            [fileData addObject:@{
                                  @"name": object,
                                  @"path": [fileUrl path],
                                  @"uri": shareUrl ? [shareUrl absoluteString] : [NSNull null],
                                  @"size": [attributes objectForKey:NSFileSize],
                                  @"lastModified": [dateFormatter stringFromDate:modDate],
                                  @"isDirectory": @(isDir),
                                  @"isFile": @(isFile)
                                  }];
        }];

        if (error) {
            return reject(@"error", [NSString stringWithFormat:@"could not copy to iCloud drive '%@'", destinationPath], error);
        }

        NSString *relativePath = [[dirPath path] stringByReplacingOccurrencesOfString:[ubiquityURL path] withString:@"."];

        return resolve(@{
                         @"files": fileData,
                         @"path": relativePath
                         });

    } else {
        NSLog(@"Could not retrieve a ubiquityURL");
        return reject(@"error", [NSString stringWithFormat:@"could not copy to iCloud drive '%@'", destinationPath], nil);
    }
}


RCT_EXPORT_METHOD(getIcloudDocument:(NSString *)filename
resolver:(RCTPromiseResolveBlock)resolver
rejecter:(RCTPromiseRejectBlock)rejecter) {
    __block bool resolved = NO;
    _query = [[NSMetadataQuery alloc] init];

    [_query setSearchScopes:@[NSMetadataQueryUbiquitousDocumentsScope, NSMetadataQueryUbiquitousDataScope]];

    NSPredicate *pred = [NSPredicate predicateWithFormat: @"%K == %@", NSMetadataItemFSNameKey, filename];
    [_query setPredicate:pred];
    

    [[NSNotificationCenter defaultCenter] addObserverForName:
     NSMetadataQueryDidFinishGatheringNotification
    object:_query queue:[NSOperationQueue currentQueue]
    usingBlock:^(NSNotification __strong *notification)
    {
        NSMetadataQuery *query = [notification object];
        [query disableUpdates];
        [query stopQuery];
        for (NSMetadataItem *item in query.results) {
            if([[item valueForAttribute:NSMetadataItemFSNameKey] isEqualToString:filename]){
                resolved = YES;
                NSURL *url = [item valueForAttribute:NSMetadataItemURLKey];
                bool fileIsReady = [self downloadFileIfNotAvailable: item];
                if(fileIsReady){
                    NSData *data = [NSData dataWithContentsOfURL: url];
                    NSString *content = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                    return resolver(content);
                } else {
                    // Call itself until the file it's ready
                    [self getIcloudDocument:filename resolver:resolver rejecter:rejecter];
                }
            }
        }
        if(!resolved){
            return rejecter(@"error", [NSString stringWithFormat:@"item not found '%@'", filename], nil);
        }
    }];

    dispatch_async(dispatch_get_main_queue(), ^{
        [self->_query startQuery];
    });

}

RCT_EXPORT_METHOD(deleteFromCloud:(NSDictionary *)item
resolver:(RCTPromiseResolveBlock)resolver
rejecter:(RCTPromiseRejectBlock)rejecter) {
   NSError *error;
   
    NSFileManager* fileManager = [NSFileManager defaultManager];
    [fileManager removeItemAtPath:item[@"path"] error:&error];
    if(error) {
        return rejecter(@"error", error.description, nil);
    }
    bool result = YES;
    return resolver(@(result));

}


RCT_EXPORT_METHOD(copyToCloud:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {

    // mimeType is ignored for iOS
    NSDictionary *source = [options objectForKey:@"sourcePath"];
    NSString *destinationPath = [options objectForKey:@"targetPath"];
    NSString *scope = [options objectForKey:@"scope"];
    bool documentsFolder = !scope || [scope caseInsensitiveCompare:@"visible"] == NSOrderedSame;

    NSFileManager* fileManager = [NSFileManager defaultManager];

    NSString *sourceUri = [source objectForKey:@"uri"];
    if(!sourceUri) {
        sourceUri = [source objectForKey:@"path"];
    }

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
                [self moveToICloudDirectory:documentsFolder :tempFile :destinationPath :resolve :reject];
            } else {
                RCTLogTrace(@"source file does not exist %@", sourceUri);
                return reject(@"error", [NSString stringWithFormat:@"failed to copy asset '%@'", sourceUri], nil);
            }
        } failureBlock:^(NSError *error) {
            RCTLogTrace(@"source file does not exist %@", sourceUri);
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
            if([fileManager fileExistsAtPath:tempFile]){
                [fileManager removeItemAtPath:tempFile error:&error];
                if(error) {
                    return reject(@"error", error.description, nil);
                }
            }

            [fileManager copyItemAtPath:[sourceURL path] toPath:tempFile error:&error];
            if(error) {
                return reject(@"error", error.description, nil);
            }

            [self moveToICloudDirectory:documentsFolder :tempFile :destinationPath :resolve :reject];
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
            [self moveToICloudDirectory:documentsFolder :tempFile :destinationPath :resolve :reject];
        } else {
            RCTLogTrace(@"source file does not exist %@", sourceUri);
            return reject(@"error", [NSString stringWithFormat:@"cannot download '%@'", sourceUri], nil);
        }
    }
}

- (void) moveToICloudDirectory:(bool) documentsFolder :(NSString *)tempFile :(NSString *)destinationPath
                              :(RCTPromiseResolveBlock)resolver
                              :(RCTPromiseRejectBlock)rejecter {

    if(documentsFolder) {
        NSURL *ubiquityURL = [self icloudDocumentsDirectory];
        [self moveToICloud:ubiquityURL :tempFile :destinationPath :resolver :rejecter];
    } else {
        NSURL *ubiquityURL = [self icloudDirectory];
        [self moveToICloud:ubiquityURL :tempFile :destinationPath :resolver :rejecter];
    }
}

- (void) moveToICloud:(NSURL *)ubiquityURL :(NSString *)tempFile :(NSString *)destinationPath
                     :(RCTPromiseResolveBlock)resolver
                     :(RCTPromiseRejectBlock)rejecter {


    NSString * destPath = destinationPath;
    while ([destPath hasPrefix:@"/"]) {
        destPath = [destPath substringFromIndex:1];
    }

    RCTLogTrace(@"Moving file %@ to %@", tempFile, destPath);

    NSFileManager* fileManager = [NSFileManager defaultManager];

    if (ubiquityURL) {

        NSURL* targetFile = [ubiquityURL URLByAppendingPathComponent:destPath];
        NSURL *dir = [targetFile URLByDeletingLastPathComponent];

        NSURL* uniqueFile = targetFile;

        if([fileManager fileExistsAtPath:uniqueFile.path]){
            NSError *error;
            [fileManager removeItemAtPath:uniqueFile.path error:&error];
            if(error) {
                return rejecter(@"error", error.description, nil);
            }
        }

        RCTLogTrace(@"Target file: %@", uniqueFile.path);

        if (![fileManager fileExistsAtPath:dir.path]) {
            [fileManager createDirectoryAtURL:dir withIntermediateDirectories:YES attributes:nil error:nil];
        }

        NSError *error;
        [fileManager setUbiquitous:YES itemAtURL:[NSURL fileURLWithPath:tempFile] destinationURL:uniqueFile error:&error];
        if(error) {
            return rejecter(@"error", error.description, nil);
        }

        [fileManager removeItemAtPath:tempFile error:&error];

        return resolver(uniqueFile.path);
    } else {
        NSError *error;
        [fileManager removeItemAtPath:tempFile error:&error];

        return rejecter(@"error", [NSString stringWithFormat:@"could not copy '%@' to iCloud drive", tempFile], nil);
    }
}

- (NSURL *)icloudDocumentsDirectory {
    NSFileManager* fileManager = [NSFileManager defaultManager];
    NSURL *rootDirectory = [[self icloudDirectory] URLByAppendingPathComponent:@"Documents"];

    if (rootDirectory) {
        if (![fileManager fileExistsAtPath:rootDirectory.path isDirectory:nil]) {
            RCTLogTrace(@"Creating documents directory: %@", rootDirectory.path);
            [fileManager createDirectoryAtURL:rootDirectory withIntermediateDirectories:YES attributes:nil error:nil];
        }
    }

    return rootDirectory;
}

- (NSURL *)icloudDirectory {
    NSFileManager* fileManager = [NSFileManager defaultManager];
    NSURL *rootDirectory = [fileManager URLForUbiquityContainerIdentifier:nil];
    return rootDirectory;
}

- (NSURL *)localPathForResource:(NSString *)resource ofType:(NSString *)type {
    NSString *documentsDirectory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0];
    NSString *resourcePath = [[documentsDirectory stringByAppendingPathComponent:resource] stringByAppendingPathExtension:type];
    return [NSURL fileURLWithPath:resourcePath];
}



- (BOOL)downloadFileIfNotAvailable:(NSMetadataItem*)item {
    if ([[item valueForAttribute:NSMetadataUbiquitousItemDownloadingStatusKey] isEqualToString:NSMetadataUbiquitousItemDownloadingStatusCurrent]){
        NSLog(@"File is ready!");
        return YES;
    }
    // Download the file.
    NSFileManager*  fm = [NSFileManager defaultManager];
    NSError *downloadError = nil;
    [fm startDownloadingUbiquitousItemAtURL:[item valueForAttribute:NSMetadataItemURLKey] error:&downloadError];
    if (downloadError) {
        NSLog(@"Error occurred starting download: %@", downloadError);
    }
    NSLog(@"Waiting before retrying...");
    [NSThread sleepForTimeInterval:0.3];
    return NO;

}


RCT_EXPORT_METHOD(syncCloud:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    _query = [[NSMetadataQuery alloc] init];
    [_query setSearchScopes:@[NSMetadataQueryUbiquitousDocumentsScope, NSMetadataQueryUbiquitousDataScope]];
    [_query setPredicate:[NSPredicate predicateWithFormat: @"%K LIKE '*'", NSMetadataItemFSNameKey]];


    dispatch_async(dispatch_get_main_queue(), ^{
        
        BOOL startedQuery = [self->_query startQuery];
        if (!startedQuery)
        {
            reject(@"error", @"Failed to start query.\n", nil);
        }
    });
    
    [[NSNotificationCenter defaultCenter] addObserverForName:
     NSMetadataQueryDidFinishGatheringNotification
    object:_query queue:[NSOperationQueue currentQueue]
    usingBlock:^(NSNotification __strong *notification)
    {
        NSMetadataQuery *query = [notification object];
        [query disableUpdates];
        [query stopQuery];
        for (NSMetadataItem *item in query.results) {
            [self downloadFileIfNotAvailable: item];
        }
        return resolve(@YES);
    }];
    
}

@end
