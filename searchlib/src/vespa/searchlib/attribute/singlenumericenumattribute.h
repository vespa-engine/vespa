// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singleenumattribute.h"
#include "numericbase.h"
#include <map>

namespace search {

/**
 * Implementation of single value numeric enum attribute that uses an underlying enum store
 * to store unique numeric values.
 *
 * B: EnumAttribute<NumericBaseClass>
 */
template <typename B>
class SingleValueNumericEnumAttribute : public SingleValueEnumAttribute<B> {
protected:
    using T = typename B::BaseClass::BaseType;
    using Change = typename B::BaseClass::Change;
    using DocId = typename B::BaseClass::DocId;
    using EnumHandle = typename B::BaseClass::EnumHandle;
    using EnumIndex = typename SingleValueEnumAttributeBase::EnumIndex;
    using EnumStore = typename SingleValueEnumAttribute<B>::EnumStore;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;
    using EnumVector = IEnumStore::EnumVector;
    using LoadedNumericValueT = typename B::BaseClass::LoadedNumericValueT;
    using LoadedVector = typename B::BaseClass::LoadedVector;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using Weighted = typename B::BaseClass::Weighted;
    using WeightedFloat = typename B::BaseClass::WeightedFloat;
    using WeightedInt = typename B::BaseClass::WeightedInt;
    using generation_t = typename B::BaseClass::generation_t;
    using largeint_t = typename B::BaseClass::largeint_t;

private:
    // used to make sure several arithmetic operations on the same document in a single commit works
    std::map<DocId, T> _currDocValues;

protected:

    // from SingleValueEnumAttribute
    void considerUpdateAttributeChange(const Change & c) override;
    void considerArithmeticAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter) override;
    void applyArithmeticValueChange(const Change& c, EnumStoreBatchUpdater& updater) override;

    /*
     * Specialization of SearchContext
     */
    class SingleSearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    protected:
        const SingleValueNumericEnumAttribute<B> & _toBeSearched;

        int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const override {
            return find(docId, elemId, weight);
        }

        int32_t onFind(DocId docId, int32_t elemId) const override {
            return find(docId, elemId);
        }
        bool valid() const override;

    public:
        SingleSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched);

        Int64Range getAsIntegerTerm() const override;

        int32_t find(DocId docId, int32_t elemId, int32_t & weight) const {
            if ( elemId != 0) return -1;
            T v = _toBeSearched._enumStore.get_value(_toBeSearched.getEnumIndex(docId));
            weight = 1;
            return this->match(v) ? 0 : -1;
        }

        int32_t find(DocId docId, int32_t elemId) const {
            if ( elemId != 0) return -1;
            T v = _toBeSearched._enumStore.get_value(_toBeSearched.getEnumIndex(docId));
            return this->match(v) ? 0 : -1;
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
    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        return this->_enumStore.get_value(this->_enumIndices[doc]);
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

