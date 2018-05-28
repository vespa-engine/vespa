// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "floatbase.h"
#include <vespa/searchlib/common/rcuvector.h>
#include <limits>

namespace search {

template <typename B>
class SingleValueNumericAttribute : public B
{
private:
    typedef typename B::BaseType      T;
    typedef typename B::DocId         DocId;
    typedef typename B::EnumHandle    EnumHandle;
    typedef typename B::largeint_t    largeint_t;
    typedef typename B::Weighted      Weighted;
    typedef typename B::WeightedInt   WeightedInt;
    typedef typename B::WeightedFloat WeightedFloat;
    typedef typename B::WeightedEnum  WeightedEnum;
    typedef typename B::generation_t generation_t;
    using B::getGenerationHolder;

    typedef attribute::RcuVectorBase<T> DataVector;
    DataVector _data;

    T getFromEnum(EnumHandle e) const override {
        (void) e;
        return T();
    }

    /*
     * Specialization of SearchContext
     */
    template <typename M>
    class SingleSearchContext : public M, public AttributeVector::SearchContext
    {
    private:
        const T * _data;

        int32_t onFind(DocId docId, int32_t elemId, int32_t & weight) const override {
            return find(docId, elemId, weight);
        }

        int32_t onFind(DocId docId, int elemId) const override {
            return find(docId, elemId);
        }

        bool valid() const override;

    public:
    SingleSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const NumericAttribute & toBeSearched);
        int32_t find(DocId docId, int32_t elemId, int32_t & weight) const {
            if ( elemId != 0) return -1;
            const T v = _data[docId];
            weight = 1;
            return this->match(v) ? 0 : -1;
        }

        int32_t find(DocId docId, int elemId) const {
            if ( elemId != 0) return -1;
            const T v = _data[docId];
            return this->match(v) ? 0 : -1;
        }

        Int64Range getAsIntegerTerm() const override;

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };


protected:
    bool findEnum(T value, EnumHandle & e) const override {
        (void) value; (void) e;
        return false;
    }

public:
    SingleValueNumericAttribute(const vespalib::string & baseFileName,
                                const AttributeVector::Config & c =
                                AttributeVector::Config(AttributeVector::
                                        BasicType::fromType(T()),
                                        attribute::CollectionType::SINGLE));


    ~SingleValueNumericAttribute();

    uint32_t getValueCount(DocId doc) const override {
        if (doc >= B::getNumDocs()) {
            return 0;
        }
        return 1;
    }
    void onCommit() override;
    void onAddDocs(DocId lidLimit) override;
    void onUpdateStat() override;
    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;
    bool addDoc(DocId & doc) override;
    bool onLoad() override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    void set(DocId doc, T v) {
        _data[doc] = v;
    }

    T getFast(DocId doc) const {
        return _data[doc];
    }

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        return getFast(doc);
    }
    largeint_t getInt(DocId doc) const override {
        return static_cast<largeint_t>(getFast(doc));
    }
    void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const override {
        (void) v;
        (void) e;
        (void) sz;
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(_data[doc]);
    }
    uint32_t getEnum(DocId doc) const override {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    uint32_t getAll(DocId doc, T * v, uint32_t sz) const override {
        (void) sz;
        v[0] = _data[doc];
        return 1;
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        (void) sz;
        v[0] = static_cast<largeint_t>(_data[doc]);
        return 1;
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        (void) sz;
        v[0] = static_cast<double>(_data[doc]);
        return 1;
    }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        (void) sz;
        e[0] = getEnum(doc);
        return 1;
    }
    uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const override {
        (void) doc; (void) v; (void) sz;
        return 0;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        (void) sz;
        v[0] = WeightedInt(static_cast<largeint_t>(_data[doc]));
        return 1;
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        (void) sz;
        v[0] = WeightedFloat(static_cast<double>(_data[doc]));
        return 1;
    }
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        (void) doc; (void) e; (void) sz;
        return 0;
    }

    void clearDocs(DocId lidLow, DocId lidLimit) override;
    void onShrinkLidSpace() override;
    std::unique_ptr<AttributeSaver> onInitSave() override;
};

}

