// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/messagebus/vtag.h>
#include <stdio.h>
#include <vespa/vespalib/component/version.h>

int main(int, char **)
{
    printf("version tag: %s\n", mbus::VersionTag);
    printf("version tag date: %s\n", mbus::VersionTagDate);
    printf("version tag system: %s\n", mbus::VersionTagSystem);
    printf("version tag system rev: %s\n", mbus::VersionTagSystemRev);
    printf("version tag builder: %s\n", mbus::VersionTagBuilder);
    printf("nice version:\n\t");
    mbus::Vtag::printVersionNice();
    printf("\n");
    printf("currentVersion object: %s\n", mbus::Vtag::currentVersion.toString().c_str());
    return 0;
}
