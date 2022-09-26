// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumattribute.h"
#include "singleenumattribute.h"
#include "stringbase.h"

namespace search {

/**
 * Implementation of single value string attribute that uses an underlying enum store
 * to store unique string values.
 *
 * B: EnumAttribute<StringAttribute>
 */
template <typename B>
class SingleValueStringAttributeT : public SingleValueEnumAttribute<B> {
protected:
    using Change = StringAttribute::Change;
    using ChangeVector = StringAttribute::ChangeVector;
    using DocId = StringAttribute::DocId;
    using EnumHandle = StringAttribute::EnumHandle;
    using EnumIndex = typename SingleValueEnumAttributeBase::EnumIndex;
    using EnumStore = typename SingleValueEnumAttribute<B>::EnumStore;
    using LoadedVector = StringAttribute::LoadedVector;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using ValueModifier = StringAttribute::ValueModifier;
    using WeightedConstChar = StringAttribute::WeightedConstChar;
    using WeightedEnum = StringAttribute::WeightedEnum;
    using WeightedString = StringAttribute::WeightedString;
    using generation_t = StringAttribute::generation_t;

public:
    SingleValueStringAttributeT(const vespalib::string & name, const AttributeVector::Config & c);
    SingleValueStringAttributeT(const vespalib::string & name);
    ~SingleValueStringAttributeT();

    void freezeEnumDictionary() override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    bool isUndefined(DocId doc) const override { return get(doc)[0] == '\0'; }
    const char * get(DocId doc) const override {
        return this->_enumStore.get_value(this->acquire_enum_entry_ref(doc));
    }
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override {
        return this->_enumStore.find_folded_enums(value);
    }
    const char * getStringFromEnum(EnumHandle e) const override {
        return this->_enumStore.get_value(e);
    }
    uint32_t get(DocId doc, vespalib::string * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = get(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, const char ** v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = get(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const override{
        if (sz > 0) {
            v[0] = WeightedString(get(doc), 1);
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override{
        if (sz > 0) {
            v[0] = WeightedConstChar(get(doc), 1);
        }
        return 1;
    }

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;
};

using SingleValueStringAttribute = SingleValueStringAttributeT<EnumAttribute<StringAttribute> >;

} // namespace search

