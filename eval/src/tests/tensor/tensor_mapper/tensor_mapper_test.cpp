// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/eval/tensor/tensor_mapper.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <ostream>

using vespalib::eval::ValueType;
using namespace vespalib::tensor;

namespace vespalib {
namespace tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}
}

template <typename BuilderType>
bool defaultBuilder() { return false; }

template <>
bool defaultBuilder<DefaultTensor::builder>() { return true; }

template <typename BuilderType>
struct TensorTFromBuilder;

template <>
struct TensorTFromBuilder<SparseTensorBuilder> {
    using TensorT = SparseTensor;
};

template <typename BuilderType>
using TensorTFromBuilder_t = typename TensorTFromBuilder<BuilderType>::TensorT;

struct FixtureBase
{
    Tensor::UP createDenseTensor(const DenseTensorCells &cells) {
        return TensorFactory::createDense(cells);
    }
};

template <typename BuilderType>
struct Fixture : public FixtureBase
{
    BuilderType _builder;
    using TensorT = TensorTFromBuilder_t<BuilderType>;
    Fixture() : FixtureBase(), _builder() {}

    Tensor::UP createTensor(const TensorCells &cells,
                            const TensorDimensions &dimensions) {
        return TensorFactory::create(cells, dimensions, _builder);
    }

    void assertSparseMapImpl(const Tensor &exp,
                             const ValueType &tensorType,
                             const Tensor &rhs, bool isDefaultBuilder)
    {
        EXPECT_TRUE(tensorType.is_sparse());
        if (isDefaultBuilder) {
            TensorMapper mapper(tensorType);
            std::unique_ptr<Tensor> mapped = mapper.map(rhs);
            EXPECT_TRUE(!!mapped);
            EXPECT_EQUAL(exp, *mapped);
        }
        std::unique_ptr<Tensor> mapped =
            TensorMapper::mapToSparse<TensorT>(rhs, tensorType);
        EXPECT_TRUE(!!mapped);
        EXPECT_EQUAL(exp, *mapped);
    }

    void assertDenseMapImpl(const Tensor &exp,
                            const ValueType &tensorType,
                            const Tensor &rhs)
    {
        EXPECT_TRUE(tensorType.is_dense());
        TensorMapper mapper(tensorType);
        std::unique_ptr<Tensor> mapped = mapper.map(rhs);
        EXPECT_TRUE(!!mapped);
        EXPECT_EQUAL(exp, *mapped);
    }

    void
    assertSparseMap(const TensorCells &expTensor,
                    const TensorDimensions &expDimensions,
                    const vespalib::string &typeSpec,
                    const TensorCells &rhsTensor,
                    const TensorDimensions &rhsDimensions)
    {
        assertSparseMapImpl(*createTensor(expTensor, expDimensions),
                            ValueType::from_spec(typeSpec),
                            *createTensor(rhsTensor, rhsDimensions),
                            defaultBuilder<BuilderType>());
    }

    void
    assertDenseMap(const DenseTensorCells &expTensor,
                   const vespalib::string &typeSpec,
                   const TensorCells &rhsTensor,
                   const TensorDimensions &rhsDimensions)
    {
        assertDenseMapImpl(*createDenseTensor(expTensor),
                           ValueType::from_spec(typeSpec),
                           *createTensor(rhsTensor, rhsDimensions));
    }
};

using SparseFixture = Fixture<SparseTensorBuilder>;

