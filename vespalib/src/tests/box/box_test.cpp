// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/box.h>

using namespace vespalib;

void checkValues(const std::vector<int> &values, size_t n) {
    ASSERT_EQUAL(n, values.size());
    for (size_t i = 0; i < n; ++i) {
        EXPECT_EQUAL(int(10 + (10 * i)), values[i]);
    }
}

TEST("require that boxes can be created and converted to vector") {
    Box<int> box;
    box.add(10).add(20).add(30);
    checkValues(box, 3);
}

TEST("require that boxes can be created in place") {
    checkValues(Box<int>().add(10).add(20).add(30), 3);
}

TEST("require that make_box works") {
    checkValues(make_box(10), 1);
    checkValues(make_box(10, 20), 2);
    checkValues(make_box(10, 20, 30), 3);
    checkValues(make_box(10, 20, 30, 40), 4);
    checkValues(make_box(10, 20, 30, 40, 50), 5);
}

TEST_MAIN() { TEST_RUN_ALL(); }
