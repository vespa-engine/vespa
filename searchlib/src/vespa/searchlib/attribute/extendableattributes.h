// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class search::SearchVisitor
 *
 * @brief Visitor that applies a search query to visitor data and converts them to a QueryResultCommand
 */
#pragma once

#include "attrvector.h"
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>

namespace search {

// Translates the actual value type to the type required by IExtendAttribute.
template <typename T> struct AddValueType {
    using Type = int64_t;
};
template <> struct AddValueType<double> {
    using Type = double;
};

//******************** CollectionType::SINGLE ********************//

template <typename T> struct AttributeTemplate {
    using Type = search::IntegerAttributeTemplate<T>;
};
template <> struct AttributeTemplate<double> {
    using Type = search::FloatingPointAttributeTemplate<double>;
};

template <typename T>
class SingleExtAttribute
    : public NumericDirectAttrVector<AttrVector::Features<false>,
                                     typename AttributeTemplate<T>::Type>,
      public IExtendAttribute
{
    using Super = typename SingleExtAttribute<T>::NumDirectAttrVec;
    using Config =  typename Super::Config;
    using BasicType = typename Super::BasicType;
    using QueryTermSimpleUP = typename Super::QueryTermSimpleUP;

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
    IExtendAttribute * getExtendInterface() override { return this; }
public:
    SingleExtAttribute(const vespalib::string &name);

    bool addDoc(typename Super::DocId &docId) override;
    bool add(typename AddValueType<T>::Type v, int32_t = 1) override;
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(typename Super::DocId lidLimit) override;
};

using SingleInt8ExtAttribute = SingleExtAttribute<int8_t>;
using SingleInt16ExtAttribute = SingleExtAttribute<int16_t>;
using SingleInt32ExtAttribute = SingleExtAttribute<int32_t>;
using SingleInt64ExtAttribute = SingleExtAttribute<int64_t>;
using SingleFloatExtAttribute = SingleExtAttribute<double>;
using SingleIntegerExtAttribute = SingleInt64ExtAttribute;

class SingleStringExtAttribute
    : public StringDirectAttrVector< AttrVector::Features<false> >,
      public IExtendAttribute
{
    IExtendAttribute * getExtendInterface() override { return this; }
public:
    SingleStringExtAttribute(const vespalib::string & name);
    bool addDoc(DocId & docId) override;
    bool add(const char * v, int32_t w = 1) override;
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(DocId ) override { }
};

//******************** CollectionType::ARRAY ********************//

template <typename T>
class MultiExtAttribute
    : public NumericDirectAttrVector<AttrVector::Features<true>, typename AttributeTemplate<T>::Type>,
      public IExtendAttribute,
      public attribute::IMultiValueAttribute
{
protected:
    using Super = typename MultiExtAttribute<T>::NumDirectAttrVec;
    using Config = typename Super::Config;
    using BasicType = typename Super::BasicType;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

    MultiExtAttribute(const vespalib::string &name, const attribute::CollectionType &ctype);
private:
    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
    IExtendAttribute * getExtendInterface() override { return this; }

public:
    MultiExtAttribute(const vespalib::string &name);
    ~MultiExtAttribute() override;

    bool addDoc(typename Super::DocId &docId) override;
    bool add(typename AddValueType<T>::Type v, int32_t = 1) override;
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(uint32_t lidLimit) override;
    const attribute::IMultiValueAttribute* as_multi_value_attribute() const override;

    // Implements attribute::IMultiValueAttribute
    const attribute::IArrayReadView<T>* make_read_view(attribute::IMultiValueAttribute::ArrayTag<T>, vespalib::Stash& stash) const override;
    const attribute::IWeightedSetReadView<T>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<T>, vespalib::Stash& stash) const override;
};

using MultiInt8ExtAttribute = MultiExtAttribute<int8_t>;
using MultiInt16ExtAttribute = MultiExtAttribute<int16_t>;
using MultiInt32ExtAttribute = MultiExtAttribute<int32_t>;
using MultiInt64ExtAttribute = MultiExtAttribute<int64_t>;
using MultiFloatExtAttribute = MultiExtAttribute<double>;
using MultiIntegerExtAttribute = MultiInt64ExtAttribute;

