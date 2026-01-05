// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/searchlib/queryeval/lazy_filter.h>

#include <vector>

using namespace search::queryeval;

class LazyFilterTest: public ::testing::Test {
protected:
    std::shared_ptr<GlobalFilter> _multiples_of_four;
    std::shared_ptr<GlobalFilter> _multiples_of_six;
    std::shared_ptr<GlobalFilter> _multiples_of_seven;
    std::shared_ptr<GlobalFilter> _multiples_of_eight;

    LazyFilterTest();
    ~LazyFilterTest() override;
    static std::vector<uint32_t> get_multiples_of(uint32_t number, uint32_t up_to);
};

LazyFilterTest::LazyFilterTest()
    : ::testing::Test()
{
    _multiples_of_four = GlobalFilter::create(get_multiples_of(4, 100), 100);
    _multiples_of_six = GlobalFilter::create(get_multiples_of(6, 150), 150);
    _multiples_of_seven = GlobalFilter::create(get_multiples_of(7, 200), 200);
    _multiples_of_eight = GlobalFilter::create(get_multiples_of(8, 200), 200);
}

LazyFilterTest::~LazyFilterTest() = default;

std::vector<uint32_t>
LazyFilterTest::get_multiples_of(uint32_t number, uint32_t up_to) {
    std::vector<uint32_t> multiples;
    for (uint32_t i = 1; number * i < up_to; i += 1) {
        multiples.push_back(number * i);
    }

    return multiples;
}

TEST_F(LazyFilterTest, fallback_filter_is_active) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_TRUE(and_filter->is_active());
}

TEST_F(LazyFilterTest, fallback_filter_size) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_EQ(100, and_filter->size());
}

TEST_F(LazyFilterTest, fallback_filter_count) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    // TODO: Should this be exact or an upper bound?
    EXPECT_EQ(24, and_filter->count());
}

TEST_F(LazyFilterTest, fallback_filter_check) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_TRUE(and_filter->check(16));
    EXPECT_FALSE(and_filter->check(12));
    EXPECT_FALSE(and_filter->check(10));
}

class LoggingGlobalFilter : public GlobalFilter {
private:
    const GlobalFilter & _global_filter;
    mutable uint32_t _number_of_checks;

public:
    LoggingGlobalFilter(const GlobalFilter & global_filter) noexcept
        : _global_filter(global_filter),
          _number_of_checks(0) {
    }
    bool is_active() const override { return _global_filter.is_active(); }
    uint32_t size() const override { return _global_filter.size(); }
    uint32_t count() const override { return _global_filter.count(); }
    bool check(uint32_t index) const override { ++_number_of_checks; return _global_filter.check(index); }

    uint32_t get_number_of_checks() const { return _number_of_checks; }
};

TEST_F(LazyFilterTest, fallback_filter_fallback_is_checked_only_when_necessary) {
    std::shared_ptr<LoggingGlobalFilter> logging_multiples_of_four = std::make_shared<LoggingGlobalFilter>(*_multiples_of_four);
    std::shared_ptr<LoggingGlobalFilter> logging_multiples_of_eight = std::make_shared<LoggingGlobalFilter>(*_multiples_of_eight);
    auto and_filter = FallbackFilter::create(*logging_multiples_of_four, *logging_multiples_of_eight);

    EXPECT_EQ(0, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(0, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_FALSE(and_filter->check(10));
    EXPECT_EQ(1, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(0, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_FALSE(and_filter->check(4));
    EXPECT_EQ(2, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(1, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_TRUE(and_filter->check(8));
    EXPECT_EQ(3, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(2, logging_multiples_of_eight->get_number_of_checks());
}

TEST_F(LazyFilterTest, and_filter_is_active) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_TRUE(and_filter->is_active());
}

TEST_F(LazyFilterTest, and_filter_size_is_min) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_EQ(100, and_filter->size());
}

TEST_F(LazyFilterTest, and_filter_count) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    // TODO: Should this be exact or an upper bound?
    //EXPECT_EQ(1, and_filter->count());
    EXPECT_EQ(24, and_filter->count());
}

TEST_F(LazyFilterTest, and_filter_check) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_FALSE(and_filter->check(24));
    EXPECT_TRUE(and_filter->check(84));
}

GTEST_MAIN_RUN_ALL_TESTS()
