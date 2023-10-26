// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "version.h"

namespace vespalib {

extern char VersionTag[];
extern char VersionTagType[];
extern char VersionTagValue[];
extern char VersionTagDate[];
extern char VersionTagSystem[];
extern char VersionTagSystemRev[];
extern char VersionTagBuilder[];
extern char VersionTagPkg[];
extern char VersionTagComponent[];
extern char VersionTagArch[];


class Vtag {
public:
    static Version currentVersion;
    static void printVersionNice();
};

}
