// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/config/config.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <random>

namespace storage {

struct GlobalBucketSpaceDistributionConverterTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(GlobalBucketSpaceDistributionConverterTest);
    CPPUNIT_TEST(can_transform_flat_cluster_config);
    CPPUNIT_TEST(can_transform_single_level_multi_group_config);
    CPPUNIT_TEST(can_transform_multi_level_multi_group_config);
    CPPUNIT_TEST(can_transform_heterogenous_multi_group_config);
    CPPUNIT_TEST(can_transform_concrete_distribution_instance);
    CPPUNIT_TEST(config_retired_state_is_propagated);
    CPPUNIT_TEST(group_capacities_are_propagated);
    CPPUNIT_TEST(global_distribution_has_same_owner_distributors_as_default);
    CPPUNIT_TEST_SUITE_END();

    void can_transform_flat_cluster_config();
    void can_transform_single_level_multi_group_config();
    void can_transform_multi_level_multi_group_config();
    void can_transform_heterogenous_multi_group_config();
    void can_transform_concrete_distribution_instance();
    void config_retired_state_is_propagated();
    void group_capacities_are_propagated();
    void global_distribution_has_same_owner_distributors_as_default();
};

CPPUNIT_TEST_SUITE_REGISTRATION(GlobalBucketSpaceDistributionConverterTest);

using DistributionConfig = vespa::config::content::StorDistributionConfig;

namespace {

vespalib::string default_to_global_config(const vespalib::string& default_config) {
    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    auto as_global = GlobalBucketSpaceDistributionConverter::convert_to_global(*default_cfg);
    return GlobalBucketSpaceDistributionConverter::config_to_string(*as_global);
}

vespalib::string default_flat_config(
R"(redundancy 1
group[1]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[3]
group[0].nodes[0].index 0
group[0].nodes[1].index 1
group[0].nodes[2].index 2
)");

vespalib::string expected_flat_global_config(
R"(redundancy 3
initial_redundancy 0
ensure_primary_persisted true
ready_copies 3
active_per_leaf_group true
distributor_auto_ownership_transfer_on_whole_group_down true
group[0].index "invalid"
group[0].name "invalid"
group[0].capacity 1
group[0].partitions "*"
group[0].nodes[0].index 0
group[0].nodes[0].retired false
group[0].nodes[1].index 1
group[0].nodes[1].retired false
group[0].nodes[2].index 2
group[0].nodes[2].retired false
disk_distribution MODULO_BID
)");

}

void GlobalBucketSpaceDistributionConverterTest::can_transform_flat_cluster_config() {
    CPPUNIT_ASSERT_EQUAL(expected_flat_global_config, default_to_global_config(default_flat_config));
}


void GlobalBucketSpaceDistributionConverterTest::can_transform_single_level_multi_group_config() {
    vespalib::string default_config(
R"(redundancy 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[3]
group[1].nodes[0].index 0
group[1].nodes[1].index 1
group[1].nodes[2].index 2
group[2].name rack1
group[2].index 1
group[2].nodes[3]
group[2].nodes[0].index 3
group[2].nodes[1].index 4
group[2].nodes[2].index 5
)");

    // The config converter cannot distinguish between default values
    // and explicitly set ones, so we get a few more entries in our output
    // config string.
    // Most crucial parts of the transformed config is the root redundancy
    // and the new partition config. We test _all_ config fields here so that
    // we catch anything we miss transferring state of.
    vespalib::string expected_global_config(
R"(redundancy 6
initial_redundancy 0
ensure_primary_persisted true
ready_copies 6
active_per_leaf_group true
distributor_auto_ownership_transfer_on_whole_group_down true
group[0].index "invalid"
group[0].name "invalid"
group[0].capacity 1
group[0].partitions "3|3|*"
group[1].index "0"
group[1].name "rack0"
group[1].capacity 1
group[1].partitions ""
group[1].nodes[0].index 0
group[1].nodes[0].retired false
group[1].nodes[1].index 1
group[1].nodes[1].retired false
group[1].nodes[2].index 2
group[1].nodes[2].retired false
group[2].index "1"
group[2].name "rack1"
group[2].capacity 1
group[2].partitions ""
group[2].nodes[0].index 3
group[2].nodes[0].retired false
group[2].nodes[1].index 4
group[2].nodes[1].retired false
group[2].nodes[2].index 5
group[2].nodes[2].retired false
disk_distribution MODULO_BID
)");
    CPPUNIT_ASSERT_EQUAL(expected_global_config, default_to_global_config(default_config));
}

void GlobalBucketSpaceDistributionConverterTest::can_transform_multi_level_multi_group_config() {
    vespalib::string default_config(
R"(redundancy 2
group[5]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions *|*
group[0].nodes[0]
group[1].name switch0
group[1].index 0
group[1].partitions 1|*
group[1].nodes[0]
group[2].name rack0
group[2].index 0.0
group[2].nodes[1]
group[2].nodes[0].index 0
group[3].name rack1
group[3].index 0.1
group[3].nodes[1]
group[3].nodes[0].index 1
group[4].name switch0
group[4].index 1
group[4].partitions *
group[4].nodes[0]
group[5].name rack0
group[5].index 1.0
group[5].nodes[1]
group[5].nodes[0].index 2
group[6].name rack1
group[6].index 1.1
group[6].nodes[1]
group[6].nodes[0].index 3
)");

    // Note: leaf groups do not have a partition spec, only inner groups.
    vespalib::string expected_global_config(
R"(redundancy 4
initial_redundancy 0
ensure_primary_persisted true
ready_copies 4
active_per_leaf_group true
distributor_auto_ownership_transfer_on_whole_group_down true
group[0].index "invalid"
group[0].name "invalid"
group[0].capacity 1
group[0].partitions "2|2|*"
group[1].index "0"
group[1].name "switch0"
group[1].capacity 1
group[1].partitions "1|1|*"
group[2].index "0.0"
group[2].name "rack0"
group[2].capacity 1
group[2].partitions ""
group[2].nodes[0].index 0
group[2].nodes[0].retired false
group[3].index "0.1"
group[3].name "rack1"
group[3].capacity 1
group[3].partitions ""
group[3].nodes[0].index 1
group[3].nodes[0].retired false
group[4].index "1"
group[4].name "switch0"
group[4].capacity 1
group[4].partitions "1|1|*"
group[5].index "1.0"
group[5].name "rack0"
group[5].capacity 1
group[5].partitions ""
group[5].nodes[0].index 2
group[5].nodes[0].retired false
group[6].index "1.1"
group[6].name "rack1"
group[6].capacity 1
group[6].partitions ""
group[6].nodes[0].index 3
group[6].nodes[0].retired false
disk_distribution MODULO_BID
)");
    CPPUNIT_ASSERT_EQUAL(expected_global_config, default_to_global_config(default_config));
}

