// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {
class Version;
}

namespace mbus {

extern char VersionTag[];
extern char VersionTagDate[];
extern char VersionTagSystem[];
extern char VersionTagSystemRev[];
extern char VersionTagBuilder[];

class Vtag {
public:
    static vespalib::Version currentVersion;
    static void printVersionNice();
};

} // namespace messagebus

