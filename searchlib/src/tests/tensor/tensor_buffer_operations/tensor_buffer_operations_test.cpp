// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/tensor_buffer_operations.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::tensor::TensorBufferOperations;
using vespalib::eval::SimpleValue;
using vespalib::eval::StreamedValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::TypedCells;

const vespalib::string tensor_type_spec("tensor(x{})");
const vespalib::string tensor_type_2d_spec("tensor(x{},y{})");
const vespalib::string tensor_type_2d_mixed_spec("tensor(x{},y[2])");
const vespalib::string float_tensor_type_spec("tensor<float>(y{})");

struct TestParam
{
    vespalib::string    _name;
    std::vector<size_t> _array_sizes;
    TensorSpec          _tensor_spec;
    TestParam(vespalib::string name, std::vector<size_t> array_sizes, TensorSpec tensor_spec)
        : _name(std::move(name)),
          _array_sizes(std::move(array_sizes)),
          _tensor_spec(std::move(tensor_spec))
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

class TensorBufferOperationsTest : public testing::TestWithParam<TestParam>
{
protected:
    ValueType _tensor_type;
    TensorBufferOperations _ops;
    TensorBufferOperationsTest();
    ~TensorBufferOperationsTest() override;
    std::vector<size_t> get_array_sizes(uint32_t max_subspaces);
    std::vector<char> store_tensor(const Value& tensor);
    std::vector<char> store_tensor(const TensorSpec& spec);
    std::unique_ptr<Value> load_tensor(vespalib::ConstArrayRef<char> buf);
    TensorSpec load_tensor_spec(vespalib::ConstArrayRef<char> buf);
    vespalib::nbostream encode_stored_tensor(vespalib::ConstArrayRef<char> buf);
    void assert_store_load(const TensorSpec& tensor_spec);
    void assert_store_copy_load(const TensorSpec& tensor_spec);
    void assert_store_encode_decode(const TensorSpec& tensor_spec);
};

TensorBufferOperationsTest::TensorBufferOperationsTest()
    : testing::TestWithParam<TestParam>(),
      _tensor_type(ValueType::from_spec(GetParam()._tensor_spec.type())),
      _ops(_tensor_type)
{
}

TensorBufferOperationsTest::~TensorBufferOperationsTest() = default;

std::vector<size_t>
TensorBufferOperationsTest::get_array_sizes(uint32_t max_subspaces)
{
    std::vector<size_t> array_sizes;
    for (uint32_t num_subspaces = 0; num_subspaces < max_subspaces; ++num_subspaces) {
        array_sizes.emplace_back(_ops.get_buffer_size(num_subspaces));
    }
    return array_sizes;
}

std::vector<char>
TensorBufferOperationsTest::store_tensor(const Value& tensor)
{
    EXPECT_EQ(_tensor_type, tensor.type());
    uint32_t num_subspaces = tensor.index().size();
    auto array_size = _ops.get_buffer_size(num_subspaces);
    std::vector<char> buf;
    buf.resize(array_size);
    _ops.store_tensor(buf, tensor);
    return buf;
}

std::vector<char>
TensorBufferOperationsTest::store_tensor(const TensorSpec& spec)
{
    auto tensor = SimpleValue::from_spec(spec);
    return store_tensor(*tensor);
}

std::unique_ptr<Value>
TensorBufferOperationsTest::load_tensor(vespalib::ConstArrayRef<char> buf)
{
    return _ops.make_fast_view(buf, _tensor_type);
}

vespalib::nbostream
TensorBufferOperationsTest::encode_stored_tensor(vespalib::ConstArrayRef<char> buf)
{
    vespalib::nbostream out;
    _ops.encode_stored_tensor(buf, _tensor_type, out);
    return out;
}

TensorSpec
TensorBufferOperationsTest::load_tensor_spec(vespalib::ConstArrayRef<char> buf)
{
    auto loaded = load_tensor(buf);
    return TensorSpec::from_value(*loaded);
}

void
TensorBufferOperationsTest::assert_store_load(const TensorSpec& tensor_spec)
{
    auto buf = store_tensor(tensor_spec);
    auto loaded_spec = load_tensor_spec(buf);
    _ops.reclaim_labels(buf);
    EXPECT_EQ(tensor_spec, loaded_spec);
}

void
TensorBufferOperationsTest::assert_store_copy_load(const TensorSpec& tensor_spec)
{
    auto buf = store_tensor(tensor_spec);
    auto buf2 = buf;
    _ops.copied_labels(buf);
    EXPECT_NE(buf, buf2);
    _ops.reclaim_labels(buf);
    buf.clear();
    auto loaded_spec = load_tensor_spec(buf2);
    _ops.reclaim_labels(buf2);
    EXPECT_EQ(tensor_spec, loaded_spec);
}

void
TensorBufferOperationsTest::assert_store_encode_decode(const TensorSpec& tensor_spec)
{
    auto buf = store_tensor(tensor_spec);
    auto encoded = encode_stored_tensor(buf);
    _ops.reclaim_labels(buf);
    const auto& factory = StreamedValueBuilderFactory::get();
    auto decoded = vespalib::eval::decode_value(encoded, factory);
    auto decoded_spec = TensorSpec::from_value(*decoded);
    EXPECT_EQ(tensor_spec, decoded_spec);
}

INSTANTIATE_TEST_SUITE_P(TensorBufferOperationsMultiTest,
                         TensorBufferOperationsTest,
                         testing::Values(TestParam("1d", {8, 16, 32, 40, 64}, TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5)),
                                                     TestParam("1dmulti", {8, 16, 32, 40, 64}, TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5).add({{"x", "c"}}, 4.25)),
                                                     TestParam("1dfloat", {4, 12, 20, 28, 36}, TensorSpec(float_tensor_type_spec).add({{"y", "aa"}}, 4.25)),
                                                     TestParam("2d", {8, 24, 40, 56, 80}, TensorSpec(tensor_type_2d_spec).add({{"x", "a"},{"y", "aa"}}, 4.75)),
                                                     TestParam("2dmixed", {8, 24, 48, 64, 96}, TensorSpec(tensor_type_2d_mixed_spec).add({{"x", "a"},{"y", 0}}, 4.5).add({{"x", "a"},{"y", 1}}, 4.25))),
                                     testing::PrintToStringParamName());

TEST_P(TensorBufferOperationsTest, array_sizes_are_calculated)
{
    EXPECT_EQ(GetParam()._array_sizes, get_array_sizes(5));
}

TEST_P(TensorBufferOperationsTest, tensor_can_be_stored_and_loaded)
{
    assert_store_load(GetParam()._tensor_spec);
}

TEST_P(TensorBufferOperationsTest, tensor_buffer_can_be_copied)
{
    assert_store_copy_load(GetParam()._tensor_spec);
}

TEST_P(TensorBufferOperationsTest, tensor_buffer_can_be_encoded)
{
    assert_store_encode_decode(GetParam()._tensor_spec);
}

GTEST_MAIN_RUN_ALL_TESTS()
