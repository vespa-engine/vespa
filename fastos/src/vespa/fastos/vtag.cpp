// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdio.h>
#include "vtag.h"

#ifndef V_TAG
#define V_TAG "NOTAG"
#define V_TAG_TYPE "NOTAG"
#define V_TAG_VALUE "NOTAG"
#define V_TAG_DATE "NOTAG"
#define V_TAG_SYSTEM "NOTAG"
#define V_TAG_SYSTEM_REV "NOTAG"
#define V_TAG_BUILDER "NOTAG"
#endif

namespace fastos {

char VersionTag[] = V_TAG;
char VersionTagDate[] = V_TAG_DATE;
char VersionTagSystem[] = V_TAG_SYSTEM;
char VersionTagSystemRev[] = V_TAG_SYSTEM_REV;
char VersionTagBuilder[] = V_TAG_BUILDER;

} // namespace fastos
