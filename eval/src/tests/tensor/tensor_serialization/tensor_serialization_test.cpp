// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/eval/tensor/serialization/sparse_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <ostream>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/eval/value_codec.h>

using namespace vespalib::tensor;
using vespalib::eval::TensorSpec;
using vespalib::nbostream;
using ExpBuffer = std::vector<uint8_t>;

namespace std {

bool operator==(const std::vector<uint8_t> &exp, const nbostream &stream)
{
    return ((exp.size() == stream.size()) &&
            (memcmp(&exp[0], stream.peek(), exp.size()) == 0));
}

std::ostream &operator<<(std::ostream &out, const std::vector<uint8_t> &rhs)
{
    out << vespalib::HexDump(&rhs[0], rhs.size());
    return out;
}

}

//-----------------------------------------------------------------------------

template <typename T>
void verify_cells_only(const ExpBuffer &exp, const TensorSpec &spec) {
    nbostream input(&exp[0], exp.size());
    std::vector<T> cells;
    TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(input, cells);
    ASSERT_EQUAL(cells.size(), spec.cells().size());
    size_t i = 0;
    for (const auto &cell: spec.cells()) {
        EXPECT_EQUAL(cells[i++], cell.second.value);
    }
    ASSERT_EQUAL(i, cells.size());
}

TensorSpec verify_new_value_serialized(const ExpBuffer &exp, const TensorSpec &spec) {
    auto factory = vespalib::eval::SimpleValueBuilderFactory();
    auto new_value = vespalib::eval::value_from_spec(spec, factory);
    auto new_value_spec = vespalib::eval::spec_from_value(*new_value);
    nbostream actual;
    vespalib::eval::new_encode(*new_value, actual);
    ASSERT_EQUAL(exp, actual);
    auto new_decoded = vespalib::eval::new_decode(actual, factory);
    auto new_decoded_spec = vespalib::eval::spec_from_value(*new_decoded);
    EXPECT_EQUAL(0u, actual.size());
    EXPECT_EQUAL(new_value_spec, new_decoded_spec);
    if (new_value->type().is_dense()) {
        TEST_DO(verify_cells_only<float>(exp, new_value_spec));
        TEST_DO(verify_cells_only<double>(exp, new_value_spec));
    }
    return new_decoded_spec;
}

void verify_serialized(const ExpBuffer &exp, const TensorSpec &spec) {
    auto &engine = DefaultTensorEngine::ref();
    auto value = engine.from_spec(spec);
    auto value_spec = engine.to_spec(*value);
    nbostream actual;
    engine.encode(*value, actual);
    EXPECT_EQUAL(exp, actual);
    auto decoded = engine.decode(actual);
    auto decoded_spec = engine.to_spec(*decoded);
    EXPECT_EQUAL(0u, actual.size());
    EXPECT_EQUAL(value_spec, decoded_spec);
    if (value->type().is_dense()) {
        TEST_DO(verify_cells_only<float>(exp, value_spec));
        TEST_DO(verify_cells_only<double>(exp, value_spec));
    }
    auto new_value_spec = verify_new_value_serialized(exp, spec);
    EXPECT_EQUAL(value_spec, new_value_spec);
}

//-----------------------------------------------------------------------------

TEST("test tensor serialization for SparseTensor") {
    TEST_DO(verify_serialized({ 0x01, 0x01, 0x01, 0x78, 0x00 },
                              TensorSpec("tensor(x{})")));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x00 },
                              TensorSpec("tensor(x{},y{})")));
    TEST_DO(verify_serialized({ 0x01, 0x01, 0x01, 0x78, 0x01, 0x01, 0x31, 0x40,
                                0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
                              TensorSpec("tensor(x{})")
                              .add({{"x", "1"}}, 3)));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x00,
                                0x00, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00 },
                              TensorSpec("tensor(x{},y{})")
                              .add({{"x", ""}, {"y", ""}}, 3)));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x01,
                                0x31, 0x00, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00 },
                              TensorSpec("tensor(x{},y{})")
                              .add({{"x", "1"}, {"y", ""}}, 3)));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x00,
                                0x01, 0x33, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00 },
                              TensorSpec("tensor(x{},y{})")
                              .add({{"x", ""}, {"y", "3"}}, 3)));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x01,
                                0x32, 0x01, 0x34, 0x40, 0x08, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00 },
                              TensorSpec("tensor(x{},y{})")
                              .add({{"x", "2"}, {"y", "4"}}, 3)));
    TEST_DO(verify_serialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79,
                                0x01, 0x01, 0x31, 0x00, 0x40, 0x08,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
                              TensorSpec("tensor(x{},y{})")
                              .add({{"x", "1"}, {"y", ""}}, 3)));
}

TEST("test float cells from sparse tensor") {
    TEST_DO(verify_serialized({ 0x05, 0x01,
                                0x02, 0x01, 0x78, 0x01, 0x79,
                                0x01, 0x01, 0x31, 0x00,
                                0x40, 0x40, 0x00, 0x00 },
                              TensorSpec("tensor<float>(x{},y{})")
                              .add({{"x", "1"}, {"y", ""}}, 3)));
}

TEST("test tensor serialization for DenseTensor") {
    TEST_DO(verify_serialized({0x02, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("double")));
    TEST_DO(verify_serialized({0x02, 0x01, 0x01, 0x78, 0x01,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[1])")
                              .add({{"x", 0}}, 0)));
    TEST_DO(verify_serialized({0x02, 0x02, 0x01, 0x78, 0x01,
                               0x01, 0x79, 0x01,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[1],y[1])")
                              .add({{"x", 0}, {"y", 0}}, 0)));
    TEST_DO(verify_serialized({0x02, 0x01, 0x01, 0x78, 0x02,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x40, 0x08, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[2])")
                              .add({{"x", 1}}, 3)));
    TEST_DO(verify_serialized({0x02, 0x02, 0x01, 0x78, 0x01,
                               0x01, 0x79, 0x01,
                               0x40, 0x08, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[1],y[1])")
                              .add({{"x", 0}, {"y", 0}}, 3)));
    TEST_DO(verify_serialized({0x02, 0x02, 0x01, 0x78, 0x02,
                               0x01, 0x79, 0x01,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x40, 0x08, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[2],y[1])")
                              .add({{"x", 1}, {"y", 0}}, 3)));
    TEST_DO(verify_serialized({0x02, 0x02, 0x01, 0x78, 0x01,
                               0x01, 0x79, 0x04,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x40, 0x08, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[1],y[4])")
                              .add({{"x", 0}, {"y", 3}}, 3)));
    TEST_DO(verify_serialized({0x02, 0x02, 0x01, 0x78, 0x03,
                               0x01, 0x79, 0x05,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x40, 0x08, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00},
                              TensorSpec("tensor(x[3],y[5])")
                              .add({{"x", 2}, {"y", 4}}, 3)));
}

TEST("test float cells for dense tensor") {
    TEST_DO(verify_serialized({0x06, 0x01, 0x02, 0x01, 0x78, 0x03,
                               0x01, 0x79, 0x05,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00,
                               0x40, 0x40, 0x00, 0x00 },
                              TensorSpec("tensor<float>(x[3],y[5])")
                              .add({{"x", 2}, {"y", 4}}, 3)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
