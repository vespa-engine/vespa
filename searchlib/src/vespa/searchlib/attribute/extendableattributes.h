// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class search::SearchVisitor
 *
 * @brief Visitor that applies a search query to visitor data and converts them to a SearchResultCommand
 */
#pragma once

#include "attrvector.h"
#include "attrvector.hpp"

namespace search {

// Translates the actual value type to the type required by IExtendAttribute.
template <typename T> struct AddValueType {
    typedef int64_t Type;
};
template <> struct AddValueType<double> {
    typedef double Type;
};

//******************** CollectionType::SINGLE ********************//

template <typename T> struct AttributeTemplate {
    typedef search::IntegerAttributeTemplate<T> Type;
};
template <> struct AttributeTemplate<double> {
    typedef search::FloatingPointAttributeTemplate<double> Type;
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

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override
    {
        (void) term;
        (void) params;
        return AttributeVector::SearchContext::UP();
    }
    IExtendAttribute * getExtendInterface() override { return this; }
public:
    SingleExtAttribute(const vespalib::string &name)
        : Super(name, Config(BasicType::fromType(T()),
                             attribute::CollectionType::SINGLE)) {}

    bool addDoc(typename Super::DocId &docId) override {
        docId = this->_data.size();
        this->_data.push_back(attribute::getUndefined<T>());
        this->incNumDocs();
        this->setCommittedDocIdLimit(this->getNumDocs());
        return true;
    }
    bool add(typename AddValueType<T>::Type v, int32_t = 1) override {
        this->_data.back() = v;
        return true;
    }
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(typename Super::DocId lidLimit) override {
        this->_data.reserve(lidLimit);
    }
};

typedef SingleExtAttribute<int8_t> SingleInt8ExtAttribute;
typedef SingleExtAttribute<int16_t> SingleInt16ExtAttribute;
typedef SingleExtAttribute<int32_t> SingleInt32ExtAttribute;
typedef SingleExtAttribute<int64_t> SingleInt64ExtAttribute;
typedef SingleExtAttribute<double> SingleFloatExtAttribute;

typedef SingleInt64ExtAttribute SingleIntegerExtAttribute;

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
      public IExtendAttribute
{
protected:
    typedef typename MultiExtAttribute<T>::NumDirectAttrVec Super;
    typedef typename Super::Config Config;
    typedef typename Super::BasicType BasicType;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

    MultiExtAttribute(const vespalib::string &name, const attribute::CollectionType &ctype)
        : Super(name, Config(BasicType::fromType(T()), ctype))
    { }
private:
    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override
    {
        (void) term;
        (void) params;
        return AttributeVector::SearchContext::UP();
    }
    IExtendAttribute * getExtendInterface() override { return this; }

public:
    MultiExtAttribute(const vespalib::string &name)
        : Super(name, Config(BasicType::fromType(static_cast<T>(0)),
                             attribute::CollectionType::ARRAY)) {}

    bool addDoc(typename Super::DocId &docId) override {
        docId = this->_idx.size() - 1;
        this->_idx.push_back(this->_idx.back());
        this->incNumDocs();
        this->setCommittedDocIdLimit(this->getNumDocs());
        return true;
    }
    bool add(typename AddValueType<T>::Type v, int32_t = 1) override {
        this->_data.push_back(v);
        std::vector<uint32_t> &idx = this->_idx;
        idx.back()++;
        this->checkSetMaxValueCount(idx.back() - idx[idx.size() - 2]);
        return true;
    }
    bool onLoad(vespalib::Executor *) override {
        return false; // Emulate that this attribute is never loaded
    }
    void onAddDocs(uint32_t lidLimit) override {
        this->_data.reserve(lidLimit);
    }
};

typedef MultiExtAttribute<int8_t> MultiInt8ExtAttribute;
typedef MultiExtAttribute<int16_t> MultiInt16ExtAttribute;
typedef MultiExtAttribute<int32_t> MultiInt32ExtAttribute;
typedef MultiExtAttribute<int64_t> MultiInt64ExtAttribute;
typedef MultiExtAttribute<double> MultiFloatExtAttribute;

typedef MultiInt64ExtAttribute MultiIntegerExtAttribute;

class MultiStringExtAttribute :
    public StringDirectAttrVector< AttrVector::Features<true> >,
    public IExtendAttribute
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
};


//******************** CollectionType::WSET ********************//

template <typename B>
class WeightedSetExtAttributeBase : public B
{
private:
    std::vector<int32_t> _weights;

protected:
    void addWeight(int32_t w) {
        _weights.push_back(w);
    }
    int32_t getWeightHelper(AttributeVector::DocId docId, uint32_t idx) const {
        return _weights[this->_idx[docId] + idx];
    }
    WeightedSetExtAttributeBase(const vespalib::string & name) :
        B(name, attribute::CollectionType::WSET),
        _weights()
    {}
    ~WeightedSetExtAttributeBase() {}
};

class WeightedSetIntegerExtAttribute
    : public WeightedSetExtAttributeBase<MultiIntegerExtAttribute>
{
    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override
    {
        (void) term;
        (void) params;
        return AttributeVector::SearchContext::UP();
    }
public:
    WeightedSetIntegerExtAttribute(const vespalib::string & name);
    ~WeightedSetIntegerExtAttribute();
    bool add(int64_t v, int32_t w = 1) override;
    uint32_t get(DocId doc, AttributeVector::WeightedInt * v, uint32_t sz) const override;
};

class WeightedSetFloatExtAttribute
    : public WeightedSetExtAttributeBase<MultiFloatExtAttribute>
{
    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override
    {
        (void) term;
        (void) params;
        return AttributeVector::SearchContext::UP();
    }
public:
    WeightedSetFloatExtAttribute(const vespalib::string & name);
    ~WeightedSetFloatExtAttribute();
    bool add(double v, int32_t w = 1) override;
    uint32_t get(DocId doc, AttributeVector::WeightedFloat * v, uint32_t sz) const override;
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
};

}  // namespace search

