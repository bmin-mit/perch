// Now Playing helper. macOS 15.4+ only answers MediaRemote queries from Apple-entitled
// processes, so the agent loads this library into /usr/bin/perl (see MediaWatcher.swift),
// which prints one JSON line on stdout whenever what's playing changes.
#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#include <dlfcn.h>

typedef void (*GetInfoFn)(dispatch_queue_t, void (^)(NSDictionary *));
typedef void (*GetPidFn)(dispatch_queue_t, void (^)(int));
typedef void (*RegisterFn)(dispatch_queue_t);

static GetInfoFn getInfo;
static GetPidFn getPid;
static NSString *last;
// Last position sent, so a jump (repeat, seek, skip back) can be detected.
static double lastPos = -1, lastAt = 0;
static BOOL lastPlaying = NO;

static id stringFor(NSDictionary *info, NSString *key) {
    id v = info[key];
    return [v isKindOfClass:NSString.class] && [v length] > 0 ? v : nil;
}

static void emit(void) {
    getInfo(dispatch_get_main_queue(), ^(NSDictionary *info) {
        getPid(dispatch_get_main_queue(), ^(int pid) {
            NSMutableDictionary *out = [NSMutableDictionary dictionary];
            NSString *title = stringFor(info, @"kMRMediaRemoteNowPlayingInfoTitle");
            if (title) {
                out[@"title"] = title;
                NSString *artist = stringFor(info, @"kMRMediaRemoteNowPlayingInfoArtist");
                NSString *album = stringFor(info, @"kMRMediaRemoteNowPlayingInfoAlbum");
                if (artist) out[@"artist"] = artist;
                if (album) out[@"album"] = album;
                double rate = [info[@"kMRMediaRemoteNowPlayingInfoPlaybackRate"] doubleValue];
                double duration = [info[@"kMRMediaRemoteNowPlayingInfoDuration"] doubleValue];
                double elapsed = [info[@"kMRMediaRemoteNowPlayingInfoElapsedTime"] doubleValue];
                NSDate *stamp = info[@"kMRMediaRemoteNowPlayingInfoTimestamp"];
                if ([stamp isKindOfClass:NSDate.class] && rate > 0) {
                    elapsed += -[stamp timeIntervalSinceNow] * rate;
                }
                out[@"playing"] = @(rate > 0);
                if (duration > 0) {
                    out[@"dur"] = @((long)duration);
                    out[@"pos"] = @((long)MIN(MAX(elapsed, 0), duration));
                    out[@"at"] = @((long)[NSDate date].timeIntervalSince1970);
                }
                if (pid > 0) {
                    NSRunningApplication *app = [NSRunningApplication runningApplicationWithProcessIdentifier:pid];
                    if (app.localizedName) out[@"app"] = app.localizedName;
                }
            }
            NSData *data = [NSJSONSerialization dataWithJSONObject:out options:NSJSONWritingSortedKeys error:nil];
            NSString *line = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
            // The position moves while playing, so compare without it to avoid a line every poll...
            NSMutableDictionary *key = [out mutableCopy];
            [key removeObjectsForKeys:@[ @"pos", @"at" ]];
            NSString *keyString = [key description];
            // ...but do send one when the position jumps away from where the phone would have
            // extrapolated it: a repeated track restarts at 0 with the same title, as does a seek.
            double now = [NSDate date].timeIntervalSince1970;
            double pos = out[@"pos"] ? [out[@"pos"] doubleValue] : -1;
            double expected = lastPos + (lastPlaying ? now - lastAt : 0);
            BOOL jumped = pos >= 0 && lastPos >= 0 && fabs(pos - expected) > 3;
            if (![keyString isEqualToString:last] || jumped) {
                last = keyString;
                lastPos = pos;
                lastAt = now;
                lastPlaying = [out[@"playing"] boolValue];
                printf("%s\n", line.UTF8String);
                fflush(stdout);
            }
        });
    });
}

void perch_media_stream(void) {
    void *h = dlopen("/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote", RTLD_NOW);
    getInfo = (GetInfoFn)dlsym(h, "MRMediaRemoteGetNowPlayingInfo");
    getPid = (GetPidFn)dlsym(h, "MRMediaRemoteGetNowPlayingApplicationPID");
    RegisterFn reg = (RegisterFn)dlsym(h, "MRMediaRemoteRegisterForNowPlayingNotifications");
    if (!getInfo || !getPid) {
        printf("{\"error\":\"unavailable\"}\n");
        fflush(stdout);
        return;
    }
    if (reg) reg(dispatch_get_main_queue());
    for (NSString *name in @[
             @"kMRMediaRemoteNowPlayingInfoDidChangeNotification",
             @"kMRMediaRemoteNowPlayingApplicationIsPlayingDidChangeNotification",
             @"kMRMediaRemoteNowPlayingApplicationDidChangeNotification",
         ]) {
        [[NSNotificationCenter defaultCenter] addObserverForName:name
                                                          object:nil
                                                           queue:nil
                                                      usingBlock:^(NSNotification *n) { emit(); }];
    }
    // Poll as well: a repeat or seek doesn't always post a change notification.
    [NSTimer scheduledTimerWithTimeInterval:2 repeats:YES block:^(NSTimer *t) { emit(); }];
    emit();
    CFRunLoopRun();
}
