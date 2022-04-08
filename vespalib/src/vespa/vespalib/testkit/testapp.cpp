// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testapp.h"

namespace vespalib {

int
TestApp::Entry(int argc, char **argv)
{
    _argc = argc;
    _argv = argv;
    return Main();
}

TestApp::~TestApp() = default;

} // namespace vespalib
