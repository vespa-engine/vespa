// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>

#include <cppunit/extensions/HelperMacros.h>

using document::BucketSpace;

namespace storage::lib {

using ClusterStatePtr = std::shared_ptr<const ClusterState>;

struct ClusterStateBundleTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(ClusterStateBundleTest);
    CPPUNIT_TEST(derived_state_is_returned_if_bucket_space_is_found);
    CPPUNIT_TEST(baseline_state_is_returned_if_bucket_space_is_not_found);
    CPPUNIT_TEST(verify_equality_operator);
    CPPUNIT_TEST_SUITE_END();

    void derived_state_is_returned_if_bucket_space_is_found();
    void baseline_state_is_returned_if_bucket_space_is_not_found();
    void verify_equality_operator();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ClusterStateBundleTest);

struct Fixture {
    ClusterState baselineState;
    ClusterStatePtr derivedState;
    ClusterStateBundle bundle;
    Fixture()
        : baselineState("storage:2"),
          derivedState(std::make_shared<const ClusterState>("storage:2 .1.s:m")),
          bundle(baselineState, {{BucketSpace(1), derivedState}})
    {}
    ~Fixture() {}
};

void
ClusterStateBundleTest::derived_state_is_returned_if_bucket_space_is_found()
{
    Fixture f;
    CPPUNIT_ASSERT_EQUAL(*f.derivedState, *f.bundle.getDerivedClusterState(BucketSpace(1)));
}

void
ClusterStateBundleTest::baseline_state_is_returned_if_bucket_space_is_not_found()
{
    Fixture f;
    CPPUNIT_ASSERT_EQUAL(f.baselineState, *f.bundle.getDerivedClusterState(BucketSpace(2)));
}

ClusterStateBundle
makeBundle(const vespalib::string &baselineState, const std::map<BucketSpace, vespalib::string> derivedStates)
{
    ClusterStateBundle::BucketSpaceStateMapping derivedBucketSpaceStates;
    for (const auto &entry : derivedStates) {
        derivedBucketSpaceStates[entry.first] = std::make_shared<const ClusterState>(entry.second);
    }
    return ClusterStateBundle(ClusterState(baselineState), std::move(derivedBucketSpaceStates));
}

void
ClusterStateBundleTest::verify_equality_operator()
{
    Fixture f;
    CPPUNIT_ASSERT(f.bundle != makeBundle("storage:3", {{BucketSpace(1), "storage:2 .1.s:m"}}));
    CPPUNIT_ASSERT(f.bundle != makeBundle("storage:2", {}));
    CPPUNIT_ASSERT(f.bundle != makeBundle("storage:2", {{BucketSpace(1), "storage:2 .0.s:m"}}));
    CPPUNIT_ASSERT(f.bundle != makeBundle("storage:2", {{BucketSpace(2), "storage:2 .1.s:m"}}));

    CPPUNIT_ASSERT_EQUAL(f.bundle, makeBundle("storage:2", {{BucketSpace(1), "storage:2 .1.s:m"}}));
}

}

