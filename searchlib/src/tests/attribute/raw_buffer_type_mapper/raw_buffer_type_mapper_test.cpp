// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/raw_buffer_type_mapper.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::attribute::RawBufferTypeMapper;

constexpr double default_grow_factor = 1.03;

class RawBufferTypeMapperTest : public testing::Test
{
protected:
    RawBufferTypeMapper _mapper;
    RawBufferTypeMapperTest();
    ~RawBufferTypeMapperTest() override;
    std::vector<size_t> get_array_sizes(uint32_t num_array_sizes);
    std::vector<size_t> get_large_array_sizes(uint32_t num_large_arrays);
    void select_type_ids(std::vector<size_t> array_sizes);
    void setup_mapper(uint32_t max_small_buffer_type_id, double grow_factor);
    static uint32_t calc_max_small_array_type_id(double grow_factor);
};

RawBufferTypeMapperTest::RawBufferTypeMapperTest()
    : testing::Test(),
      _mapper(5, default_grow_factor)
{
}

RawBufferTypeMapperTest::~RawBufferTypeMapperTest() = default;

void
RawBufferTypeMapperTest::setup_mapper(uint32_t max_small_buffer_type_id, double grow_factor)
{
    _mapper = RawBufferTypeMapper(max_small_buffer_type_id, grow_factor);
}

std::vector<size_t>
RawBufferTypeMapperTest::get_array_sizes(uint32_t num_array_sizes)
{
    std::vector<size_t> array_sizes;
    for (uint32_t type_id = 1; type_id <= num_array_sizes; ++type_id) {
        array_sizes.emplace_back(_mapper.get_array_size(type_id));
    }
    return array_sizes;
}

std::vector<size_t>
RawBufferTypeMapperTest::get_large_array_sizes(uint32_t num_large_array_sizes)
{
    setup_mapper(num_large_array_sizes * 100, default_grow_factor);
    std::vector<size_t> result;
    for (uint32_t i = 0; i < num_large_array_sizes; ++i) {
        uint32_t type_id = (i + 1) * 100;
        auto array_size = _mapper.get_array_size(type_id);
        result.emplace_back(array_size);
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size));
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size - 1));
        if (i + 1 == num_large_array_sizes) {
            EXPECT_EQ(0u, _mapper.get_type_id(array_size + 1));
        } else {
            EXPECT_EQ(type_id + 1, _mapper.get_type_id(array_size + 1));
        }
    }
    return result;
}

void
RawBufferTypeMapperTest::select_type_ids(std::vector<size_t> array_sizes)
{
    uint32_t type_id = 0;
    for (auto array_size : array_sizes) {
        ++type_id;
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size));
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size - 1));
        if (array_size == array_sizes.back()) {
            // Fallback to indirect storage, using type id 0
            EXPECT_EQ(0u, _mapper.get_type_id(array_size + 1));
        } else {
            EXPECT_EQ(type_id + 1, _mapper.get_type_id(array_size + 1));
        }
    }
}

uint32_t
RawBufferTypeMapperTest::calc_max_small_array_type_id(double grow_factor)
{
    RawBufferTypeMapper mapper(1000, grow_factor);
    return mapper.get_max_small_array_type_id(1000);
}

TEST_F(RawBufferTypeMapperTest, array_sizes_are_calculated)
{
    EXPECT_EQ((std::vector<size_t>{8, 12, 16, 20, 24}), get_array_sizes(5));
}

TEST_F(RawBufferTypeMapperTest, type_ids_are_selected)
{
    select_type_ids({8, 12, 16, 20, 24});
}

TEST_F(RawBufferTypeMapperTest, large_arrays_grows_exponentially)
{
    EXPECT_EQ((std::vector<size_t>{1148, 22796, 438572, 8429384}), get_large_array_sizes(4));
}

TEST_F(RawBufferTypeMapperTest, avoid_array_size_overflow)
{
    EXPECT_EQ(29, calc_max_small_array_type_id(2.0));
    EXPECT_EQ(379, calc_max_small_array_type_id(1.05));
    EXPECT_EQ(468, calc_max_small_array_type_id(1.04));
    EXPECT_EQ(610, calc_max_small_array_type_id(1.03));
    EXPECT_EQ(892, calc_max_small_array_type_id(1.02));
}

GTEST_MAIN_RUN_ALL_TESTS()
