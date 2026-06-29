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
    [[nodiscard]] size_t calc_reserved_memory(size_t                               concurrent,
                                              std::vector<std::pair<size_t, bool>> reserved_memory_and_priority_vec);
};

ReservedMemoryCalculatorTest::ReservedMemoryCalculatorTest()
    : testing::Test(),
      _type(IFlushTarget::Type::OTHER),
      _component(IFlushTarget::Component::OTHER),
      _each_max_memory(1000),
      _global_max_memory(4000) {
}

ReservedMemoryCalculatorTest::~ReservedMemoryCalculatorTest() = default;

size_t ReservedMemoryCalculatorTest::calc_reserved_memory(
    size_t concurrent, std::vector<std::pair<size_t, bool>> reserved_memory_and_priority_vec) {
    ReservedMemoryCalculator calc(concurrent, _each_max_memory, _global_max_memory);
    for (auto reserved_memory_and_priority : reserved_memory_and_priority_vec) {
        calc.track_reserved_memory_for_flush(reserved_memory_and_priority.first, _type, _component,
                                             reserved_memory_and_priority.second);
    }
    return calc.reserved_memory_for_flush() + calc.reserved_memory_for_memory_indexes();
}

TEST_F(ReservedMemoryCalculatorTest, calc_reserved_memory_for_flush) {
    EXPECT_EQ(0, calc_reserved_memory(1, {}));
    EXPECT_EQ(0, calc_reserved_memory(1, {{10, false}, {20, false}, {30, false}}));
    EXPECT_EQ(10, calc_reserved_memory(1, {{10, true}, {20, false}, {30, false}}));
    EXPECT_EQ(30, calc_reserved_memory(1, {{10, true}, {20, true}, {30, true}}));
    EXPECT_EQ(30, calc_reserved_memory(2, {{10, false}, {20, false}, {30, false}}));
    EXPECT_EQ(40, calc_reserved_memory(2, {{10, true}, {20, false}, {30, false}}));
    EXPECT_EQ(50, calc_reserved_memory(2, {{10, true}, {20, true}, {30, true}}));
    EXPECT_EQ(50, calc_reserved_memory(3, {{10, false}, {20, false}, {30, false}}));
    EXPECT_EQ(60, calc_reserved_memory(3, {{10, true}, {20, false}, {30, false}}));
    EXPECT_EQ(60, calc_reserved_memory(3, {{10, false}, {20, true}, {30, false}}));
}

TEST_F(ReservedMemoryCalculatorTest, calc_reserved_memory_for_memory_index) {
    _type = IFlushTarget::Type::FLUSH;
    _component = IFlushTarget::Component::INDEX;
    EXPECT_EQ(0, calc_reserved_memory(1, {}));
    // 2 memory indexes, _each_max_memory reserved for each memory index
    EXPECT_EQ(2000, calc_reserved_memory(1, {{0, true}, {0, true}}));
    EXPECT_EQ(2020, calc_reserved_memory(1, {{10, true}, {20, true}}));
    // 6 memory indexes, capped by _global_max_memory
    EXPECT_EQ(4000, calc_reserved_memory(6, {{0, true}, {0, true}, {0, true}, {0, true}, {0, true}, {0, true}}));
}
