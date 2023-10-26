// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/component/vtag.h>
#include <stdio.h>

int main(int, char **)
{
    printf("version tag: %s\n", vespalib::VersionTag);
    printf("version tag date: %s\n", vespalib::VersionTagDate);
    printf("version tag system: %s\n", vespalib::VersionTagSystem);
    printf("version tag system rev: %s\n", vespalib::VersionTagSystemRev);
    printf("version tag builder: %s\n", vespalib::VersionTagBuilder);
    printf("nice version:\n\t");
    vespalib::Vtag::printVersionNice();
    printf("\n");
    printf("currentVersion object: %s\n", vespalib::Vtag::currentVersion.toString().c_str());
    return 0;
}
