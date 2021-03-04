// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/attribute/singleenumattribute.h>
#include "enumhintsearchcontext.h"

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
    using EnumHintSearchContext = attribute::EnumHintSearchContext;
    using EnumIndex = typename SingleValueEnumAttributeBase::EnumIndex;
    using EnumIndexVector = typename SingleValueEnumAttributeBase::EnumIndexVector;
    using EnumStore = typename SingleValueEnumAttribute<B>::EnumStore;
    using LoadedVector = StringAttribute::LoadedVector;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SearchContext = StringAttribute::SearchContext;
    using ValueModifier = StringAttribute::ValueModifier;
    using WeightedConstChar = StringAttribute::WeightedConstChar;
    using WeightedEnum = StringAttribute::WeightedEnum;
    using WeightedString = StringAttribute::WeightedString;
    using generation_t = StringAttribute::generation_t;

public:
    SingleValueStringAttributeT(const vespalib::string & name, const AttributeVector::Config & c =
                                AttributeVector::Config(AttributeVector::BasicType::STRING));
    ~SingleValueStringAttributeT();

    void freezeEnumDictionary() override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    bool isUndefined(DocId doc) const override { return get(doc)[0] == '\0'; }
    const char * get(DocId doc) const override {
        return this->_enumStore.get_value(this->_enumIndices[doc]);
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

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    class StringSingleImplSearchContext : public StringAttribute::StringSearchContext {
    public:
        StringSingleImplSearchContext(QueryTermSimpleUP qTerm, const StringAttribute & toBeSearched) :
            StringSearchContext(std::move(qTerm), toBeSearched)
        { }
    protected:
        int32_t onFind(DocId doc, int32_t elemId, int32_t &weight) const override {
            weight = 1;
            return onFind(doc, elemId);
        }

        int32_t onFind(DocId doc, int32_t elemId) const override {
            if ( elemId != 0) return -1;
            const SingleValueStringAttributeT<B> & attr(static_cast<const SingleValueStringAttributeT<B> &>(attribute()));
            return isMatch(attr._enumStore.get_value(attr._enumIndices[doc])) ? 0 : -1;
        }

    };

    class StringTemplSearchContext : public StringSingleImplSearchContext,
                                     public EnumHintSearchContext
    {
        using AttrType = SingleValueStringAttributeT<B>;
        using StringSingleImplSearchContext::queryTerm;
    public:
        StringTemplSearchContext(QueryTermSimpleUP qTerm, const AttrType & toBeSearched);
    };
};

using SingleValueStringAttribute = SingleValueStringAttributeT<EnumAttribute<StringAttribute> >;

} // namespace search

