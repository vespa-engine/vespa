// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/reserved_memory_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::flushengine::ReservedMemoryCalculator;
using searchcorespi::IFlushTarget;

class ReservedMemoryCalculatorTest : public testing::Test {
protected:
    IFlushTarget::Type      _type;
    IFlushTarget::Component _component;
    size_t                  _each_max_memory;
    size_t                  _global_max_memory;
    ReservedMemoryCalculatorTest();
    ~ReservedMemoryCalculatorTest() override;
    [[nodiscard]] size_t calc_reserved_memory(size_t concurrent, std::vector<size_t> transient_memory_sizes);
};

ReservedMemoryCalculatorTest::ReservedMemoryCalculatorTest()
    : testing::Test(),
      _type(IFlushTarget::Type::OTHER),
      _component(IFlushTarget::Component::OTHER),
      _each_max_memory(1000),
      _global_max_memory(4000) {
}

ReservedMemoryCalculatorTest::~ReservedMemoryCalculatorTest() = default;

size_t ReservedMemoryCalculatorTest::calc_reserved_memory(size_t              concurrent,
                                                          std::vector<size_t> transient_memory_sizes) {
    ReservedMemoryCalculator calc(concurrent, _each_max_memory, _global_max_memory);
    for (auto transient_memory : transient_memory_sizes) {
        calc.track_transient_memory_for_flush(transient_memory, _type, _component);
    }
    return calc.get_reserved_memory();
}

TEST_F(ReservedMemoryCalculatorTest, calc_reserved_memory_for_flush) {
    EXPECT_EQ(0, calc_reserved_memory(1, {}));
    EXPECT_EQ(30, calc_reserved_memory(1, {10, 20, 30}));
    EXPECT_EQ(50, calc_reserved_memory(2, {10, 20, 30}));
    EXPECT_EQ(60, calc_reserved_memory(3, {10, 20, 30}));
}

TEST_F(ReservedMemoryCalculatorTest, calc_reserved_memory_for_memory_index) {
    _type = IFlushTarget::Type::FLUSH;
    _component = IFlushTarget::Component::INDEX;
    EXPECT_EQ(0, calc_reserved_memory(1, {}));
    // 2 memory indexes, _each_max_memory reserved for each memory index
    EXPECT_EQ(2000, calc_reserved_memory(1, {0, 0}));
    EXPECT_EQ(2020, calc_reserved_memory(1, {10, 20}));
    // 6 memory indexes, capped by _global_max_memory
    EXPECT_EQ(4000, calc_reserved_memory(6, {0, 0, 0, 0, 0, 0}));
}
