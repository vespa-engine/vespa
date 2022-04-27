// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singleenumattribute.h"
#include "numericbase.h"
#include "numeric_range_matcher.h"
#include "search_context.h"
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

public:
    SingleValueNumericEnumAttribute(const vespalib::string & baseFileName,
                                    const AttributeVector::Config & c =
                                    AttributeVector::Config(AttributeVector::BasicType::fromType(T()),
                                                        attribute::CollectionType::SINGLE));
    ~SingleValueNumericEnumAttribute();

    void onCommit() override;
    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        return this->_enumStore.get_value(this->acquire_enum_entry_ref(doc));
    }
    largeint_t getInt(DocId doc) const override {
        return static_cast<largeint_t>(get(doc));
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(get(doc));
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

