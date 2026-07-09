// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/reserved_memory_for_attribute_load_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::ReservedMemoryForAttributeLoadCalculator;
using proton::initializer::LoadMemoryUsage;

class ReservedMemoryForAttributeLoadCalculatorTest : public ::testing::Test {
protected:
    size_t                                   _initialize_threads;
    ReservedMemoryForAttributeLoadCalculator _calc;
    ReservedMemoryForAttributeLoadCalculatorTest();
    ~ReservedMemoryForAttributeLoadCalculatorTest() override;
    void set_initialize_threads(size_t value);
    size_t calc_reserved(std::vector<LoadMemoryUsage> ready_usage, std::vector<LoadMemoryUsage> notready_usage);
    size_t calc_reserved_2dbs(std::vector<LoadMemoryUsage> ready1_usage, std::vector<LoadMemoryUsage> notready1_usage,
                              std::vector<LoadMemoryUsage> ready2_usage,
                              std::vector<LoadMemoryUsage> notready2_usage);
};

ReservedMemoryForAttributeLoadCalculatorTest::ReservedMemoryForAttributeLoadCalculatorTest()
    : ::testing::Test(), _initialize_threads(0), _calc(_initialize_threads) {
}

ReservedMemoryForAttributeLoadCalculatorTest::~ReservedMemoryForAttributeLoadCalculatorTest() = default;

void ReservedMemoryForAttributeLoadCalculatorTest::set_initialize_threads(size_t value) {
    _initialize_threads = value;
    _calc = ReservedMemoryForAttributeLoadCalculator(_initialize_threads);
}

size_t ReservedMemoryForAttributeLoadCalculatorTest::calc_reserved(std::vector<LoadMemoryUsage> ready_usages,
                                                                   std::vector<LoadMemoryUsage> notready_usages) {
    _calc.reset();
    _calc.add(ready_usages, notready_usages);
    return _calc.calc();
}

size_t ReservedMemoryForAttributeLoadCalculatorTest::calc_reserved_2dbs(
    std::vector<LoadMemoryUsage> ready1_usages, std::vector<LoadMemoryUsage> notready1_usages,
    std::vector<LoadMemoryUsage> ready2_usages, std::vector<LoadMemoryUsage> notready2_usages) {
    _calc.reset();
    _calc.add(ready1_usages, notready1_usages);
    _calc.add(ready2_usages, notready2_usages);
    return _calc.calc();
}

TEST_F(ReservedMemoryForAttributeLoadCalculatorTest, one_initializer_thread_per_document_type) {
    EXPECT_EQ(0, calc_reserved({}, {}));
    EXPECT_EQ(10, calc_reserved({{10, 0}}, {{5, 0}}));
    EXPECT_EQ(7, calc_reserved({{6, 3}, {10, 3}}, {{5, 1}}));
    EXPECT_EQ(14, calc_reserved_2dbs({{6, 3}, {10, 3}}, {{5, 1}}, {{7, 2}}, {{3, 1}}));
}

TEST_F(ReservedMemoryForAttributeLoadCalculatorTest, one_shared_initializer_thread) {
    set_initialize_threads(1);
    EXPECT_EQ(0, calc_reserved({}, {}));
    EXPECT_EQ(10, calc_reserved({{10, 0}}, {{5, 0}}));
    EXPECT_EQ(7, calc_reserved({{6, 3}, {10, 3}}, {{5, 1}}));
    EXPECT_EQ(7, calc_reserved_2dbs({{6, 3}, {10, 3}}, {{5, 1}}, {{7, 2}}, {{3, 1}}));
}

TEST_F(ReservedMemoryForAttributeLoadCalculatorTest, two_shared_initializer_threads) {
    set_initialize_threads(2);
    EXPECT_EQ(0, calc_reserved({}, {}));
    EXPECT_EQ(15, calc_reserved({{10, 0}}, {{5, 0}}));
    EXPECT_EQ(16, calc_reserved({{6, 3}, {10, 3}}, {{5, 1}}));
    EXPECT_EQ(17, calc_reserved_2dbs({{6, 3}, {10, 3}}, {{5, 1}}, {{7, 2}}, {{3, 1}}));
}

TEST_F(ReservedMemoryForAttributeLoadCalculatorTest, three_shared_initializer_threads) {
    set_initialize_threads(3);
    EXPECT_EQ(0, calc_reserved({}, {}));
    EXPECT_EQ(15, calc_reserved({{10, 0}}, {{5, 0}}));
    EXPECT_EQ(21, calc_reserved({{6, 3}, {10, 3}}, {{5, 1}}));
    EXPECT_EQ(23, calc_reserved_2dbs({{6, 3}, {10, 3}}, {{5, 1}}, {{7, 2}}, {{3, 1}}));
}
