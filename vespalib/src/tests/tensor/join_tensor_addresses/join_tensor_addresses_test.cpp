// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/tensor/compact/compact_tensor_address.h>
#include <vespa/vespalib/tensor/compact/compact_tensor_address_builder.h>
#include <vespa/vespalib/tensor/tensor_address_element_iterator.h>
#include <vespa/vespalib/tensor/dimensions_vector_iterator.h>
#include <vespa/vespalib/tensor/join_tensor_addresses.h>

using namespace vespalib::tensor;

using TensorAddressMap = std::map<std::string, std::string>;
using TensorAddressElementVec =
    std::vector<std::pair<std::string, std::string>>;

namespace vespalib
{

std::ostream &
operator<<(std::ostream &out, const TensorAddressElementVec &vec)
{
    out << "{";
    bool first = true;
    for (const auto &elem : vec) {
        if (!first) {
            out << ",";
        }
        out << "{\"" << elem.first << "\",\"" << elem.second << "\"}";
        first = false;
    }
    out << "}";
    return out;
};

}


class DummyAddressBuilder
{
    TensorAddressElementVec _elements;
public:
    void add(vespalib::stringref dimension, vespalib::stringref label)
    {
        _elements.emplace_back(dimension, label);
    }

    const TensorAddressElementVec &elements() const { return _elements; }
    void clear() { }
};


template <class TensorAddressT> struct FixtureBase;

template <> struct FixtureBase<TensorAddress>
{
    using AddressType = TensorAddress;
    using AddressBuilderType = TensorAddressBuilder;

    static TensorAddress create(TensorAddressBuilder &builder) {
        return builder.build();
    }
};


template <> struct FixtureBase<CompactTensorAddress>
{
    using AddressType = CompactTensorAddress;
    using AddressBuilderType = CompactTensorAddressBuilder;

    vespalib::Stash _stash;

    CompactTensorAddress
    create(CompactTensorAddressBuilder &builder)
    {
        CompactTensorAddressRef oldRef = builder.getAddressRef();
        CompactTensorAddressRef newRef(oldRef, _stash);
        CompactTensorAddress ret;
        ret.deserializeFromSparseAddressRef(newRef);
        return ret;
    }
};

template <> struct FixtureBase<CompactTensorAddressRef>
{
    using AddressType = CompactTensorAddressRef;
    using AddressBuilderType = CompactTensorAddressBuilder;

    vespalib::Stash _stash;

    CompactTensorAddressRef
    create(CompactTensorAddressBuilder &builder)
    {
        CompactTensorAddressRef oldRef = builder.getAddressRef();
        CompactTensorAddressRef newRef(oldRef, _stash);
        return newRef;
    }
};

