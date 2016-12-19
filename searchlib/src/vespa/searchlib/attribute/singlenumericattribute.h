// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "floatbase.h"
#include "attributeiterators.h"
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/query/query.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
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

    virtual T getFromEnum(EnumHandle e) const {
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

        bool onCmp(DocId docId, int32_t & weight) const override {
            return cmp(docId, weight);
        }

        bool onCmp(DocId docId) const override {
            return cmp(docId);
        }

        bool valid() const override;

    public:
        SingleSearchContext(QueryTermSimple::UP qTerm, const NumericAttribute & toBeSearched);
        bool cmp(DocId docId, int32_t & weight) const {
            const T v = _data[docId];
            weight = 1;
            return this->match(v);
        }

        bool cmp(DocId docId) const {
            const T v = _data[docId];
            return this->match(v);
        }

        Int64Range getAsIntegerTerm() const override;

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };


protected:
    virtual bool findEnum(T value, EnumHandle & e) const {
        (void) value; (void) e;
        return false;
    }

public:
    SingleValueNumericAttribute(const vespalib::string & baseFileName,
                                const AttributeVector::Config & c =
                                AttributeVector::Config(AttributeVector::
                                        BasicType::fromType(T()),
                                        attribute::CollectionType::SINGLE));


    virtual
    ~SingleValueNumericAttribute(void);

    virtual uint32_t getValueCount(DocId doc) const {
        if (doc >= B::getNumDocs()) {
            return 0;
        }
        return 1;
    }
    virtual void onCommit();
    virtual void onUpdateStat();
    virtual void removeOldGenerations(generation_t firstUsed);
    virtual void onGenerationChange(generation_t generation);
    virtual bool addDoc(DocId & doc) {
        bool incGen = _data.isFull();
        _data.push_back(attribute::getUndefined<T>());
        std::atomic_thread_fence(std::memory_order_release);
        B::incNumDocs();
        doc = B::getNumDocs() - 1;
        this->updateUncommittedDocIdLimit(doc);
        if (incGen) {
            this->incGeneration();
        } else
            this->removeAllOldGenerations();
        return true;
    }
    virtual bool onLoad();

    bool onLoadEnumerated(ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimple::UP term, const AttributeVector::SearchContext::Params & params) const override;

    void set(DocId doc, T v) {
        _data[doc] = v;
    }

    T getFast(DocId doc) const {
        return _data[doc];
    }

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    virtual T get(DocId doc) const {
        return getFast(doc);
    }
    virtual largeint_t getInt(DocId doc) const {
        return static_cast<largeint_t>(getFast(doc));
    }
    virtual void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const {
        (void) v;
        (void) e;
        (void) sz;
    }
    virtual double getFloat(DocId doc) const {
        return static_cast<double>(_data[doc]);
    }
    virtual uint32_t getEnum(DocId doc) const {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    virtual uint32_t getAll(DocId doc, T * v, uint32_t sz) const {
        (void) sz;
        v[0] = _data[doc];
        return 1;
    }
    virtual uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const {
        (void) sz;
        v[0] = static_cast<largeint_t>(_data[doc]);
        return 1;
    }
    virtual uint32_t get(DocId doc, double * v, uint32_t sz) const {
        (void) sz;
        v[0] = static_cast<double>(_data[doc]);
        return 1;
    }
    virtual uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const {
        (void) sz;
        e[0] = getEnum(doc);
        return 1;
    }
    virtual uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const {
        (void) doc; (void) v; (void) sz;
        return 0;
    }
    virtual uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const {
        (void) sz;
        v[0] = WeightedInt(static_cast<largeint_t>(_data[doc]));
        return 1;
    }
    virtual uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const {
        (void) sz;
        v[0] = WeightedFloat(static_cast<double>(_data[doc]));
        return 1;
    }
    virtual uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const {
        (void) doc; (void) e; (void) sz;
        return 0;
    }

    virtual void
    clearDocs(DocId lidLow, DocId lidLimit);

    virtual void
    onShrinkLidSpace();

    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
};

}

