// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_builder.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/eval/tensor/serialization/sparse_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <ostream>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>

using namespace vespalib::tensor;
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

namespace vespalib::tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}

template <class BuilderType>
void
checkDeserialize(vespalib::nbostream &stream, const Tensor &rhs)
{
    (void) stream;
    (void) rhs;
}

template <>
void
checkDeserialize<DefaultTensor::builder>(nbostream &stream, const Tensor &rhs)
{
    nbostream wrapStream(stream.peek(), stream.size());
    auto chk = TypedBinaryFormat::deserialize(wrapStream);
    EXPECT_EQUAL(0u, wrapStream.size());
    EXPECT_EQUAL(*chk, rhs);
}

template <typename BuilderType>
struct Fixture
{
    BuilderType _builder;
    Fixture() : _builder() {}

    Tensor::UP createTensor(const TensorCells &cells) {
        return TensorFactory::create(cells, _builder);
    }
    Tensor::UP createTensor(const TensorCells &cells, const TensorDimensions &dimensions) {
        return TensorFactory::create(cells, dimensions, _builder);
    }

    void serialize(nbostream &stream, const Tensor &tensor) {
        TypedBinaryFormat::serialize(stream, tensor);
    }
    Tensor::UP deserialize(nbostream &stream) {
        BuilderType builder;
        nbostream wrapStream(stream.peek(), stream.size());
        auto formatId = wrapStream.getInt1_4Bytes();
        ASSERT_EQUAL(formatId, 1u); // sparse format
        SparseBinaryFormat::deserialize(wrapStream, builder);
        EXPECT_TRUE(wrapStream.empty());
        auto ret = builder.build();
        checkDeserialize<BuilderType>(stream, *ret);
        stream.adjustReadPos(stream.size());
        return ret;
    }
    void assertSerialized(const ExpBuffer &exp, const TensorCells &rhs,
                          const TensorDimensions &rhsDimensions) {
        Tensor::UP rhsTensor(createTensor(rhs, rhsDimensions));
        nbostream rhsStream;
        serialize(rhsStream, *rhsTensor);
        EXPECT_EQUAL(exp, rhsStream);
        auto rhs2 = deserialize(rhsStream);
        EXPECT_EQUAL(*rhs2, *rhsTensor);
    }
};

using SparseFixture = Fixture<SparseTensorBuilder>;


template <typename FixtureType>
void
testTensorSerialization(FixtureType &f)
{
    TEST_DO(f.assertSerialized({ 0x01, 0x00, 0x00 }, {}, {}));
    TEST_DO(f.assertSerialized({ 0x01, 0x01, 0x01, 0x78, 0x00 },
                               {}, { "x" }));
    TEST_DO(f.assertSerialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x00 },
                               {}, { "x", "y" }));
    TEST_DO(f.assertSerialized({ 0x01, 0x01, 0x01, 0x78, 0x01, 0x01, 0x31, 0x40,
                                 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
                               { {{{"x","1"}}, 3} }, { "x" }));
    TEST_DO(f.assertSerialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x00,
                                 0x00, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00,
                                 0x00 },
                               { {{}, 3} }, { "x", "y"}));
    TEST_DO(f.assertSerialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x01,
                                 0x31, 0x00, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00,
                                 0x00, 0x00 },
                               { {{{"x","1"}}, 3} }, { "x", "y" }));
    TEST_DO(f.assertSerialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x00,
                                 0x01, 0x33, 0x40, 0x08, 0x00, 0x00, 0x00, 0x00,
                                 0x00, 0x00 },
                               { {{{"y","3"}}, 3} }, { "x", "y" }));
    TEST_DO(f.assertSerialized({ 0x01, 0x02, 0x01, 0x78, 0x01, 0x79, 0x01, 0x01,
                                 0x32, 0x01, 0x34, 0x40, 0x08, 0x00, 0x00, 0x00,
                                 0x00, 0x00, 0x00 },
                               { {{{"x","2"}, {"y", "4"}}, 3} }, { "x", "y" }));
    TEST_DO(f.assertSerialized({        0x01, 0x02, 0x01, 0x78, 0x01, 0x79,
                                        0x01, 0x01, 0x31, 0x00, 0x40, 0x08,
                                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
                               { {{{"x","1"}}, 3} }, {"x", "y"}));
}

