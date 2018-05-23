// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/attribute/singleenumattribute.h>
#include "enumhintsearchcontext.h"

namespace search {

/*
 * Implementation of single value string attribute that uses an underlying enum store
 * to store unique string values.
 *
 * B: EnumAttribute<StringAttribute>
 */
template <typename B>
class SingleValueStringAttributeT : public SingleValueEnumAttribute<B>
{
protected:
    typedef StringAttribute::DocId             DocId;
    typedef StringAttribute::EnumHandle        EnumHandle;
    typedef StringAttribute::generation_t      generation_t;
    typedef StringAttribute::WeightedString    WeightedString;
    typedef StringAttribute::WeightedConstChar WeightedConstChar;
    typedef StringAttribute::WeightedEnum      WeightedEnum;
    typedef StringAttribute::SearchContext     SearchContext;
    typedef StringAttribute::ChangeVector      ChangeVector;
    typedef StringAttribute::Change            Change;
    typedef StringAttribute::ValueModifier     ValueModifier;
    typedef StringAttribute::EnumModifier      EnumModifier;
    typedef StringAttribute::LoadedVector      LoadedVector;

    typedef typename SingleValueEnumAttribute<B>::EnumStore        EnumStore;
    typedef typename SingleValueEnumAttributeBase::EnumIndex       EnumIndex;
    typedef typename SingleValueEnumAttributeBase::EnumIndexVector EnumIndexVector;
    typedef attribute::EnumHintSearchContext    EnumHintSearchContext;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

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
        return this->_enumStore.getValue(this->_enumIndices[doc]);
    }
    const char * getStringFromEnum(EnumHandle e) const override {
        return this->_enumStore.getValue(e);
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
            return isMatch(attr._enumStore.getValue(attr._enumIndices[doc])) ? 0 : -1;
        }

    };

    class StringTemplSearchContext : public StringSingleImplSearchContext,
                                     public EnumHintSearchContext
    {
        using StringSingleImplSearchContext::queryTerm;
        typedef SingleValueStringAttributeT<B> AttrType;
        typedef typename EnumStore::FoldedComparatorType FoldedComparatorType;
    public:
        StringTemplSearchContext(QueryTermSimpleUP qTerm, const AttrType & toBeSearched);
    };
};

typedef SingleValueStringAttributeT<EnumAttribute<StringAttribute> > SingleValueStringAttribute;

} // namespace search

