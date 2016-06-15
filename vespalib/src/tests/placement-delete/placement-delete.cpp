// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;

bool checkCompile(const std::string &base) {
    std::string out;
    std::string gcc = getenv("CXX_PROG"); // TODO: override from environment
    std::string cmd = make_string("%s -o %s %s.cpp 2>&1", gcc.c_str(), base.c_str(), base.c_str());
    bool ok = SlaveProc::run(cmd.c_str(), out);
    fprintf(stderr, "CMD: %s\n(compile output follows...)\n%s\n", cmd.c_str(), out.c_str());
    return ok;
}

TEST("require that valid test program can be compiled") {
    EXPECT_EQUAL(checkCompile("hello"), true);
}

TEST("require that bogus test program can not be compiled") {
    EXPECT_EQUAL(checkCompile("fail"), false);
}

TEST("require that templated placement delete is instantiated resulting in a compile error") {
    EXPECT_EQUAL(checkCompile("undef"), false);
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
