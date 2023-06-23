// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::datastore::ArrayStoreDynamicTypeMapper;

constexpr double default_grow_factor = 1.03;

template <typename ElemT>
class TestBase : public testing::Test
{
protected:
    ArrayStoreDynamicTypeMapper<ElemT> _mapper;
    TestBase();
    ~TestBase() override;
    std::vector<size_t> get_array_sizes(uint32_t num_array_sizes);
    std::vector<size_t> get_entry_sizes(uint32_t num_entry_sizes);
    std::vector<size_t> get_large_array_sizes(uint32_t num_large_arrays);
    void select_type_ids(std::vector<size_t> array_sizes);
    void setup_mapper(uint32_t max_buffer_type_id, double grow_factor);
    static uint32_t calc_max_buffer_type_id(double grow_factor);
};

template <typename ElemT>
TestBase<ElemT>::TestBase()
    : testing::Test(),
      _mapper(5, default_grow_factor)
{
}

template <typename ElemT>
TestBase<ElemT>::~TestBase() = default;

template <typename ElemT>
void
TestBase<ElemT>::setup_mapper(uint32_t max_buffer_type_id, double grow_factor)
{
    _mapper = ArrayStoreDynamicTypeMapper<ElemT>(max_buffer_type_id, grow_factor);
}

template <typename ElemT>
std::vector<size_t>
TestBase<ElemT>::get_array_sizes(uint32_t num_array_sizes)
{
    std::vector<size_t> array_sizes;
    for (uint32_t type_id = 1; type_id <= num_array_sizes; ++type_id) {
        array_sizes.emplace_back(_mapper.get_array_size(type_id));
    }
    return array_sizes;
}

template <typename ElemT>
std::vector<size_t>
TestBase<ElemT>::get_entry_sizes(uint32_t num_entry_sizes)
{
    std::vector<size_t> entry_sizes;
    for (uint32_t type_id = 1; type_id <= num_entry_sizes; ++type_id) {
        entry_sizes.emplace_back(_mapper.get_entry_size(type_id));
    }
    return entry_sizes;
}

template <typename ElemT>
std::vector<size_t>
TestBase<ElemT>::get_large_array_sizes(uint32_t num_large_array_sizes)
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

template <typename ElemT>
void
TestBase<ElemT>::select_type_ids(std::vector<size_t> array_sizes)
{
    uint32_t type_id = 0;
    std::optional<size_t> prev_array_size;
    for (auto array_size : array_sizes) {
        ++type_id;
        EXPECT_EQ(type_id, _mapper.get_type_id(array_size));
        if (!prev_array_size.has_value() || prev_array_size.value() < array_size - 1) {
            EXPECT_EQ(type_id, _mapper.get_type_id(array_size - 1));
        } else {
            EXPECT_EQ(type_id - 1, _mapper.get_type_id(array_size - 1));
        }
        prev_array_size = array_size;
        if (array_size == array_sizes.back()) {
            // Fallback to indirect storage, using type id 0
            EXPECT_EQ(0u, _mapper.get_type_id(array_size + 1));
        } else {
            EXPECT_EQ(type_id + 1, _mapper.get_type_id(array_size + 1));
        }
    }
}

template <typename ElemT>
uint32_t
TestBase<ElemT>::calc_max_buffer_type_id(double grow_factor)
{
    ArrayStoreDynamicTypeMapper<ElemT> mapper(1000, grow_factor);
    return mapper.get_max_type_id(1000);
}

using ArrayStoreDynamicTypeMapperCharTest = TestBase<char>;

TEST_F(ArrayStoreDynamicTypeMapperCharTest, array_sizes_are_calculated)
{
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5}), get_array_sizes(5));
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5}), get_entry_sizes(5));
    setup_mapper(10, 1.4);
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5, 8, 12, 16, 24, 36}), get_array_sizes(10));
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5, 12, 16, 20, 28, 40}), get_entry_sizes(10));
}

TEST_F(ArrayStoreDynamicTypeMapperCharTest, type_ids_are_selected)
{
    select_type_ids({1, 2, 3, 4, 5});
    setup_mapper(10, 1.4);
    select_type_ids({1, 2, 3, 4, 5, 8, 12, 16, 24, 36});
}

TEST_F(ArrayStoreDynamicTypeMapperCharTest, large_arrays_grows_exponentially)
{
    EXPECT_EQ((std::vector<size_t>{232, 13372, 276860, 5338108}), get_large_array_sizes(4));
}

TEST_F(ArrayStoreDynamicTypeMapperCharTest, avoid_entry_size_overflow)
{
    EXPECT_EQ(32, calc_max_buffer_type_id(2.0));
    EXPECT_EQ(395, calc_max_buffer_type_id(1.05));
    EXPECT_EQ(485, calc_max_buffer_type_id(1.04));
    EXPECT_EQ(626, calc_max_buffer_type_id(1.03));
    EXPECT_EQ(900, calc_max_buffer_type_id(1.02));
}

using ArrayStoreDynamicTypeMapperInt32Test = TestBase<int32_t>;

TEST_F(ArrayStoreDynamicTypeMapperInt32Test, array_sizes_are_calculated)
{
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5}), get_array_sizes(5));
    EXPECT_EQ((std::vector<size_t>{4, 8, 12, 16, 20}), get_entry_sizes(5));
    setup_mapper(10, 1.4);
    EXPECT_EQ((std::vector<size_t>{1, 2, 3, 4, 5, 7, 9, 12, 16, 22}), get_array_sizes(10));
    EXPECT_EQ((std::vector<size_t>{4, 8, 12, 16, 20, 32, 40, 52, 68, 92}), get_entry_sizes(10));
}

TEST_F(ArrayStoreDynamicTypeMapperInt32Test, avoid_entry_size_overflow)
{
    EXPECT_EQ(30, calc_max_buffer_type_id(2.0));
    EXPECT_EQ(379, calc_max_buffer_type_id(1.05));
    EXPECT_EQ(462, calc_max_buffer_type_id(1.04));
    EXPECT_EQ(596, calc_max_buffer_type_id(1.03));
    EXPECT_EQ(849, calc_max_buffer_type_id(1.02));
}

GTEST_MAIN_RUN_ALL_TESTS()
