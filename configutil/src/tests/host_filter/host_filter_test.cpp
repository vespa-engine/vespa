// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <lib/hostfilter.h>
#include <vespa/vespalib/gtest/gtest.h>

TEST(HostFilterTest, empty_hostfilter_includes_any_and_all_hosts)
{
    HostFilter filter;
    EXPECT_TRUE(filter.includes("foo.yahoo.com"));
}

TEST(HostFilterTest, explicit_host_set_limits_to_provided_hosts_only)
{
    HostFilter::HostSet hosts({"bar.yahoo.com", "zoidberg.yahoo.com"});
    HostFilter filter(std::move(hosts));
    EXPECT_TRUE(filter.includes("bar.yahoo.com"));
    EXPECT_TRUE(filter.includes("zoidberg.yahoo.com"));
    EXPECT_FALSE(filter.includes("foo.yahoo.com"));
}

GTEST_MAIN_RUN_ALL_TESTS()
