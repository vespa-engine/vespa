// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"

namespace search {

struct NotImplementedAttribute : AttributeVector {
    using AttributeVector::AttributeVector;

    virtual void notImplemented() const __attribute__((noinline)) {
        assert(false);
        throw vespalib::IllegalStateException(
                "The function is not implemented.");
    }

    virtual uint32_t
    getValueCount(DocId) const
    {
        notImplemented();
        return 0;
    }

    virtual largeint_t
    getInt(DocId) const
    {
        notImplemented();
        return 0;
    }

    virtual double
    getFloat(DocId) const
    {
        notImplemented();
        return 0;
    }

    virtual const char *
    getString(DocId, char *, size_t) const
    {
        notImplemented();
        return NULL;
    }

    virtual uint32_t
    get(DocId, largeint_t *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, double *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, vespalib::string *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, const char **, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, EnumHandle *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, WeightedInt *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, WeightedFloat *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, WeightedString *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, WeightedConstChar *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    get(DocId, WeightedEnum *, uint32_t) const
    {
        notImplemented();
        return 0;
    }

    virtual bool
    findEnum(const char *, EnumHandle &) const
    {
        notImplemented();
        return false;
    }

    virtual long
    onSerializeForAscendingSort(DocId, void *, long,
                                const common::BlobConverter *) const
    {
        notImplemented();
        return 0;
    }

    virtual long
    onSerializeForDescendingSort(DocId, void *, long,
                                 const common::BlobConverter *) const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    clearDoc(DocId)
    {
        notImplemented();
        return 0;
    }

    virtual int64_t
    getDefaultValue() const
    {
        notImplemented();
        return 0;
    }

    virtual uint32_t
    getEnum(DocId) const
    {
        notImplemented();
        return 0;
    }

    virtual void
    getEnumValue(const EnumHandle *, uint32_t *, uint32_t) const
    {
        notImplemented();
    }

    virtual bool
    addDoc(DocId &)
    {
        notImplemented();
        return false;
    }

    SearchContext::UP
    getSearch(QueryTermSimple::UP, const SearchContext::Params &) const override
    {
        notImplemented();
        return SearchContext::UP();
    }
};

}  // namespace search

