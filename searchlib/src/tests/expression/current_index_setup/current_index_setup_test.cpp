// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/current_index_setup.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::expression::CurrentIndex;
using search::expression::CurrentIndexSetup;

TEST(CurrentIndexSetupTest, bound_structs_can_be_resolved) {
    CurrentIndexSetup setup;
    CurrentIndex foo_idx;
    CurrentIndex bar_idx;
    setup.bind("foo", foo_idx);
    setup.bind("foo.bar", bar_idx);
    EXPECT_EQ(setup.resolve("plain"), nullptr);
    EXPECT_EQ(setup.resolve("foo.a"), &foo_idx);
    EXPECT_EQ(setup.resolve("foo.b"), &foo_idx);
    EXPECT_EQ(setup.resolve("foo.c"), &foo_idx);
    EXPECT_EQ(setup.resolve("foo.bar.x"), &bar_idx);
    EXPECT_EQ(setup.resolve("foo.bar.y"), &bar_idx);
    EXPECT_EQ(setup.resolve("baz.f"), nullptr);
    EXPECT_EQ(setup.resolve("foo.baz.f"), nullptr);
}

TEST(CurrentIndexSetupTest, unbound_struct_usage_can_be_captured) {
    CurrentIndexSetup setup;
    CurrentIndexSetup::Usage usage;
    CurrentIndex foo_idx;
    setup.bind("foo", foo_idx);
    EXPECT_FALSE(usage.has_single_unbound_struct());
    {
        CurrentIndexSetup::Usage::Bind capture_guard(setup, usage);
        EXPECT_EQ(setup.resolve("foo.a"), &foo_idx);
        EXPECT_EQ(setup.resolve("bar.a"), nullptr);
        EXPECT_EQ(setup.resolve("bar.b"), nullptr);
        EXPECT_EQ(setup.resolve("plain"), nullptr);
    }
    EXPECT_EQ(setup.resolve("baz.a"), nullptr);
    EXPECT_TRUE(usage.has_single_unbound_struct());
    EXPECT_EQ(usage.get_unbound_struct_name(), "bar");
}

TEST(CurrentIndexSetupTest, multi_unbound_struct_conflict_can_be_captured) {
    CurrentIndexSetup setup;
    CurrentIndexSetup::Usage usage;
    EXPECT_FALSE(usage.has_single_unbound_struct());
    {
        CurrentIndexSetup::Usage::Bind capture_guard(setup, usage);
        EXPECT_FALSE(usage.has_single_unbound_struct());
        EXPECT_EQ(setup.resolve("foo.a"), nullptr);
        EXPECT_TRUE(usage.has_single_unbound_struct());
        EXPECT_EQ(setup.resolve("bar.a"), nullptr);
        EXPECT_FALSE(usage.has_single_unbound_struct());
    }
    EXPECT_FALSE(usage.has_single_unbound_struct());
}

GTEST_MAIN_RUN_ALL_TESTS()
