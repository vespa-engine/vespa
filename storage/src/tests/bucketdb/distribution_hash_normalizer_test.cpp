// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/bucketdb/distribution_hash_normalizer.h>
#include <string>

namespace storage {

using Normalizer = DistributionHashNormalizer;

class DistributionHashNormalizerTest : public CppUnit::TestFixture {
public:
    CPPUNIT_TEST_SUITE(DistributionHashNormalizerTest);
    CPPUNIT_TEST(orderNonHierarchicRootGroupNodesByDistributionKey);
    CPPUNIT_TEST(mayHaveSameGroupIndexAsNodeIndex);
    CPPUNIT_TEST(emitOptionalCapacityForRootGroup);
    CPPUNIT_TEST(emitOptionalCapacityForSubGroups);
    CPPUNIT_TEST(hierarchicGroupsAreOrderedByGroupIndex);
    CPPUNIT_TEST(subgroupsOrderedOnEachNestingLevel);
    CPPUNIT_TEST(distributionSpecIsCopiedVerbatim);
    CPPUNIT_TEST(emptyInputYieldsEmptyOutput);
    CPPUNIT_TEST(parseFailureReturnsInputVerbatim);
    CPPUNIT_TEST_SUITE_END();

    void orderNonHierarchicRootGroupNodesByDistributionKey();
    void mayHaveSameGroupIndexAsNodeIndex();
    void emitOptionalCapacityForRootGroup();
    void emitOptionalCapacityForSubGroups();
    void hierarchicGroupsAreOrderedByGroupIndex();
    void subgroupsOrderedOnEachNestingLevel();
    void distributionSpecIsCopiedVerbatim();
    void emptyInputYieldsEmptyOutput();
    void parseFailureReturnsInputVerbatim();

private:
    DistributionHashNormalizer _normalizer;
};

CPPUNIT_TEST_SUITE_REGISTRATION(DistributionHashNormalizerTest);

void
DistributionHashNormalizerTest::orderNonHierarchicRootGroupNodesByDistributionKey()
{
    // Group index is first in list.
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(1;0;2;3;4;7)"),
                         _normalizer.normalize("(1;4;7;2;0;3)"));
}

void
DistributionHashNormalizerTest::mayHaveSameGroupIndexAsNodeIndex()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0;0;2;3;4;7)"),
                         _normalizer.normalize("(0;4;7;2;0;3)"));
}

void
DistributionHashNormalizerTest::emitOptionalCapacityForRootGroup()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0c12.5;1;2;3;4;7)"),
                         _normalizer.normalize("(0c12.5;1;4;7;2;3)"));
}

void
DistributionHashNormalizerTest::emitOptionalCapacityForSubGroups()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0d1|*(1c5.5;1)(2;2)(3c7;3))"),
                         _normalizer.normalize("(0d1|*(2;2)(1c5.5;1)(3c7;3))"));
}

void
DistributionHashNormalizerTest::hierarchicGroupsAreOrderedByGroupIndex()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0d1|*(0;0)(1;1)(3;3))"),
                         _normalizer.normalize("(0d1|*(3;3)(1;1)(0;0))"));
}

void
DistributionHashNormalizerTest::subgroupsOrderedOnEachNestingLevel()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0d1|*(1d3|*(2;2)(3;3))"
                                          "(4;1)(7d2|*(5;5)(6;6)))"),
                         _normalizer.normalize("(0d1|*(7d2|*(6;6)(5;5))"
                                               "(1d3|*(2;2)(3;3))(4;1))"));
}

void
DistributionHashNormalizerTest::distributionSpecIsCopiedVerbatim()
{
    // Definitely don't want to do any ordering of the distribution spec.
    CPPUNIT_ASSERT_EQUAL(vespalib::string("(0d3|2|1|*(0;0)(1;1)(3;3))"),
                         _normalizer.normalize("(0d3|2|1|*(3;3)(1;1)(0;0))"));
}

void
DistributionHashNormalizerTest::emptyInputYieldsEmptyOutput()
{
    // Technically a parse failure (only 4.2 has this behavior), but it's
    // explicitly checked for in BucketManager, so let's test it explicitly
    // here as well.
    CPPUNIT_ASSERT_EQUAL(vespalib::string(""), _normalizer.normalize(""));
}

// In the (unlikely) case that the parser somehow fails to capture all possible
// valid values of the distribution hash, fall back to returning the non-
// normalized string. A log warning will also be emitted (though that's not
// testable).
void
DistributionHashNormalizerTest::parseFailureReturnsInputVerbatim()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("onkel skrue"),
                         _normalizer.normalize("onkel skrue"));
}

} // storage

