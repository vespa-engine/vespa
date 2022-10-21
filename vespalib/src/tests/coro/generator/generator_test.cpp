// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/generator.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ranges>
#include <vector>

using vespalib::coro::Lazy;
using vespalib::coro::Generator;

class Unmovable {
private:
    int _value;
public:
    Unmovable() = delete;
    Unmovable &operator=(const Unmovable &) = delete;
    Unmovable(const Unmovable &) = delete;
    Unmovable &operator=(Unmovable &&) = delete;
    Unmovable(Unmovable &&) = delete;
    Unmovable(int value) : _value(value) {}
    int get() const { return _value; }
};

static_assert(std::input_iterator<Generator<int>::Iterator>);
static_assert(std::ranges::input_range<Generator<int>>);

Lazy<int> foo() { co_return 0; }

Generator<int> make_numbers(int begin, int end) {
    // co_yield co_await foo();
    for (int i = begin; i < end; ++i) {
        co_yield i;
    }
}

Generator<int> make_numbers(int begin, int split, int end) {
    co_yield make_numbers(begin, split);
    co_yield make_numbers(split, end);
}

static_assert(std::input_iterator<Generator<std::unique_ptr<int>>::Iterator>);
static_assert(std::ranges::input_range<Generator<std::unique_ptr<int>>>);

Generator<std::unique_ptr<int>> make_movable(int begin, int end) {
    for (int i = begin; i < end; ++i) {
        co_yield std::make_unique<int>(i);
    }
}

static_assert(std::input_iterator<Generator<Unmovable>::Iterator>);
static_assert(std::ranges::input_range<Generator<Unmovable>>);

Generator<Unmovable> make_unmovable(int begin, int end) {
    for (int i = begin; i < end; ++i) {
        co_yield Unmovable(i);
    }
}

Generator<int> make_failed_numbers(int begin, int end, int fail) {
    for (int i = begin; i < end; ++i) {
        REQUIRE(i != fail);
        co_yield i;
    }
}

Generator<int> make_safe(Generator<int> gen) {
    try {
        co_yield gen;
    } catch (...) {}
}

Generator<int> a_then_b(Generator<int> a, Generator<int> b) {
    co_yield a;
    co_yield b;
}

TEST(GeneratorTest, generate_some_numbers) {
    auto gen = make_numbers(1, 4);
    auto pos = gen.begin();
    auto end = gen.end();
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(*pos, 1);
    ++pos;
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(*pos, 2);
    ++pos;
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(*pos, 3);
    ++pos;
    EXPECT_TRUE(pos == end);
}

TEST(GeneratorTest, generate_no_numbers) {
    auto gen = make_numbers(1, 1);
    auto pos = gen.begin();
    auto end = gen.end();
    EXPECT_TRUE(pos == end);
}

TEST(GeneratorTest, generate_movable_values) {
    auto gen = make_movable(1,4);
    std::vector<std::unique_ptr<int>> res;
    for(auto pos = gen.begin(); pos != gen.end(); ++pos) {
        res.push_back(*pos);
    }
    ASSERT_EQ(res.size(), 3);
    EXPECT_EQ(*res[0], 1);
    EXPECT_EQ(*res[1], 2);
    EXPECT_EQ(*res[2], 3);
}

TEST(GeneratorTest, generate_unmovable_values) {
    auto gen = make_unmovable(1,4);
    auto pos = gen.begin();
    auto end = gen.end();
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(pos->get(), 1);
    ++pos;
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(pos->get(), 2);
    ++pos;
    ASSERT_FALSE(pos == end);
    EXPECT_EQ(pos->get(), 3);
    ++pos;
    EXPECT_TRUE(pos == end);
}

TEST(GeneratorTest, range_based_for_loop) {
    int expect = 1;
    for (int x: make_numbers(1, 10)) {
        EXPECT_EQ(x, expect);
        ++expect;
    }
    EXPECT_EQ(expect, 10);
}

TEST(GeneratorTest, explicit_range_for_loop) {
    int expect = 1;
    auto gen = make_numbers(1, 10);
    auto pos = std::ranges::begin(gen);
    auto end = std::ranges::end(gen);
    for (; pos != end; ++pos) {
        EXPECT_EQ(*pos, expect);
        ++expect;
    }
    EXPECT_EQ(expect, 10);
}

TEST(GeneratorTest, recursive_generator) {
    int expect = 1;
    for (int x: make_numbers(1, 4, 10)) {
        EXPECT_EQ(x, expect);
        ++expect;
    }
    EXPECT_EQ(expect, 10);
}

TEST(GeneratorTest, deeper_recursive_generator) {
    int expect = 1;
    for (int x: a_then_b(make_numbers(1, 3, 5), make_numbers(5, 7, 10))) {
        EXPECT_EQ(x, expect);
        ++expect;
    }
    EXPECT_EQ(expect, 10);
}

TEST(GeneratorTest, simple_exception) {
    auto gen = make_failed_numbers(1, 10, 5);
    auto pos = std::ranges::begin(gen);
    auto end = std::ranges::end(gen);
    EXPECT_EQ(*pos, 1);
    EXPECT_EQ(*++pos, 2);
    EXPECT_EQ(*++pos, 3);
    EXPECT_EQ(*++pos, 4);
    EXPECT_FALSE(pos == end);
    EXPECT_THROW(++pos, vespalib::RequireFailedException);
    EXPECT_TRUE(pos == end);
}

TEST(GeneratorTest, forwarded_exception) {
    auto gen = a_then_b(make_failed_numbers(1, 10, 5), make_numbers(10, 20));
    auto pos = std::ranges::begin(gen);
    auto end = std::ranges::end(gen);
    EXPECT_EQ(*pos, 1);
    EXPECT_EQ(*++pos, 2);
    EXPECT_EQ(*++pos, 3);
    EXPECT_EQ(*++pos, 4);
    EXPECT_FALSE(pos == end);
    EXPECT_THROW(++pos, vespalib::RequireFailedException);
    EXPECT_TRUE(pos == end);
}

TEST(GeneratorTest, exception_captured_by_parent_generator) {
    int expect = 1;
    for (int x: a_then_b(make_safe(make_failed_numbers(1, 10, 5)), make_numbers(5, 10))) {
        EXPECT_EQ(x, expect);
        ++expect;
    }
    EXPECT_EQ(expect, 10);
}

TEST(GeneratorTest, moving_iterator_with_recursive_generator) {
    auto gen = a_then_b(make_numbers(1, 3, 5), make_numbers(5, 7, 9));
    auto pos = std::ranges::begin(gen);
    auto end = std::ranges::end(gen);
    EXPECT_EQ(*pos, 1);
    EXPECT_EQ(*++pos, 2);
    auto pos2 = std::move(pos);
    EXPECT_EQ(*++pos2, 3);
    EXPECT_EQ(*++pos2, 4);
    auto pos3 = std::move(pos2);
    EXPECT_EQ(*++pos3, 5);
    EXPECT_EQ(*++pos3, 6);
    auto pos4 = std::move(pos3);
    EXPECT_EQ(*++pos4, 7);
    EXPECT_EQ(*++pos4, 8);
    auto pos5 = std::move(pos4);
    EXPECT_FALSE(pos5 == end);
    ++pos5;
    EXPECT_TRUE(pos5 == end);
}

GTEST_MAIN_RUN_ALL_TESTS()
