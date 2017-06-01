// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("searchable_stats_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/util/searchable_stats.h>

using namespace search;

class Test : public vespalib::TestApp {
public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("searchable_stats_test");
    {
        SearchableStats stats;
        EXPECT_EQUAL(0u, stats.memoryUsage().allocatedBytes());
        EXPECT_EQUAL(0u, stats.docsInMemory());
        EXPECT_EQUAL(0u, stats.sizeOnDisk());
        {
            SearchableStats rhs;
            EXPECT_EQUAL(&rhs.memoryUsage(MemoryUsage(100,0,0,0)), &rhs);
            EXPECT_EQUAL(&rhs.docsInMemory(10), &rhs);
            EXPECT_EQUAL(&rhs.sizeOnDisk(1000), &rhs);
            EXPECT_EQUAL(&stats.add(rhs), &stats);
        }
        EXPECT_EQUAL(100u, stats.memoryUsage().allocatedBytes());
        EXPECT_EQUAL(10u, stats.docsInMemory());
        EXPECT_EQUAL(1000u, stats.sizeOnDisk());
        EXPECT_EQUAL(&stats.add(SearchableStats().memoryUsage(MemoryUsage(100,0,0,0)).docsInMemory(10).sizeOnDisk(1000)), &stats);
        EXPECT_EQUAL(200u, stats.memoryUsage().allocatedBytes());
        EXPECT_EQUAL(20u, stats.docsInMemory());
        EXPECT_EQUAL(2000u, stats.sizeOnDisk());
    }
    TEST_DONE();
}

TEST_APPHOOK(Test);
