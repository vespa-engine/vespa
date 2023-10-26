// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/group.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/stringtokenizer.h>

namespace storage::lib {

namespace {

Group::UP createLeafGroup(uint16_t index, const std::string& name,
                          double capacity, const std::string& nodelist)
{
    Group::UP group(new Group(index, name));
    group->setCapacity(capacity);
    vespalib::StringTokenizer st(nodelist, ",");
    std::vector<uint16_t> nodes(st.size());
    for (uint32_t i=0; i<st.size(); ++i) {
        nodes[i] = atoi(st[i].data());
    }
    group->setNodes(nodes);
    return group;
}

}

TEST(GroupTest, test_config_hash)
{
    Group rootGroup(12, "foo", Group::Distribution("1|*"), 3);
    rootGroup.addSubGroup(createLeafGroup(4, "bar", 1.5, "1,4,6,8"));
    rootGroup.addSubGroup(createLeafGroup(6, "ror", 1.2, "3,10,11"));
    rootGroup.addSubGroup(createLeafGroup(15, "ing", 1.0, "13,15"));

    vespalib::string expected = "(12d1|*(4c1.5;1;4;6;8)(6c1.2;3;10;11)(15;13;15))";
    EXPECT_EQ(expected, rootGroup.getDistributionConfigHash());
}

/**
 * To maintain backwards compatibility, distribution config hashes must be
 * output with the same node order as the groups were configured with, even
 * if their internal node list has a well-defined ordering.
 */
TEST(GroupTest, config_hash_uses_original_input_ordering)
{
    Group rootGroup(1, "root", Group::Distribution("1|*"), 2);
    rootGroup.addSubGroup(createLeafGroup(2, "fluffy", 1.0, "5,2,7,6"));
    rootGroup.addSubGroup(createLeafGroup(3, "bunny", 1.0, "15,10,12,11"));

    vespalib::string expected = "(1d1|*(2;5;2;7;6)(3;15;10;12;11))";
    EXPECT_EQ(expected, rootGroup.getDistributionConfigHash());
}

/**
 * Unlike node indices, groups have always been output in ascending order in
 * the config hash, and we must ensure this remains the case.
 *
 * Who said anything about internal consistency, anyway?
 */
TEST(GroupTest, config_hash_subgroups_are_ordered_by_group_index)
{
    Group rootGroup(1, "root", Group::Distribution("1|*"), 2);
    rootGroup.addSubGroup(createLeafGroup(5, "fluffy", 1.0, "5,2,7,6"));
    rootGroup.addSubGroup(createLeafGroup(3, "bunny", 1.0, "15,10,12,11"));
    rootGroup.addSubGroup(createLeafGroup(4, "kitten", 1.0, "3,4,8"));

    vespalib::string expected = "(1d1|*(3;15;10;12;11)(4;3;4;8)(5;5;2;7;6))";
    EXPECT_EQ(expected, rootGroup.getDistributionConfigHash());
}

}
