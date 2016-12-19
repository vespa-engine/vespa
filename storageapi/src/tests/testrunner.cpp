// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/cppunittestrunner.h>

LOG_SETUP("storageapicppunittestrunner");

int
main(int argc, char **argv)
{
    vdstestlib::CppUnitTestRunner testRunner;
    return testRunner.run(argc, argv);
}