template <typename FixtureType>
void
testTensorMapper(FixtureType &f)
{
    TEST_DO(f.assertSparseMap({
                                   {{{"y","1"}}, 4},
                                   {{{"y","2"}}, 12}
                               },
                              { "y" },
                              "tensor(y{})",
                              {
                                  {{{"x","1"},{"y","1"}}, 1},
                                  {{{"x","2"},{"y","1"}}, 3},
                                  {{{"x","1"},{"y","2"}}, 5},
                                  {{{"x","2"},{"y","2"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertSparseMap({
                                   {{{"x","1"}}, 6},
                                   {{{"x","2"}}, 10}
                               },
                              { "x" },
                              "tensor(x{})",
                              {
                                  {{{"x","1"},{"y","1"}}, 1},
                                  {{{"x","2"},{"y","1"}}, 3},
                                  {{{"x","1"},{"y","2"}}, 5},
                                  {{{"x","2"},{"y","2"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"y",0}}, 4},
                                  {{{"y",1}}, 12},
                                  {{{"y",2}}, 0}
                              },
                             "tensor(y[3])",
                              {
                                  {{{"x","1"},{"y","0"}}, 1},
                                  {{{"x","2"},{"y","0"}}, 3},
                                  {{{"x","1"},{"y","1"}}, 5},
                                  {{{"x","2"},{"y","1"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"y",0}}, 3},
                                  {{{"y",1}}, 5},
                                  {{{"y",2}}, 0}
                              },
                             "tensor(y[3])",
                              {
                                  {{{"x","1"},{"y","0x"}}, 1},
                                  {{{"x","2"},{"y",""}}, 3},
                                  {{{"x","1"},{"y","1"}}, 5},
                                  {{{"x","2"},{"y","10"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"x",0},{"y",0}}, 1},
                                  {{{"x",0},{"y",1}}, 5},
                                  {{{"x",0},{"y",2}}, 0},
                                  {{{"x",1},{"y",0}}, 3},
                                  {{{"x",1},{"y",1}}, 0},
                                  {{{"x",1},{"y",2}}, 0}
                              },
                             "tensor(x[2], y[3])",
                              {
                                  {{{"x","0"},{"y","0"}}, 1},
                                  {{{"x","1"},{"y","0"}}, 3},
                                  {{{"x","0"},{"y","1"}}, 5},
                                  {{{"x","10"},{"y","1"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"x",0},{"y",0}}, 1},
                                  {{{"x",0},{"y",1}}, 5},
                                  {{{"x",1},{"y",0}}, 3},
                                  {{{"x",1},{"y",1}}, 0}
                              },
                             "tensor(x[2], y[])",
                              {
                                  {{{"x","0"},{"y","0"}}, 1},
                                  {{{"x","1"},{"y","0"}}, 3},
                                  {{{"x","0"},{"y","1"}}, 5},
                                  {{{"x","10"},{"y","1"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"x",0},{"y",0}}, 1},
                                  {{{"x",0},{"y",1}}, 5},
                                  {{{"x",1},{"y",0}}, 3},
                                  {{{"x",1},{"y",1}}, 0},
                                  {{{"x",2},{"y",0}}, 7},
                                  {{{"x",2},{"y",1}}, 0}
                              },
                             "tensor(x[], y[])",
                              {
                                  {{{"x","0"},{"y","0"}}, 1},
                                  {{{"x","1"},{"y","0"}}, 3},
                                  {{{"x","0"},{"y","1"}}, 5},
                                  {{{"x","2"},{"y","0"}}, 7}
                              },
                              { "x", "y" }));
    TEST_DO(f.assertDenseMap({
                                  {{{"x",0},{"y",0}}, 1},
                                  {{{"x",0},{"y",1}}, 5},
                                  {{{"x",0},{"y",2}}, 0},
                                  {{{"x",1},{"y",0}}, 3},
                                  {{{"x",1},{"y",1}}, 0},
                                  {{{"x",1},{"y",2}}, 0}
                              },
                             "tensor(x[], y[3])",
                              {
                                  {{{"x","0"},{"y","0"}}, 1},
                                  {{{"x","1"},{"y","0"}}, 3},
                                  {{{"x","0"},{"y","1"}}, 5},
                                  {{{"x","10"},{"y","3"}}, 7}
                              },
                              { "x", "y" }));
}

TEST_F("test tensor mapper for SparseTensor", SparseFixture)
{
    testTensorMapper(f);
}

TEST_MAIN() { TEST_RUN_ALL(); }
