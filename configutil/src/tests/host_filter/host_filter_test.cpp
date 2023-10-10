// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <lib/hostfilter.h>

TEST("empty hostfilter includes any and all hosts") {
    HostFilter filter;
    EXPECT_TRUE(filter.includes("foo.yahoo.com"));
}

TEST("explicit host set limits to provided hosts only") {
    HostFilter::HostSet hosts({"bar.yahoo.com", "zoidberg.yahoo.com"});
    HostFilter filter(std::move(hosts));
    EXPECT_TRUE(filter.includes("bar.yahoo.com"));
    EXPECT_TRUE(filter.includes("zoidberg.yahoo.com"));
    EXPECT_FALSE(filter.includes("foo.yahoo.com"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
