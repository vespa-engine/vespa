// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "stringbase.h"
#include "enumattribute.h"
#include "enumstore.h"
#include "multienumattribute.h"
#include "multi_value_mapping.h"
#include <vespa/searchcommon/attribute/multivalue.h>

namespace search {

/**
 * Implementation of multi value string attribute that uses an underlying enum store
 * to store unique string values and a multi value mapping to store the enum store indices
 * for each document.
 * This class is used for both array and weighted set types.
 *
 * B: Base class: EnumAttribute<StringAttribute>
 * M: IEnumStore::Index (array) or
 *    multivalue::WeightedValue<IEnumStore::Index> (weighted set)
 */
template <typename B, typename M>
class MultiValueStringAttributeT : public MultiValueEnumAttribute<B, M> {
protected:
    using DocIndices = typename MultiValueAttribute<B, M>::DocumentValues;
    using EnumIndex = typename MultiValueAttribute<B, M>::ValueType;
    using EnumStore = typename B::EnumStore;
    using MultiValueMapping = typename MultiValueAttribute<B, M>::MultiValueMapping;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using WeightedIndex = typename MultiValueAttribute<B, M>::MultiValueType;
    using WeightedIndexArrayRef = typename MultiValueAttribute<B, M>::MultiValueArrayRef;

    using Change = StringAttribute::Change;
    using ChangeVector = StringAttribute::ChangeVector;
    using DocId = StringAttribute::DocId;
    using EnumHandle = StringAttribute::EnumHandle;
    using LoadedVector = StringAttribute::LoadedVector;
    using ValueModifier = StringAttribute::ValueModifier;
    using WeightedConstChar = StringAttribute::WeightedConstChar;
    using WeightedEnum = StringAttribute::WeightedEnum;
    using WeightedString = StringAttribute::WeightedString;
    using generation_t = StringAttribute::generation_t;

public:
    MultiValueStringAttributeT(const vespalib::string & name, const AttributeVector::Config & c);
    MultiValueStringAttributeT(const vespalib::string & name);
    ~MultiValueStringAttributeT();

    void freezeEnumDictionary() override;

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    const char * get(DocId doc) const  override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return NULL;
        } else {
            return this->_enumStore.get_value(multivalue::get_value_ref(indices[0]).load_acquire());
        }
    }

    std::vector<EnumHandle> findFoldedEnums(const char *value) const override {
        return this->_enumStore.find_folded_enums(value);
    }

    const char * getStringFromEnum(EnumHandle e) const override {
        return this->_enumStore.get_value(e);
    }
    template <typename BufferType>
    uint32_t getHelper(DocId doc, BufferType * buffer, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for(uint32_t i = 0, m = std::min(sz, valueCount); i < m; i++) {
            buffer[i] = this->_enumStore.get_value(multivalue::get_value_ref(indices[i]).load_acquire());
        }
        return valueCount;
    }
    uint32_t get(DocId doc, vespalib::string * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }
    uint32_t get(DocId doc, const char ** v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }

    /// Weighted interface
    template <typename WeightedType>
    uint32_t getWeightedHelper(DocId doc, WeightedType * buffer, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            buffer[i] = WeightedType(this->_enumStore.get_value(multivalue::get_value_ref(indices[i]).load_acquire()), multivalue::get_weight(indices[i]));
        }
        return valueCount;
    }
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const override {
        return getWeightedHelper(doc, v, sz);
    }
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override {
        return getWeightedHelper(doc, v, sz);
    }

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    // Implements attribute::IMultiValueAttribute
    const attribute::IArrayReadView<const char*>* make_read_view(attribute::IMultiValueAttribute::ArrayTag<const char*>, vespalib::Stash& stash) const override;
    const attribute::IWeightedSetReadView<const char*>* make_read_view(attribute::IMultiValueAttribute::WeightedSetTag<const char*>, vespalib::Stash& stash) const override;
};


using ArrayStringAttribute = MultiValueStringAttributeT<EnumAttribute<StringAttribute>, vespalib::datastore::AtomicEntryRef>;
using WeightedSetStringAttribute = MultiValueStringAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef> >;

}
