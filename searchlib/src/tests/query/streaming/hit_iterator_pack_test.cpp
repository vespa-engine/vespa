// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/hit_iterator_pack.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::streaming::HitIterator;
using search::streaming::HitIteratorPack;
using search::streaming::QueryNodeList;
using search::streaming::QueryTerm;
using search::streaming::QueryNodeResultBase;

using FieldElement = HitIterator::FieldElement;

TEST(HitIteratorPackTest, seek_to_matching_field_element)
{
    QueryNodeList qnl;
    auto qt = std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "7", "", QueryTerm::Type::WORD);
    qt->add(11, 0, 10, 0);
    qt->add(11, 0, 10, 5);
    qt->add(11, 1, 12, 0);
    qt->add(11, 1, 12, 0);
    qt->add(12, 1, 13, 0);
    qt->add(12, 1, 13, 0);
    qnl.emplace_back(std::move(qt));
    qt = std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), "8", "", QueryTerm::Type::WORD);
    qt->add(2, 0, 4, 0);
    qt->add(11, 0, 10, 0);
    qt->add(12, 1, 13, 0);
    qt->add(12, 2, 14, 0);
    qnl.emplace_back(std::move(qt));
    HitIteratorPack itr_pack(qnl);
    EXPECT_TRUE(itr_pack.all_valid());
    EXPECT_TRUE(itr_pack.seek_to_matching_field_element());
    EXPECT_EQ(FieldElement(11, 0), itr_pack.get_field_element_ref());
    EXPECT_TRUE(itr_pack.seek_to_matching_field_element());
    EXPECT_EQ(FieldElement(11, 0), itr_pack.get_field_element_ref());
    ++itr_pack.get_field_element_ref().second;
    EXPECT_TRUE(itr_pack.seek_to_matching_field_element());
    EXPECT_EQ(FieldElement(12, 1), itr_pack.get_field_element_ref());
    ++itr_pack.get_field_element_ref().second;
    EXPECT_FALSE(itr_pack.seek_to_matching_field_element());
}

GTEST_MAIN_RUN_ALL_TESTS()
