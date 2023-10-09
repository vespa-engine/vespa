// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/tensor_buffer_type_mapper.h>
#include <vespa/searchlib/tensor/tensor_buffer_operations.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/datastore/array_store_config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <limits>

using search::tensor::TensorBufferOperations;
using search::tensor::TensorBufferTypeMapper;
using vespalib::datastore::ArrayStoreConfig;
using vespalib::eval::ValueType;

const vespalib::string tensor_type_sparse_spec("tensor(x{})");
const vespalib::string tensor_type_2d_spec("tensor(x{},y{})");
const vespalib::string tensor_type_2d_mixed_spec("tensor(x{},y[2])");
const vespalib::string float_tensor_type_spec("tensor<float>(y{})");
const vespalib::string tensor_type_dense_spec("tensor(x[2])");

namespace {

constexpr double default_grow_factor = 1.03;
constexpr size_t default_max_buffer_size = ArrayStoreConfig::default_max_buffer_size;
constexpr size_t max_max_buffer_size = std::numeric_limits<uint32_t>::max();

}

struct TestParam
{
    vespalib::string    _name;
    std::vector<size_t> _array_sizes;
    std::vector<size_t> _large_array_sizes;
    std::vector<uint32_t> _type_id_caps;
    vespalib::string    _tensor_type_spec;
    TestParam(vespalib::string name, std::vector<size_t> array_sizes, std::vector<size_t> large_array_sizes, std::vector<uint32_t> type_id_caps, const vespalib::string& tensor_type_spec)
        : _name(std::move(name)),
          _array_sizes(std::move(array_sizes)),
          _large_array_sizes(std::move(large_array_sizes)),
          _type_id_caps(type_id_caps),
          _tensor_type_spec(tensor_type_spec)
    {
    }
    TestParam(const TestParam&);
    ~TestParam();
};

TestParam::TestParam(const TestParam&) = default;

TestParam::~TestParam() = default;

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param._name;
    return os;
}

class TensorBufferTypeMapperTest : public testing::TestWithParam<TestParam>
{
protected:
    ValueType         _tensor_type;
    TensorBufferOperations _ops;
    TensorBufferTypeMapper _mapper;
    TensorBufferTypeMapperTest();
    ~TensorBufferTypeMapperTest() override;
    std::vector<size_t> get_array_sizes();
    std::vector<size_t> get_large_array_sizes();
    void select_type_ids();
};

TensorBufferTypeMapperTest::TensorBufferTypeMapperTest()
    : testing::TestWithParam<TestParam>(),
      _tensor_type(ValueType::from_spec(GetParam()._tensor_type_spec)),
      _ops(_tensor_type),
      _mapper(GetParam()._array_sizes.size(), default_grow_factor, default_max_buffer_size, &_ops)
{
}

TensorBufferTypeMapperTest::~TensorBufferTypeMapperTest() = default;

std::vector<size_t>
TensorBufferTypeMapperTest::get_array_sizes()
{
    uint32_t max_small_subspaces_type_id = GetParam()._array_sizes.size();
    std::vector<size_t> array_sizes;
    for (uint32_t type_id = 1; type_id <= max_small_subspaces_type_id; ++type_id) {
        auto num_subspaces = _tensor_type.is_dense() ? 1 : (type_id - 1);
        array_sizes.emplace_back(_mapper.get_array_size(type_id));
        EXPECT_EQ(_ops.get_buffer_size(num_subspaces), array_sizes.back());
    }
    return array_sizes;
}

std::vector<size_t>
TensorBufferTypeMapperTest::get_large_array_sizes()
{
    auto& large_array_sizes = GetParam()._large_array_sizes;
    uint32_t max_large = large_array_sizes.size();
    TensorBufferTypeMapper mapper(max_large * 100, default_grow_factor, default_max_buffer_size, &_ops);
    std::vector<size_t> result;
    for (uint32_t i = 0; i < max_large; ++i) {
        uint32_t type_id = (i + 1) * 100;
        if (type_id > mapper.get_max_type_id(max_large * 100)) {
            break;
        }
        auto array_size = mapper.get_array_size(type_id);
        result.emplace_back(array_size);
        EXPECT_EQ(type_id, mapper.get_type_id(array_size));
        EXPECT_EQ(type_id, mapper.get_type_id(array_size - 1));
        if (array_size == large_array_sizes.back()) {
            EXPECT_EQ(0u, mapper.get_type_id(array_size + 1));
        } else {
            EXPECT_EQ(type_id + 1, mapper.get_type_id(array_size + 1));
        }
    }
    return result;
}

void
TensorBufferTypeMapperTest::select_type_ids()
{
    auto& array_sizes = GetParam()._array_sizes;
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

/*
 * For "dense" case, array size for type id 1 is irrelevant, since
 * type ids 0 and 1 are not used when storing dense tensors in
 * TensorBufferStore.
 */

INSTANTIATE_TEST_SUITE_P(TensorBufferTypeMapperMultiTest,
                         TensorBufferTypeMapperTest,
                         testing::Values(TestParam("1d", {8, 16, 32, 40, 64}, {2768, 49712, 950768, 18268976, 351101184}, {27, 30, 514, 584}, tensor_type_sparse_spec),
                                         TestParam("1dfloat", {4, 12, 20, 28, 36}, {2688, 48896, 937248, 18009808, 346121248}, {27, 30, 514, 585}, float_tensor_type_spec),
                                         TestParam("2d", {8, 24, 40, 56, 80}, {2416, 41392, 790112, 15179616, 291726288}, {26, 29, 520, 590}, tensor_type_2d_spec),
                                         TestParam("2dmixed", {8, 24, 48, 64, 96}, {3008, 51728, 987632, 18974512, 364657856}, {26, 29, 513, 583}, tensor_type_2d_mixed_spec),
                                         TestParam("dense", {24}, {}, {1, 1, 1, 1}, tensor_type_dense_spec)),
                         testing::PrintToStringParamName());

TEST_P(TensorBufferTypeMapperTest, array_sizes_are_calculated)
{
    EXPECT_EQ(GetParam()._array_sizes, get_array_sizes());
}

TEST_P(TensorBufferTypeMapperTest, type_ids_are_selected)
{
    select_type_ids();
}

TEST_P(TensorBufferTypeMapperTest, large_arrays_grows_exponentially)
{
    EXPECT_EQ(GetParam()._large_array_sizes, get_large_array_sizes());
}

TEST_P(TensorBufferTypeMapperTest, type_id_is_capped)
{
    auto& exp_type_id_caps = GetParam()._type_id_caps;
    std::vector<uint32_t> act_type_id_caps;
    std::vector<double> grow_factors = { 2.0, default_grow_factor };
    std::vector<size_t> max_buffer_sizes = { default_max_buffer_size, max_max_buffer_size };
    for (auto& grow_factor : grow_factors) {
        for (auto max_buffer_size : max_buffer_sizes) {
            TensorBufferTypeMapper mapper(1000, grow_factor, max_buffer_size, &_ops);
            act_type_id_caps.emplace_back(mapper.get_max_type_id(1000));
        }
    }
    EXPECT_EQ(exp_type_id_caps, act_type_id_caps);
}

GTEST_MAIN_RUN_ALL_TESTS()
