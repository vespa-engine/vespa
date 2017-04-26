// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/group.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace lib {

struct GroupTest : public CppUnit::TestFixture {
    void testConfigHash();
    void configHashUsesOriginalInputOrdering();
    void configHashSubgroupsAreOrderedByGroupIndex();

    CPPUNIT_TEST_SUITE(GroupTest);
    CPPUNIT_TEST(testConfigHash);
    CPPUNIT_TEST(configHashUsesOriginalInputOrdering);
    CPPUNIT_TEST(configHashSubgroupsAreOrderedByGroupIndex);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(GroupTest);

namespace {
    Group::UP createLeafGroup(uint16_t index, const std::string& name,
                              double capacity, const std::string& nodelist)
    {
        Group::UP group(new Group(index, name));
        group->setCapacity(capacity);
        vespalib::StringTokenizer st(nodelist, ",");
        std::vector<uint16_t> nodes(st.size());
        for (uint32_t i=0; i<st.size(); ++i) {
            nodes[i] = atoi(st[i].c_str());
        }
        group->setNodes(nodes);
        return group;
    }
}

void
GroupTest::testConfigHash()
{
    Group rootGroup(12, "foo", Group::Distribution("1|*"), 3);
    rootGroup.addSubGroup(createLeafGroup(4, "bar", 1.5, "1,4,6,8"));
    rootGroup.addSubGroup(createLeafGroup(6, "ror", 1.2, "3,10,11"));
    rootGroup.addSubGroup(createLeafGroup(15, "ing", 1.0, "13,15"));

    vespalib::string expected = "(12d1|*(4c1.5;1;4;6;8)(6c1.2;3;10;11)(15;13;15))";
    CPPUNIT_ASSERT_EQUAL(expected, rootGroup.getDistributionConfigHash());
}

/**
 * To maintain backwards compatibility, distribution config hashes must be
 * output with the same node order as the groups were configured with, even
 * if their internal node list has a well-defined ordering.
 */
void
GroupTest::configHashUsesOriginalInputOrdering()
{
    Group rootGroup(1, "root", Group::Distribution("1|*"), 2);
    rootGroup.addSubGroup(createLeafGroup(2, "fluffy", 1.0, "5,2,7,6"));
    rootGroup.addSubGroup(createLeafGroup(3, "bunny", 1.0, "15,10,12,11"));

    vespalib::string expected = "(1d1|*(2;5;2;7;6)(3;15;10;12;11))";
    CPPUNIT_ASSERT_EQUAL(expected, rootGroup.getDistributionConfigHash());
}

/**
 * Unlike node indices, groups have always been output in ascending order in
 * the config hash, and we must ensure this remains the case.
 *
 * Who said anything about internal consistency, anyway?
 */
void
GroupTest::configHashSubgroupsAreOrderedByGroupIndex()
{
    Group rootGroup(1, "root", Group::Distribution("1|*"), 2);
    rootGroup.addSubGroup(createLeafGroup(5, "fluffy", 1.0, "5,2,7,6"));
    rootGroup.addSubGroup(createLeafGroup(3, "bunny", 1.0, "15,10,12,11"));
    rootGroup.addSubGroup(createLeafGroup(4, "kitten", 1.0, "3,4,8"));

    vespalib::string expected = "(1d1|*(3;15;10;12;11)(4;3;4;8)(5;5;2;7;6))";
    CPPUNIT_ASSERT_EQUAL(expected, rootGroup.getDistributionConfigHash());
}

} // lib
} // storage
