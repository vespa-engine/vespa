// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/tensor_buffer_store.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::tensor::TensorBufferStore;
using vespalib::datastore::EntryRef;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

const vespalib::string tensor_type_spec("tensor(x{})");

class TensorBufferStoreTest : public testing::Test
{
protected:
    ValueType         _tensor_type;
    TensorBufferStore _store;
    TensorBufferStoreTest();
    ~TensorBufferStoreTest() override;
    EntryRef store_tensor(const Value& tensor);
    EntryRef store_tensor(const TensorSpec& spec);
    std::unique_ptr<Value> load_tensor(EntryRef ref);
    TensorSpec load_tensor_spec(EntryRef ref);
    vespalib::nbostream encode_stored_tensor(EntryRef ref);
    void assert_store_load(const TensorSpec& tensor_spec);
    void assert_store_load_many(const TensorSpec& tensor_spec);
    void assert_store_move_on_compact_load(const TensorSpec& tensor_spec);
    void assert_store_encode_store_encoded_load(const TensorSpec& tensor_spec);
};

TensorBufferStoreTest::TensorBufferStoreTest()
    : testing::Test(),
      _tensor_type(ValueType::from_spec(tensor_type_spec)),
      _store(_tensor_type, {}, 4)
{
}

TensorBufferStoreTest::~TensorBufferStoreTest() = default;

EntryRef
TensorBufferStoreTest::store_tensor(const Value& tensor)
{
    EXPECT_EQ(_tensor_type, tensor.type());
    return _store.store_tensor(tensor);
}

EntryRef
TensorBufferStoreTest::store_tensor(const TensorSpec& spec)
{
    auto tensor = value_from_spec(spec, FastValueBuilderFactory::get());
    return store_tensor(*tensor);
}

std::unique_ptr<Value>
TensorBufferStoreTest::load_tensor(EntryRef ref)
{
    return _store.get_tensor(ref);
}

vespalib::nbostream
TensorBufferStoreTest::encode_stored_tensor(EntryRef ref)
{
    vespalib::nbostream out;
    _store.encode_stored_tensor(ref, out);
    return out;
}

TensorSpec
TensorBufferStoreTest::load_tensor_spec(EntryRef ref)
{
    auto loaded = load_tensor(ref);
    return TensorSpec::from_value(*loaded);
}

void
TensorBufferStoreTest::assert_store_load(const TensorSpec& tensor_spec)
{
    auto ref = store_tensor(tensor_spec);
    auto loaded_spec = load_tensor_spec(ref);
    _store.holdTensor(ref);
    EXPECT_EQ(tensor_spec, loaded_spec);
}

void
TensorBufferStoreTest::assert_store_load_many(const TensorSpec& tensor_spec)
{
    constexpr uint32_t cnt = 2000;
    std::vector<EntryRef> refs;
    for (uint32_t i = 0; i < cnt; ++i) {
        refs.emplace_back(store_tensor(tensor_spec));
    }
    for (auto ref : refs) {
        auto loaded_spec = load_tensor_spec(ref);
        _store.holdTensor(ref);
        EXPECT_EQ(tensor_spec, loaded_spec);
    }
}

void
TensorBufferStoreTest::assert_store_move_on_compact_load(const TensorSpec& tensor_spec)
{
    auto ref = store_tensor(tensor_spec);
    auto ref2 = _store.move_on_compact(ref);
    EXPECT_NE(ref, ref2);
    auto loaded_spec = load_tensor_spec(ref2);
    _store.holdTensor(ref2);
    EXPECT_EQ(tensor_spec, loaded_spec);
}

void
TensorBufferStoreTest::assert_store_encode_store_encoded_load(const TensorSpec& tensor_spec)
{
    auto ref = store_tensor(tensor_spec);
    auto encoded = encode_stored_tensor(ref);
    _store.holdTensor(ref);
    auto ref2 = _store.store_encoded_tensor(encoded);
    EXPECT_NE(ref, ref2);
    auto loaded_spec = load_tensor_spec(ref2);
    _store.holdTensor(ref2);
    EXPECT_EQ(tensor_spec, loaded_spec);
}

std::vector<TensorSpec> tensor_specs = {
    TensorSpec(tensor_type_spec),
    TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5),
    TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5).add({{"x", "b"}}, 5.5),
    TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5).add({{"x", "b"}}, 5.5).add({{"x", "c"}}, 6.5),
    TensorSpec(tensor_type_spec).add({{"x", "a"}}, 4.5).add({{"x", "b"}}, 5.5).add({{"x", "c"}}, 6.5).add({{"x", "d"}}, 7.5)
};

TEST_F(TensorBufferStoreTest, tensor_can_be_stored_and_loaded)
{
    for (auto& tensor_spec : tensor_specs) {
        assert_store_load(tensor_spec);
    }
}

TEST_F(TensorBufferStoreTest, tensor_can_be_stored_and_loaded_many_times)
{
    for (auto& tensor_spec : tensor_specs) {
        assert_store_load_many(tensor_spec);
    }
}

TEST_F(TensorBufferStoreTest, stored_tensor_can_be_moved_on_compact)
{
    for (auto& tensor_spec : tensor_specs) {
        assert_store_move_on_compact_load(tensor_spec);
    }
}

TEST_F(TensorBufferStoreTest, stored_tensor_can_be_encoded_and_stored_as_encoded_and_loaded)
{
    for (auto& tensor_spec : tensor_specs) {
        assert_store_encode_store_encoded_load(tensor_spec);
    }
}

TEST_F(TensorBufferStoreTest, get_vectors)
{
    auto ref = store_tensor(tensor_specs.back());
    std::vector<double> values;
    auto vectors = _store.get_vectors(ref);
    EXPECT_EQ(4, vectors.subspaces());
    for (uint32_t subspace = 0; subspace < 4; ++subspace) {
        auto cells = vectors.cells(subspace).typify<double>();
        EXPECT_EQ(1, cells.size());
        values.emplace_back(cells[0]);
    }
    EXPECT_EQ((std::vector<double>{4.5, 5.5, 6.5, 7.5}), values);
    EXPECT_EQ(0, _store.get_vectors(EntryRef()).subspaces());
}

GTEST_MAIN_RUN_ALL_TESTS()
