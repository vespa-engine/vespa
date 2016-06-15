// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/tensor/tensor_address.h>
#include <vespa/vespalib/tensor/tensor_address_builder.h>
#include <vespa/vespalib/tensor/compact/compact_tensor_address.h>
#include <vespa/vespalib/tensor/compact/compact_tensor_address_builder.h>
#include <vespa/vespalib/tensor/tensor_address_element_iterator.h>

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
    verifyPlainIterate(const TensorAddressMap &address_in)
    {
        AddressType address = create(address_in);
        TensorAddressElementIterator<AddressType> itr(address);
        for (auto &element : address_in) {
            EXPECT_TRUE(itr.valid());
            EXPECT_EQUAL(element.first, itr.dimension());
            EXPECT_EQUAL(element.second, itr.label());
            itr.next();
        }
        EXPECT_FALSE(itr.valid());
    }


    void
    verifyPlainIterate()
    {
        TEST_DO(verifyPlainIterate({}));
        TEST_DO(verifyPlainIterate({{"a", "1"}}));
        TEST_DO(verifyPlainIterate({{"a", "1"}, {"b", "2"}}));
    }

    void
    verifyBeforeDimension(const TensorAddressMap &lhsAddress_in,
                          const TensorAddressMap &rhsAddress_in,
                          bool exp)
    {
        AddressType lhsAddress = create(lhsAddress_in);
        TensorAddressElementIterator<AddressType> lhsItr(lhsAddress);
        AddressType rhsAddress = create(rhsAddress_in);
        TensorAddressElementIterator<AddressType> rhsItr(rhsAddress);
        EXPECT_EQUAL(exp, lhsItr.beforeDimension(rhsItr));
    }

    void
    verifyBeforeDimension() {
        TEST_DO(verifyBeforeDimension({}, {}, false));
        TEST_DO(verifyBeforeDimension({}, {{"x", "1"}}, false));
        TEST_DO(verifyBeforeDimension({{"x", "1"}}, {}, true));
        TEST_DO(verifyBeforeDimension({{"x", "1"}}, {{"x", "2"}}, false));
        TEST_DO(verifyBeforeDimension({{"x", "1"}}, {{"y", "2"}}, true));
        TEST_DO(verifyBeforeDimension({{"y", "1"}}, {{"x", "2"}}, false));
    }

    void
    verifyAtDimension(const TensorAddressMap &address_in,
                      vespalib::stringref dimension,
                      bool exp)
    {
        AddressType address = create(address_in);
        TensorAddressElementIterator<AddressType> itr(address);
        EXPECT_EQUAL(exp, itr.atDimension(dimension));
    }

    void
    verifyAtDimension()
    {
        TEST_DO(verifyAtDimension({}, "x", false));
        TEST_DO(verifyAtDimension({{"x", "1"}}, "x", true));
        TEST_DO(verifyAtDimension({{"x", "1"}}, "y", false));
        TEST_DO(verifyAtDimension({{"y", "1"}}, "x", false));
        TEST_DO(verifyAtDimension({{"y", "1"}}, "y", true));
    }

    void
    verifyAddElements(const TensorAddressMap &lhsAddress_in,
                      const TensorAddressMap &rhsAddress_in,
                      const TensorAddressElementVec &exp)
    {
        AddressType lhsAddress = create(lhsAddress_in);
        TensorAddressElementIterator<AddressType> lhsItr(lhsAddress);
        AddressType rhsAddress = create(rhsAddress_in);
        TensorAddressElementIterator<AddressType> rhsItr(rhsAddress);
        DummyAddressBuilder builder;
        lhsItr.addElements(builder, rhsItr);
        EXPECT_EQUAL(exp, builder.elements());
    }

    void verifyAddElements(const TensorAddressMap &address_in,
                           const TensorAddressElementVec &exp)
    {
        AddressType address = create(address_in);
        TensorAddressElementIterator<AddressType> itr(address);
        DummyAddressBuilder builder;
        itr.addElements(builder);
        EXPECT_EQUAL(exp, builder.elements());
    }

    void verifyAddElements(const TensorAddressMap &address_in,
                           const DimensionsSet &dimensions,
                           bool exp,
                           const TensorAddressElementVec &expVec)
    {
        AddressType address = create(address_in);
        TensorAddressElementIterator<AddressType> itr(address);
        DummyAddressBuilder builder;
        EXPECT_EQUAL(exp, itr.addElements(builder, dimensions));
        EXPECT_EQUAL(expVec, builder.elements());
    }

    void verifyAddElements(const TensorAddressMap &lhsAddress_in,
                           const TensorAddressMap &rhsAddress_in,
                           const DimensionsSet &dimensions,
                           bool exp,
                           const TensorAddressElementVec &expVec)
    {
        AddressType lhsAddress = create(lhsAddress_in);
        TensorAddressElementIterator<AddressType> lhsItr(lhsAddress);
        AddressType rhsAddress = create(rhsAddress_in);
        TensorAddressElementIterator<AddressType> rhsItr(rhsAddress);
        DummyAddressBuilder builder;
        ASSERT_TRUE(lhsItr.beforeDimension(rhsItr));
        EXPECT_EQUAL(exp, lhsItr.addElements(builder, dimensions, rhsItr));
        EXPECT_EQUAL(expVec, builder.elements());
    }

    void
    verifyAddElements()
    {
        // Stop according to rhs iterator
        TEST_DO(verifyAddElements({}, {}, {}));
        TEST_DO(verifyAddElements({{"x", "1"}}, {}, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}}, {{"x", "1"}}, {}));
        TEST_DO(verifyAddElements({{"x", "1"}}, {{"y", "1"}}, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"y", "1"}}, {{"x", "1"}}, {}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {{"z", "1"}},
                                  {{"x", "1"}, {"y", "2"}}));
        // Pass through everything
        TEST_DO(verifyAddElements({}, {}));
        TEST_DO(verifyAddElements({{"x", "1"}}, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}},
                                  {{"x", "1"}, {"y", "2"}}));
        // Filter on dimension set
        TEST_DO(verifyAddElements({}, {}, true, {}));
        TEST_DO(verifyAddElements({{"x", "1"}}, {}, true, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {}, true,
                                  {{"x", "1"}, {"y", "2"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {"y"}, false,
                                  {{"x", "1"}}));
        // Filter on dimension set and stop according to rhs iterator
        TEST_DO(verifyAddElements({{"x", "1"}}, {}, {}, true, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {}, {}, true,
                                  {{"x", "1"}, {"y", "2"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {{"y", "2"}}, {},
                                  true, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {{"y", "2"}}, {"y"},
                                  true, {{"x", "1"}}));
        TEST_DO(verifyAddElements({{"x", "1"}, {"y", "2"}}, {{"y", "2"}}, {"x"},
                                  false, {}));
    }
};


TEST_F("Test that Tensor address can be iterated", Fixture<TensorAddress>)
{
    f.verifyPlainIterate();
}

TEST_F("Test that compact Tensor address can be iterated",
       Fixture<CompactTensorAddress>)
{
    f.verifyPlainIterate();
}


TEST_F("Test that compact Tensor address ref can be iterated",
       Fixture<CompactTensorAddressRef>)
{
    f.verifyPlainIterate();
}

TEST_F("Test that Tensor address works with beforeDimension",
       Fixture<TensorAddress>)
{
    f.verifyBeforeDimension();
}

TEST_F("Test that compact Tensor address works with beforeDimension",
       Fixture<CompactTensorAddress>)
{
    f.verifyBeforeDimension();
}

TEST_F("Test that compat Tensor address ref works with beforeDimension",
       Fixture<CompactTensorAddressRef>)
{
    f.verifyBeforeDimension();
}

TEST_F("Test that Tensor address works with atDimension",
       Fixture<TensorAddress>)
{
    f.verifyAtDimension();
}

TEST_F("Test that compact Tensor address works with atDimension",
       Fixture<CompactTensorAddress>)
{
    f.verifyAtDimension();
}

TEST_F("Test that compat Tensor address ref works with atDimension",
       Fixture<CompactTensorAddressRef>)
{
    f.verifyAtDimension();
}

TEST_F("Test that Tensor address works with addElements",
       Fixture<TensorAddress>)
{
    f.verifyAddElements();
}

TEST_F("Test that compact Tensor address works with addElements",
       Fixture<CompactTensorAddress>)
{
    f.verifyAddElements();
}

TEST_F("Test that compat Tensor address ref works with addElements",
       Fixture<CompactTensorAddressRef>)
{
    f.verifyAddElements();
}


TEST_MAIN() { TEST_RUN_ALL(); }
