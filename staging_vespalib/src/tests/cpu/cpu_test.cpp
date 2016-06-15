// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("cpu_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/cpu.h>

using namespace vespalib;

class Test : public vespalib::TestApp
{
public:
    int Main();
};

int Test::Main()
{
    TEST_INIT("cpu_test");

    const X86CpuInfo & cpu = X86CpuInfo::cpuInfo();
    EXPECT_TRUE(cpu.hasMMX());
    EXPECT_TRUE(cpu.hasSSE());
    EXPECT_TRUE(cpu.hasSSE2());
    EXPECT_TRUE(cpu.hasSSE3());
    EXPECT_TRUE(cpu.hasCX16());

    X86CpuInfo::print(stdout);

    TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test)
