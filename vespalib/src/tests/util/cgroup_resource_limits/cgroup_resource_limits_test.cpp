// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/cgroup_resource_limits.h>
#include <vespa/vespalib/util/size_literals.h>

namespace vespalib {

class CGroupResourceLimitsTest : public ::testing::Test
{
protected:
    CGroupResourceLimitsTest();
    ~CGroupResourceLimitsTest();
    void check_limits(const std::string &name, const std::optional<uint64_t>& memory_limit, const std::optional<uint32_t>& cpu_limit);
};

CGroupResourceLimitsTest::CGroupResourceLimitsTest() = default;
CGroupResourceLimitsTest::~CGroupResourceLimitsTest() = default;

void
CGroupResourceLimitsTest::check_limits(const std::string &base, const std::optional<uint64_t>& memory_limit, const std::optional<uint32_t>& cpu_limit)
{
    CGroupResourceLimits cg_limits(base + "/cgroup", base + "/self");
    EXPECT_EQ(memory_limit, cg_limits.get_memory_limit());
    EXPECT_EQ(cpu_limit, cg_limits.get_cpu_limit());
}

TEST_F(CGroupResourceLimitsTest, no_cgroup)
{
    check_limits("no_cgroup", std::nullopt, std::nullopt);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v1_host)
{
    check_limits("cgroup_v1_host", 4_Mi, 3);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v1_host_nested)
{
    check_limits("cgroup_v1_host_nested", 5_Mi, 4);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v1_host_no_limit)
{
    check_limits("cgroup_v1_host_no_limit", std::nullopt, std::nullopt);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v1_container)
{
    check_limits("cgroup_v1_container", 8_Mi, 5);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v2_host)
{
    check_limits("cgroup_v2_host", 12_Mi, 7);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v2_host_nested)
{
    check_limits("cgroup_v2_host_nested", 13_Mi, 8);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v2_host_no_limit)
{
    check_limits("cgroup_v2_host_no_limit", std::nullopt, std::nullopt);
}

TEST_F(CGroupResourceLimitsTest, cgroup_v2_container)
{
    check_limits("cgroup_v2_container", 16_Mi, 9);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