TEST_F("test tensor serialization for SparseTensor", SparseFixture)
{
    testTensorSerialization(f);
}


struct DenseFixture
{
    Tensor::UP createTensor(const DenseTensorCells &cells) {
        return TensorFactory::createDense(cells);
    }

    void serialize(nbostream &stream, const Tensor &tensor) {
        TypedBinaryFormat::serialize(stream, tensor);
    }

    Tensor::UP deserialize(nbostream &stream) {
        nbostream wrapStream(stream.peek(), stream.size());
        auto ret = TypedBinaryFormat::deserialize(wrapStream);
        EXPECT_TRUE(wrapStream.size() == 0);
        stream.adjustReadPos(stream.size());
        return ret;
    }
    void assertSerialized(const ExpBuffer &exp, const DenseTensorCells &rhs) {
        assertSerialized(exp, SerializeFormat::DOUBLE, rhs);
    }
    template <typename T>
    void assertCellsOnly(const ExpBuffer &exp, const DenseTensorView & rhs) {
        nbostream a(&exp[0], exp.size());
        std::vector<T> v;
        TypedBinaryFormat::deserializeCellsOnlyFromDenseTensors(a, v);
        EXPECT_EQUAL(v.size(), rhs.cellsRef().size());
        for (size_t i(0); i < v.size(); i++) {
            EXPECT_EQUAL(v[i], rhs.cellsRef()[i]);
        }
    }
    void assertSerialized(const ExpBuffer &exp, SerializeFormat cellType, const DenseTensorCells &rhs) {
        Tensor::UP rhsTensor(createTensor(rhs));
        nbostream rhsStream;
        TypedBinaryFormat::serialize(rhsStream, *rhsTensor, cellType);
        EXPECT_EQUAL(exp, rhsStream);
        auto rhs2 = deserialize(rhsStream);
        EXPECT_EQUAL(*rhs2, *rhsTensor);

        assertCellsOnly<float>(exp, dynamic_cast<const DenseTensorView &>(*rhs2));
        assertCellsOnly<double>(exp, dynamic_cast<const DenseTensorView &>(*rhs2));
    }
};


TEST_F("test tensor serialization for DenseTensor", DenseFixture) {
    TEST_DO(f.assertSerialized({0x02, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {}));
    TEST_DO(f.assertSerialized({0x02, 0x01, 0x01, 0x78, 0x01,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 0}}, 0}}));
    TEST_DO(f.assertSerialized({0x02, 0x02, 0x01, 0x78, 0x01,
                                0x01, 0x79, 0x01,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 0}, {"y", 0}}, 0}}));
    TEST_DO(f.assertSerialized({0x02, 0x01, 0x01, 0x78, 0x02,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x40, 0x08, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 1}}, 3}}));
    TEST_DO(f.assertSerialized({0x02, 0x02, 0x01, 0x78, 0x01,
                                0x01, 0x79, 0x01,
                                0x40, 0x08, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 0}, {"y", 0}}, 3}}));
    TEST_DO(f.assertSerialized({0x02, 0x02, 0x01, 0x78, 0x02,
                                0x01, 0x79, 0x01,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x40, 0x08, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 1}, {"y", 0}}, 3}}));
    TEST_DO(f.assertSerialized({0x02, 0x02, 0x01, 0x78, 0x01,
                                0x01, 0x79, 0x04,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00,
                                0x40, 0x08, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00},
                               {{{{"x", 0}, {"y", 3}}, 3}}));
    TEST_DO(f.assertSerialized({0x02, 0x02, 0x01, 0x78, 0x03,
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
                               {{{{"x", 2}, {"y", 4}}, 3}}));
}

TEST_F("test 'float' cells", DenseFixture) {
    TEST_DO(f.assertSerialized({0x04, 0x01, 0x02, 0x01, 0x78, 0x03,
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
                               SerializeFormat::FLOAT, { {{{"x",2}, {"y",4}}, 3} }));
}


TEST_MAIN() { TEST_RUN_ALL(); }
