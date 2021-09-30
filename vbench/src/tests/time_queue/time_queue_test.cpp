// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_MT_FFF("time queue", 2, TimeQueue<int>(10.0, 5.0), vespalib::Gate(), vespalib::Gate()) {
    if (thread_id == 0) {
        f1.insert(std::unique_ptr<int>(new int(1)), 1.0);
        f1.insert(std::unique_ptr<int>(new int(2)), 3.0);
        f1.insert(std::unique_ptr<int>(new int(3)), 2.0);
        f2.countDown();
        f1.insert(std::unique_ptr<int>(new int(4)), 100.0);
        f1.insert(std::unique_ptr<int>(new int(5)), 101.0);
        f3.countDown();
    } else {
        double delay;
        std::vector<std::unique_ptr<int> > list;
        EXPECT_TRUE(f2.await(20s));
        EXPECT_FALSE(f3.await(20ms));
        {
            f1.extract(1.5, list, delay);
            ASSERT_EQUAL(1u, list.size());
            EXPECT_EQUAL(1, *list[0]);
            EXPECT_EQUAL(0.5, delay);
            list.clear();
        }
        {
            f1.extract(10.0, list, delay);
            ASSERT_EQUAL(2u, list.size());
            EXPECT_EQUAL(3, *list[0]);
            EXPECT_EQUAL(2, *list[1]);
            EXPECT_EQUAL(5.0, delay);
            list.clear();
        }
        {
            f1.extract(99.25, list, delay);
            EXPECT_EQUAL(0u, list.size());
            EXPECT_EQUAL(5.0, delay);
        }
        EXPECT_TRUE(f3.await(20s));
        {
            f1.extract(99.25, list, delay);
            EXPECT_EQUAL(0u, list.size());
            EXPECT_EQUAL(0.75, delay);
        }
        f1.discard();
        {
            f1.extract(101.5, list, delay);
            EXPECT_EQUAL(0u, list.size());
            EXPECT_EQUAL(5.0, delay);
        }
        f1.close();
        f1.insert(std::unique_ptr<int>(new int(6)), 102.0);
        f1.insert(std::unique_ptr<int>(new int(7)), 103.0);
        {
            f1.extract(103.5, list, delay);
            EXPECT_EQUAL(0u, list.size());
            EXPECT_EQUAL(5.0, delay);
        }
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
