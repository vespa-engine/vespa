// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multienumattribute.h"
#include "numericbase.h"
#include "numeric_range_matcher.h"
#include "primitivereader.h"
#include "search_context.h"

namespace search {

/**
 * Implementation of multi value numeric attribute that uses an underlying enum store
 * to store unique numeric values and a multi value mapping to store enum indices for each document.
 * This class is used for both array and weighted set types.
 *
 * B: EnumAttribute<BaseClass>
 * M: MultiValueType (MultiValueMapping template argument)
 */
template <typename B, typename M>
class MultiValueNumericEnumAttribute : public MultiValueEnumAttribute<B, M> {
public:
    using T = typename B::BaseClass::BaseType;

protected:
    using DocId = typename B::BaseClass::DocId;
    using EnumHandle = typename B::BaseClass::EnumHandle;
    using EnumIndex = IEnumStore::Index;
    using EnumVector = IEnumStore::EnumVector;
    using LoadedNumericValueT = typename B::BaseClass::LoadedNumericValueT;
    using LoadedVector = typename B::BaseClass::LoadedVector;
    using LoadedVectorR = SequentialReadModifyWriteVector<LoadedNumericValueT>;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using Weighted = typename B::BaseClass::Weighted;
    using WeightedEnum = typename B::BaseClass::WeightedEnum;
    using WeightedFloat = typename B::BaseClass::WeightedFloat;
    using WeightedIndex = typename MultiValueEnumAttribute<B, M>::MultiValueType;
    using WeightedIndexArrayRef = typename MultiValueEnumAttribute<B, M>::MultiValueArrayRef;
    using WeightedInt = typename B::BaseClass::WeightedInt;
    using largeint_t = typename B::BaseClass::largeint_t;

public:
    MultiValueNumericEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);

    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return T();
        } else {
            return this->_enumStore.get_value(multivalue::get_value_ref(indices[0]).load_acquire());
        }
    }
    largeint_t getInt(DocId doc) const override {
        return static_cast<largeint_t>(get(doc));
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(get(doc));
    }

    template <typename BufferType>
    uint32_t getHelper(DocId doc, BufferType * buffer, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for(uint32_t i = 0, m = std::min(sz, valueCount); i < m; i++) {
            buffer[i] = static_cast<BufferType>(this->_enumStore.get_value(multivalue::get_value_ref(indices[i]).load_acquire()));
        }
        return valueCount;
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }

    template <typename WeightedType, typename ValueType>
    uint32_t getWeightedHelper(DocId doc, WeightedType * buffer, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            buffer[i] = WeightedType(static_cast<ValueType>(this->_enumStore.get_value(multivalue::get_value_ref(indices[i]).load_acquire())), multivalue::get_weight(indices[i]));
        }
        return valueCount;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedInt, largeint_t>(doc, v, sz);
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedFloat, double>(doc, v, sz);
    }

    // Implements attribute::IMultiValueAttribute
    const attribute::IArrayReadView<T>* make_read_view(attribute::IMultiValueAttribute::ArrayTag<T>, vespalib::Stash& stash) const override;
    const attribute::IWeightedSetReadView<T>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<T>, vespalib::Stash& stash) const override;

private:
    using AttributeReader = PrimitiveReader<typename B::LoadedValueType>;
    void loadAllAtOnce(AttributeReader & attrReader, size_t numDocs, size_t numValues);
};

} // namespace search
