// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class CppUnitTestRunner
 * @ingroup cppunit
 *
 * @brief Application for running cppunit tests.
 *
 * This is an application to use when running cppunit tests, currently used
 * in document,vdslib,storageapi and storage.
 *
 * It is built like a library, as one need to create one per project, but the
 * cppunit test application file in each project can now only contain a little
 * main method, creating an instance of this class and calling run.
 *
 * See storage/src/cpp/tests/testrunner.h for an example of simple app using
 * this.
 *
 * When using this test binary you have the following options.
 *
 * If the TEST_SUBSET environment variable is set, only tests matching the
 * pattern given in the environment is run. For instance, by doing
 * TEST_SUBSET=foo ./testrunner, only tests that match the regular expression
 * .*foo.* will be run. Optionally you can postfix your expression with a
 * dollar, to only run tests that end with the given string. You can match
 * against any part of the function shown in verbose mode. For instance
 * TEST_SUBSET=foo::bar$ will run tests whose test class ends in foo, and
 * having test name bar.
 *
 * You can specify --verbose mode. In verbose mode, each test name is printed
 * to stdout when started, and after completion, the runtime of the test is
 * shown. This is very useful to identify slow unit tests which should be
 * improved, and also to see in what test the system might be hung up in. In
 * addition, in verbose mode, a vespa log entry is given at the start and end
 * of each test, such that one can identify which parts of the vespa log belongs
 * to each test, in case you are redirecting the log to a file.
 *
 * You can also use the --includestress option to also include tests that match
 * the regular expression '.*[sS]tress.*'. These are excluded by default, such
 * that regular test runs can be quick.
 */

#pragma once

#include <cppunit/TestSuite.h>

namespace vdstestlib {

class CppUnitTestRunner {
public:
    CppUnitTestRunner();

    void listTests(const CppUnit::TestSuite *tests);
    int run(int argc, const char * argv[]);

};

} // vdstestlib

