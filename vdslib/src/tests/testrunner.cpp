// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/cppunittestrunner.h>

#include <vespa/log/log.h>
LOG_SETUP("vdslibcppunittestrunner");

int
main(int argc, const char *argv[])
{
    vdstestlib::CppUnitTestRunner testRunner;
    return testRunner.run(argc, argv);
}

