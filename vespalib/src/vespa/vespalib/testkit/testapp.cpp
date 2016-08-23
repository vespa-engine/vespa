// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "testapp.h"

namespace vespalib {

TestMaster &TestApp::master(TestMaster::master);

TestApp::TestApp()
    : FastOS_Application(),
      _name("<unnamed>")
{
}

TestApp::~TestApp()
{
}

const std::string&
TestApp::GetSourceDirectory()
{
    static const std::string srcDir = [] () {
        std::string dir(".");
        const char* env = getenv("SOURCE_DIRECTORY");
        if (env) {
            dir = env;
        }
        if (*dir.rbegin() != '/') {
            dir += "/";
        }
        return dir;
    } ();
    return srcDir;
}

void
TestApp::ReportInit(const char *name)
{
    _name = name;
}

bool
TestApp::ReportConclusion()
{
    return true;
}

} // namespace vespalib
