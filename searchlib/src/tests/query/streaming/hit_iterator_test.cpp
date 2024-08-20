// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/hit_iterator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::streaming::Hit;
using search::streaming::HitList;
using search::streaming::HitIterator;

using FieldElement = HitIterator::FieldElement;

namespace {

HitList
make_hit_list()
{
    HitList hl;
    hl.emplace_back(11, 0, 10, 0);
    hl.emplace_back(11, 0, 10, 5);
    hl.emplace_back(11, 1, 12, 0);
    hl.emplace_back(11, 1, 12, 7);
    hl.emplace_back(12, 1, 13, 0);
    hl.emplace_back(12, 1, 13, 9);
    return hl;
}

void
check_seek_to_field_elem(HitIterator& it, const FieldElement& field_element, const Hit* exp_ptr, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_TRUE(it.seek_to_field_element(field_element));
    EXPECT_TRUE(it.valid());
    EXPECT_EQ(exp_ptr, &*it);
}

void
check_seek_to_field_elem_failure(HitIterator& it, const FieldElement& field_element, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_FALSE(it.seek_to_field_element(field_element));
    EXPECT_FALSE(it.valid());
}

void
check_step_in_field_element(HitIterator& it, FieldElement& field_element, bool exp_success, const Hit* exp_ptr, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(exp_success, it.step_in_field_element(field_element));
    if (exp_ptr) {
        EXPECT_TRUE(it.valid());
        EXPECT_EQ(it.get_field_element(), field_element);
        EXPECT_EQ(exp_ptr, &*it);
    } else {
        EXPECT_FALSE(it.valid());
    }
}

void
check_seek_in_field_element(HitIterator& it, uint32_t position, FieldElement& field_element, bool exp_success, const Hit* exp_ptr, const std::string& label)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(exp_success, it.seek_in_field_element(position, field_element));
    if (exp_ptr) {
        EXPECT_TRUE(it.valid());
        EXPECT_EQ(it.get_field_element(), field_element);
        EXPECT_EQ(exp_ptr, &*it);
    } else {
        EXPECT_FALSE(it.valid());
    }
}

}

TEST(HitITeratorTest, seek_to_field_element)
{
    auto hl = make_hit_list();
    HitIterator it(hl);
    EXPECT_TRUE(it.valid());
    EXPECT_EQ(&hl[0], &*it);
    check_seek_to_field_elem(it, FieldElement(0, 0), &hl[0], "(0, 0)");
    check_seek_to_field_elem(it, FieldElement(11, 0), &hl[0], "(11, 0)");
    check_seek_to_field_elem(it, FieldElement(11, 1), &hl[2], "(11, 1)");
    check_seek_to_field_elem(it, FieldElement(11, 2), &hl[4], "(11, 2)");
    check_seek_to_field_elem(it, FieldElement(12, 0), &hl[4], "(12, 0)");
    check_seek_to_field_elem(it, FieldElement(12, 1), &hl[4], "(12, 1)");
    check_seek_to_field_elem_failure(it, FieldElement(12, 2), "(12, 2)");
    check_seek_to_field_elem_failure(it, FieldElement(13, 0), "(13, 0)");
}

TEST(HitIteratorTest, step_in_field_element)
{
    auto hl = make_hit_list();
    HitIterator it(hl);
    auto field_element = it.get_field_element();
    check_step_in_field_element(it, field_element, true, &hl[1], "1");
    check_step_in_field_element(it, field_element, false, &hl[2], "2");
    check_step_in_field_element(it, field_element, true, &hl[3], "3");
    check_step_in_field_element(it, field_element, false, &hl[4], "4");
    check_step_in_field_element(it, field_element, true, &hl[5], "5");
    check_step_in_field_element(it, field_element, false, nullptr, "end");
}

TEST(hitIteratorTest, seek_in_field_elem)
{
    auto hl = make_hit_list();
    HitIterator it(hl);
    auto field_element = it.get_field_element();
    check_seek_in_field_element(it, 0, field_element, true, &hl[0], "0a");
    check_seek_in_field_element(it, 2, field_element, true, &hl[1], "2");
    check_seek_in_field_element(it, 5, field_element, true, &hl[1], "5");
    check_seek_in_field_element(it, 6, field_element, false, &hl[2], "6");
    check_seek_in_field_element(it, 0, field_element, true, &hl[2], "0b");
    check_seek_in_field_element(it, 1, field_element, true, &hl[3], "1");
    check_seek_in_field_element(it, 7, field_element, true, &hl[3], "7");
    check_seek_in_field_element(it, 8, field_element, false, &hl[4], "8");
    check_seek_in_field_element(it, 0, field_element, true, &hl[4], "0c");
    check_seek_in_field_element(it, 3, field_element, true, &hl[5], "3");
    check_seek_in_field_element(it, 9, field_element, true, &hl[5], "9");
    check_seek_in_field_element(it, 10, field_element, false, nullptr, "end");
}

GTEST_MAIN_RUN_ALL_TESTS()
