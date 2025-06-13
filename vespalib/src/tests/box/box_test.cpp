// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/box.h>

using namespace vespalib;

void checkValues(const std::vector<int> &values, size_t n) {
    ASSERT_EQ(n, values.size());
    for (size_t i = 0; i < n; ++i) {
        EXPECT_EQ(int(10 + (10 * i)), values[i]);
    }
}

TEST(BoxTest, require_that_boxes_can_be_created_and_converted_to_vector) {
    Box<int> box;
    box.add(10).add(20).add(30);
    checkValues(box, 3);
}

TEST(BoxTest, require_that_boxes_can_be_created_in_place) {
    checkValues(Box<int>().add(10).add(20).add(30), 3);
}

TEST(BoxTest, require_that_make_box_works) {
    checkValues(make_box(10), 1);
    checkValues(make_box(10, 20), 2);
    checkValues(make_box(10, 20, 30), 3);
    checkValues(make_box(10, 20, 30, 40), 4);
    checkValues(make_box(10, 20, 30, 40, 50), 5);
}

GTEST_MAIN_RUN_ALL_TESTS()
