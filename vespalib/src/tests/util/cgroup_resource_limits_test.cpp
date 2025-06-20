// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <vespa/vespalib/util/cgroup_resource_limits.h>
#include <vespa/vespalib/util/size_literals.h>
#include <string>

namespace vespalib {

class CGroupResourceLimitsTest : public ::testing::Test
{
protected:
    CGroupResourceLimitsTest();
    ~CGroupResourceLimitsTest();
    void check_limits(std::string_view name, const std::optional<uint64_t>& memory_limit, const std::optional<uint32_t>& cpu_limit);
};

CGroupResourceLimitsTest::CGroupResourceLimitsTest() = default;
CGroupResourceLimitsTest::~CGroupResourceLimitsTest() = default;

void
CGroupResourceLimitsTest::check_limits(std::string_view subdir, const std::optional<uint64_t>& memory_limit, const std::optional<uint32_t>& cpu_limit)
{
    std::string base = "cgroup_resource_limits/" + std::string(subdir);
    auto src_base = TEST_PATH(base);
    CGroupResourceLimits cg_limits(src_base + "/cgroup", src_base + "/self");
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
