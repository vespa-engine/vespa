// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singleenumattribute.h"
#include "numericbase.h"
#include <map>

namespace search {

/*
 * Implementation of single value numeric enum attribute that uses an underlying enum store
 * to store unique numeric values.
 *
 * B: EnumAttribute<NumericBaseClass>
 */
template <typename B>
class SingleValueNumericEnumAttribute : public SingleValueEnumAttribute<B>
{
protected:
    typedef typename B::BaseClass::BaseType        T;
    typedef typename B::BaseClass::Change          Change;
    typedef typename B::BaseClass::DocId           DocId;
    typedef typename B::BaseClass::EnumHandle      EnumHandle;
    typedef typename B::BaseClass::largeint_t      largeint_t;
    typedef typename B::BaseClass::Weighted        Weighted;
    typedef typename B::BaseClass::WeightedInt     WeightedInt;
    typedef typename B::BaseClass::WeightedFloat   WeightedFloat;
    typedef typename B::BaseClass::generation_t    generation_t;
    typedef typename B::BaseClass::LoadedNumericValueT LoadedNumericValueT;
    typedef typename B::BaseClass::LoadedVector    LoadedVector;

    typedef typename SingleValueEnumAttribute<B>::EnumStore        EnumStore;
    typedef typename SingleValueEnumAttributeBase::EnumIndex       EnumIndex;
    typedef typename SingleValueEnumAttribute<B>::UniqueSet        UniqueSet;
    typedef EnumStoreBase::IndexVector            EnumIndexVector;
    typedef EnumStoreBase::EnumVector             EnumVector;
    typedef attribute::LoadedEnumAttributeVector  LoadedEnumAttributeVector;
    typedef attribute::LoadedEnumAttribute        LoadedEnumAttribute;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

private:
    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, T> _currDocValues;

protected:

    // from SingleValueEnumAttribute
    void considerUpdateAttributeChange(const Change & c) override;
    void considerArithmeticAttributeChange(const Change & c, UniqueSet & newUniques) override;
    void applyArithmeticValueChange(const Change & c, EnumStoreBase::IndexVector & unused) override;

    /*
     * Specialization of SearchContext
     */
    class SingleSearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    protected:
        const SingleValueNumericEnumAttribute<B> & _toBeSearched;

        bool onCmp(DocId docId, int32_t & weight) const override {
            return cmp(docId, weight);
        }

        bool onCmp(DocId docId) const override {
            return cmp(docId);
        }
        bool valid() const override;

    public:
        SingleSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched);

        Int64Range getAsIntegerTerm() const override;

        bool cmp(DocId docId, int32_t & weight) const {
            T v = _toBeSearched._enumStore.getValue(_toBeSearched.getEnumIndex(docId));
            weight = 1;
            return this->match(v);
        }

        bool cmp(DocId docId) const {
            T v = _toBeSearched._enumStore.getValue(_toBeSearched.getEnumIndex(docId));
            return this->match(v);
        }

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };

public:
    SingleValueNumericEnumAttribute(const vespalib::string & baseFileName,
                                    const AttributeVector::Config & c =
                                    AttributeVector::Config(AttributeVector::BasicType::fromType(T()),
                                                        attribute::CollectionType::SINGLE));
    ~SingleValueNumericEnumAttribute();

    void onCommit() override;
    bool onLoad() override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        return this->_enumStore.getValue(this->_enumIndices[doc]);
    }
    largeint_t getInt(DocId doc) const override {
        return static_cast<largeint_t>(get(doc));
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(get(doc));
    }
    uint32_t getAll(DocId doc, T * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = get(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = getInt(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = getFloat(doc);
        }
        return 1;
    }
    uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = Weighted(get(doc));
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = WeightedInt(getInt(doc));
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = WeightedFloat(getFloat(doc));
        }
        return 1;
    }
};

}

