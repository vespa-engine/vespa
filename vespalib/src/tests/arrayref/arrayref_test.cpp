// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/arrayref.h>

using namespace vespalib;

TEST("require that default constructors create references to empty arrays") {
    ArrayRef<int> array_ref;
    ConstArrayRef<int> const_ref;
    EXPECT_EQUAL(array_ref.size(), 0u);
    EXPECT_EQUAL(array_ref.begin(), array_ref.end());
    EXPECT_EQUAL(const_ref.size(), 0u);
    EXPECT_EQUAL(const_ref.begin(), const_ref.end());
}

TEST("require that data can be referenced") {
    std::vector<int> data({1,2,3});
    ArrayRef<int> array_ref(data);
    ConstArrayRef<int> const_ref(data);
    EXPECT_EQUAL(array_ref.size(), 3u);
    EXPECT_EQUAL(array_ref.end() - array_ref.begin(), 3);
    EXPECT_EQUAL(array_ref[1], 2);
    EXPECT_EQUAL(const_ref.size(), 3u);
    EXPECT_EQUAL(const_ref.end() - const_ref.begin(), 3);
    EXPECT_EQUAL(const_ref[2], 3);
}

TEST("require that non-const array ref can be written to") {
    std::vector<int> data({1,2,3});
    ArrayRef<int> array_ref(data);
    array_ref[1] = 5;
    EXPECT_EQUAL(data[1], 5);
}

TEST("require that references can be constified") {
    std::vector<int> data({1,2,3});
    const ArrayRef<int> array_ref(data);
    ConstArrayRef<int> const_ref(array_ref);
    EXPECT_EQUAL(const_ref.size(), 3u);
    EXPECT_EQUAL(const_ref.end() - const_ref.begin(), 3);
    EXPECT_EQUAL(const_ref[2], 3);    
}

TEST("require that references can be unconstified") {
    std::vector<int> data({1,2,3});
    const ConstArrayRef<int> const_ref(data);
    ArrayRef<int> array_ref = unconstify(const_ref);
    EXPECT_EQUAL(array_ref.size(), 3u);
    EXPECT_EQUAL(array_ref.end() - array_ref.begin(), 3);
    EXPECT_EQUAL(array_ref[1], 2);
    array_ref[1] = 5;
    EXPECT_EQUAL(data[1], 5);
}

TEST_MAIN() { TEST_RUN_ALL(); }