void GlobalBucketSpaceDistributionConverterTest::can_transform_heterogenous_multi_group_config() {
    vespalib::string default_config(
R"(redundancy 2
ready_copies 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[1]
group[1].nodes[0].index 0
group[2].name rack1
group[2].index 1
group[2].nodes[2]
group[2].nodes[0].index 1
group[2].nodes[1].index 2
)");

    vespalib::string expected_global_config(
R"(redundancy 3
initial_redundancy 0
ensure_primary_persisted true
ready_copies 3
active_per_leaf_group true
distributor_auto_ownership_transfer_on_whole_group_down true
group[0].index "invalid"
group[0].name "invalid"
group[0].capacity 1
group[0].partitions "1|2|*"
group[1].index "0"
group[1].name "rack0"
group[1].capacity 1
group[1].partitions ""
group[1].nodes[0].index 0
group[1].nodes[0].retired false
group[2].index "1"
group[2].name "rack1"
group[2].capacity 1
group[2].partitions ""
group[2].nodes[0].index 1
group[2].nodes[0].retired false
group[2].nodes[1].index 2
group[2].nodes[1].retired false
disk_distribution MODULO_BID
)");
    CPPUNIT_ASSERT_EQUAL(expected_global_config, default_to_global_config(default_config));
}

void GlobalBucketSpaceDistributionConverterTest::can_transform_concrete_distribution_instance() {
    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_flat_config);
    lib::Distribution flat_distr(*default_cfg);
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(flat_distr);
    CPPUNIT_ASSERT_EQUAL(expected_flat_global_config, global_distr->serialize());
}

void GlobalBucketSpaceDistributionConverterTest::config_retired_state_is_propagated() {
    vespalib::string default_config(
R"(redundancy 1
group[1]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[3]
group[0].nodes[0].index 0
group[0].nodes[0].retired false
group[0].nodes[1].index 1
group[0].nodes[1].retired true
group[0].nodes[2].index 2
group[0].nodes[2].retired true
)");

    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    auto as_global = GlobalBucketSpaceDistributionConverter::convert_to_global(*default_cfg);

    CPPUNIT_ASSERT_EQUAL(size_t(1), as_global->group.size());
    CPPUNIT_ASSERT_EQUAL(size_t(3), as_global->group[0].nodes.size());
    CPPUNIT_ASSERT_EQUAL(false, as_global->group[0].nodes[0].retired);
    CPPUNIT_ASSERT_EQUAL(true, as_global->group[0].nodes[1].retired);
    CPPUNIT_ASSERT_EQUAL(true, as_global->group[0].nodes[2].retired);
}

void GlobalBucketSpaceDistributionConverterTest::group_capacities_are_propagated() {
    vespalib::string default_config(
R"(redundancy 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].capacity 5
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].capacity 2
group[1].nodes[1]
group[1].nodes[0].index 0
group[2].name rack1
group[2].capacity 3
group[2].index 1
group[2].nodes[1]
group[2].nodes[0].index 1
)");
    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    auto as_global = GlobalBucketSpaceDistributionConverter::convert_to_global(*default_cfg);

    CPPUNIT_ASSERT_EQUAL(size_t(3), as_global->group.size());
    CPPUNIT_ASSERT_DOUBLES_EQUAL(5.0, as_global->group[0].capacity, 0.00001);
    CPPUNIT_ASSERT_DOUBLES_EQUAL(2.0, as_global->group[1].capacity, 0.00001);
    CPPUNIT_ASSERT_DOUBLES_EQUAL(3.0, as_global->group[2].capacity, 0.00001);
}

void GlobalBucketSpaceDistributionConverterTest::global_distribution_has_same_owner_distributors_as_default() {
    vespalib::string default_config(
R"(redundancy 2
ready_copies 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[1]
group[1].nodes[0].index 0
group[2].name rack1
group[2].index 1
group[2].nodes[2]
group[2].nodes[0].index 1
group[2].nodes[1].index 2
)");

    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    auto global_cfg = GlobalBucketSpaceDistributionConverter::convert_to_global(*default_cfg);

    lib::Distribution default_distr(*default_cfg);
    lib::Distribution global_distr(*global_cfg);
    lib::ClusterState state("distributor:6 storage:6");

    std::mt19937 rng;
    std::uniform_int_distribution<uint64_t> d(0, UINT64_MAX);
    for (int i = 0; i < 100; ++i) {
        document::BucketId bucket(16, d(rng));
        const auto default_index = default_distr.getIdealDistributorNode(state, bucket, "ui");
        const auto global_index = global_distr.getIdealDistributorNode(state, bucket, "ui");
        CPPUNIT_ASSERT_EQUAL(default_index, global_index);
    }
}

}