class MultiStringExtAttribute :
    public StringDirectAttrVector< AttrVector::Features<true> >,
    public IExtendAttribute,
    public attribute::IMultiValueAttribute
{
    IExtendAttribute * getExtendInterface() override { return this; }
protected:
    MultiStringExtAttribute(const vespalib::string & name, const attribute::CollectionType & ctype);

public:
    MultiStringExtAttribute(const vespalib::string & name);
    bool addDoc(DocId & docId) override;
    bool add(const char * v, int32_t w = 1) override;
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(DocId ) override { }
    const attribute::IMultiValueAttribute* as_multi_value_attribute() const override;
    // Implements attribute::IMultiValueAttribute
    const attribute::IArrayReadView<const char*>* make_read_view(attribute::IMultiValueAttribute::ArrayTag<const char*>, vespalib::Stash& stash) const override;
    const attribute::IWeightedSetReadView<const char*>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const override;
};


//******************** CollectionType::WSET ********************//

template <typename B>
class WeightedSetExtAttributeBase : public B
{
private:
    std::vector<int32_t> _weights;

protected:
    void addWeight(int32_t w);
    int32_t getWeightHelper(AttributeVector::DocId docId, uint32_t idx) const {
        return _weights[this->_idx[docId] + idx];
    }
    WeightedSetExtAttributeBase(const vespalib::string & name);
    ~WeightedSetExtAttributeBase();
    const std::vector<int32_t>& get_weights() const noexcept { return _weights; }
};

class WeightedSetIntegerExtAttribute
    : public WeightedSetExtAttributeBase<MultiIntegerExtAttribute>
{
    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
public:
    WeightedSetIntegerExtAttribute(const vespalib::string & name);
    ~WeightedSetIntegerExtAttribute();
    bool add(int64_t v, int32_t w = 1) override;
    uint32_t get(DocId doc, AttributeVector::WeightedInt * v, uint32_t sz) const override;
    // Implements attribute::IMultiValueAttribute
    const attribute::IWeightedSetReadView<int64_t>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<int64_t>, vespalib::Stash& stash) const override;
};

class WeightedSetFloatExtAttribute
    : public WeightedSetExtAttributeBase<MultiFloatExtAttribute>
{
    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
public:
    WeightedSetFloatExtAttribute(const vespalib::string & name);
    ~WeightedSetFloatExtAttribute();
    bool add(double v, int32_t w = 1) override;
    uint32_t get(DocId doc, AttributeVector::WeightedFloat * v, uint32_t sz) const override;
    // Implements attribute::IMultiValueAttribute
    const attribute::IWeightedSetReadView<double>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<double>, vespalib::Stash& stash) const override;
};

class WeightedSetStringExtAttribute
    : public WeightedSetExtAttributeBase<MultiStringExtAttribute>
{
private:
    const char * getHelper(DocId doc, int idx) const {
        return &_buffer[_offsets[_idx[doc] + idx]];
    }
    template <typename T>
    uint32_t getAllHelper(DocId doc, T * v, uint32_t sz) const
    {
        uint32_t valueCount = _idx[doc + 1] - _idx[doc];
        uint32_t num2Read = std::min(valueCount, sz);
        for (uint32_t i = 0; i < num2Read; ++i) {
            v[i] = T(getHelper(doc, i), getWeightHelper(doc, i));
        }
        return valueCount;
    }

public:
    WeightedSetStringExtAttribute(const vespalib::string & name);
    ~WeightedSetStringExtAttribute();
    bool add(const char * v, int32_t w = 1) override;
    uint32_t get(DocId doc, AttributeVector::WeightedString * v, uint32_t sz) const override;
    uint32_t get(DocId doc, AttributeVector::WeightedConstChar * v, uint32_t sz) const override;
    // Implements attribute::IMultiValueAttribute
    const attribute::IWeightedSetReadView<const char*>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const override;
};

}  // namespace search

