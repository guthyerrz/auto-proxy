#import "APAutoLoader.h"

#if __has_include(<AutoProxy/AutoProxy-Swift.h>)
#import <AutoProxy/AutoProxy-Swift.h>
#elif __has_include("AutoProxy-Swift.h")
#import "AutoProxy-Swift.h"
#endif

@implementation APAutoLoader

+ (void)load {
    dispatch_async(dispatch_get_main_queue(), ^{
        [[AutoProxy shared] loadConfig];
    });
}

@end