template <class TensorAddressT> struct Fixture
    : public FixtureBase<TensorAddressT>
{
    using Parent = FixtureBase<TensorAddressT>;
    using AddressType = typename Parent::AddressType;
    using AddressBuilderType = typename Parent::AddressBuilderType;
    using Parent::create;

    AddressType
    create(const TensorAddressMap &address_in) {
        AddressBuilderType builder;
        for (auto &element : address_in) {
            builder.add(element.first, element.second);
        }
        return create(builder);
    }

    void
    verifyJoin3Way(bool exp,
                   const TensorAddressElementVec &expVec,
                   const DimensionsVector &commonDimensions,
                   const TensorAddressMap &lhsAddress_in,
                   const TensorAddressMap &rhsAddress_in)
    {
        AddressType expAddress = create(lhsAddress_in);
        AddressType lhsAddress = create(lhsAddress_in);
        AddressType rhsAddress = create(rhsAddress_in);
        DummyAddressBuilder builder;
        bool act = joinTensorAddresses<DummyAddressBuilder,
            AddressType, AddressType>
                   (builder, commonDimensions, lhsAddress, rhsAddress);
        EXPECT_EQUAL(exp, act);
        if (exp) {
            EXPECT_EQUAL(expVec, builder.elements());
        }
    }

    void
    verifyJoin2Way(bool exp,
                   const TensorAddressElementVec &expVec,
                   const DimensionsSet &commonDimensions,
                   const TensorAddressMap &lhsAddress_in,
                   const TensorAddressMap &rhsAddress_in)
    {
        AddressType expAddress = create(lhsAddress_in);
        AddressType lhsAddress = create(lhsAddress_in);
        AddressType rhsAddress = create(rhsAddress_in);
        DummyAddressBuilder builder;
        bool act = joinTensorAddresses<DummyAddressBuilder,
            AddressType, AddressType>
                   (builder, commonDimensions, lhsAddress, rhsAddress);
        EXPECT_EQUAL(exp, act);
        if (exp) {
            EXPECT_EQUAL(expVec, builder.elements());
        }
    }

    void
    verifyJoin(bool exp,
               const TensorAddressElementVec &expVec,
               const DimensionsVector &commonDimensions,
               const TensorAddressMap &lhsAddress,
               const TensorAddressMap &rhsAddress)
    {
        TEST_DO(verifyJoin3Way(exp, expVec, commonDimensions,
                               lhsAddress, rhsAddress));
        DimensionsSet commonDimensionsSet(commonDimensions.begin(),
                                          commonDimensions.end());
        TEST_DO(verifyJoin2Way(exp, expVec, commonDimensionsSet,
                               lhsAddress, rhsAddress));
    }

    void
    verifyJoin(const TensorAddressElementVec &expVec,
               const DimensionsVector &commonDimensions,
               const TensorAddressMap &lhsAddress,
               const TensorAddressMap &rhsAddress)
    {
        verifyJoin(true, expVec, commonDimensions, lhsAddress, rhsAddress);
    }

    void
    verifyJoinFailure(const DimensionsVector &commonDimensions,
                      const TensorAddressMap &lhsAddress,
                      const TensorAddressMap &rhsAddress)
    {
        verifyJoin(false, {}, commonDimensions, lhsAddress, rhsAddress);
    }

    void
    verifyJoinFailureOnLabelMisMatch()
    {
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"x", "1"}, {"y", "3"}}));
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"x", "2"}, {"y", "2"}}));
        TEST_DO(verifyJoinFailure({"y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"y", "1"}, {"z", "3"}}));
        TEST_DO(verifyJoinFailure({"y"},
                           {{"y", "2"}, {"z", "3"}},
                           {{"x", "1"}, {"y", "1"}}));
    }

    void
    verityJoinFailureOnMissingDimension()
    {
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"y", "2"}},
                           {{"x", "2"}, {"y", "2"}}));
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"y", "2"}}));
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"x", "1"}},
                           {{"x", "2"}, {"y", "2"}}));
        TEST_DO(verifyJoinFailure({"x", "y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"x", "2"}}));
        TEST_DO(verifyJoinFailure({"x", "y", "z"},
                           {{"x", "1"}, {"z", "3"}},
                           {{"x", "2"}, {"y", "2"}, {"z", "3"}}));
        TEST_DO(verifyJoinFailure({"x", "y", "z"},
                           {{"x", "2"}, {"y", "2"}, {"z", "3"}},
                           {{"x", "1"}, {"z", "3"}}));
    }

    void
    verifyJoinSuccessOnDisjunctDimensions()
    {
        TEST_DO(verifyJoin({}, {}, {}, {}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}, {"zz", "4"}},
                           {},
                           {{"x", "1"}, {"y", "2"}},
                           {{"z", "3"}, {"zz", "4"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}, {"zz", "4"}},
                           {},
                           {{"z", "3"}, {"zz", "4"}},
                           {{"x", "1"}, {"y", "2"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}, {"zz", "4"}},
                           {},
                           {{"x", "1"}, {"z", "3"}},
                           {{"y", "2"}, {"zz", "4"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}},
                           {},
                           {{"x", "1"}, {"y", "2"}},
                           {}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}},
                           {},
                           {},
                           {{"x", "1"}, {"y", "2"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"z", "3"}}, {"y"},
                           {{"x", "1"}},
                           {{"z", "3"}}));
        TEST_DO(verifyJoin( {{"x", "1"}, {"z", "3"}}, {"y"},
                           {{"z", "3"}},
                           {{"x", "1"}}));
    }

    void
    verifyJoinSuccessOnOverlappingDimensions()
    {
        TEST_DO(verifyJoin({{"x", "1"}}, {"x"},
                           {{"x", "1"}}, {{"x", "1"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}},
                           {"x", "z"},
                           {{"x", "1"}, {"y", "2"}, {"z", "3"}},
                           {{"x", "1"}, {"z", "3"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}},
                           {"x", "z"},
                           {{"x", "1"}, {"y", "2"}, {"z", "3"}},
                           {{"x", "1"}, {"z", "3"}}));
        TEST_DO(verifyJoin( {{"x", "1"}, {"y", "2"}}, {"x", "y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"x", "1"}, {"y", "2"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}}, {"y"},
                           {{"x", "1"}, {"y", "2"}},
                           {{"y", "2"}, {"z", "3"}}));
        TEST_DO(verifyJoin({{"x", "1"}, {"y", "2"}, {"z", "3"}}, {"y"},
                           {{"y", "2"}, {"z", "3"}},
                           {{"x", "1"}, {"y", "2"}}));
    }

    void
    verifyJoin()
    {
        verifyJoinSuccessOnDisjunctDimensions();
        verifyJoinSuccessOnOverlappingDimensions();
        verifyJoinFailureOnLabelMisMatch();
        verityJoinFailureOnMissingDimension();
    }

};


TEST_F("Test that Tensor address can be joined", Fixture<TensorAddress>)
{
    f.verifyJoin();
}

TEST_F("Test that compact Tensor address can be joined",
       Fixture<CompactTensorAddress>)
{
    f.verifyJoin();
}


TEST_F("Test that compact Tensor address ref can be joined",
       Fixture<CompactTensorAddressRef>)
{
    f.verifyJoin();
}

TEST_MAIN() { TEST_RUN_ALL(); }
