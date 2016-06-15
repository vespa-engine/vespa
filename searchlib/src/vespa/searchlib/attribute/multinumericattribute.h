// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/multivaluemapping.h>
#include <vespa/searchlib/attribute/multivalueattribute.h>
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/query/query.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <limits>
#include <string>

namespace search {

/*
 * Implementation of multi value numeric attribute that uses an underlying
 * multi value mapping from MultiValueAttribute.
 *
 * B: Base class
 * M: MultiValueType (MultiValueMapping template argument)
 */
template <typename B, typename M>
class MultiValueNumericAttribute : public MultiValueAttribute<B, M>
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

    typedef typename MultiValueAttribute<B, M>::MultiValueMapping MultiValueMapping;
    typedef typename MultiValueAttribute<B, M>::DocumentValues    DocumentValues;
    typedef typename MultiValueAttribute<B, M>::Change            Change;
    typedef typename MultiValueAttribute<B, M>::ValueType         MValueType; // = B::BaseType
    typedef typename MultiValueAttribute<B, M>::MultiValueType    MultiValueType; // = B::BaseType

    virtual bool extractChangeData(const Change & c, MValueType & data) {
        data = static_cast<MValueType>(c._data.get());
        return true;
    }

    virtual T getFromEnum(EnumHandle e) const;
    virtual bool findEnum(T value, EnumHandle & e) const;
    virtual void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const {
        (void) v;
        (void) e;
        (void) sz;
    }


protected:
    typedef typename B::generation_t generation_t;
    typedef MultiValueType WType;
    uint32_t get(DocId doc, const WType * & values) const { return this->_mvMapping.get(doc, values); }

public:
    virtual uint32_t getRawValues(DocId doc, const WType * & values) const { return get(doc, values); }
    /*
     * Specialization of SearchContext for weighted set type
     */
    class SetSearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    private:
        const MultiValueNumericAttribute<B, M> & _toBeSearched;

        virtual bool
        onCmp(DocId docId, int32_t & weight) const
        {
            return cmp(docId, weight);
        }

        virtual bool
        onCmp(DocId docId) const
        {
            return cmp(docId);
        }

        virtual bool valid() const { return this->isValid(); }

    public:
        SetSearchContext(QueryTermSimple::UP qTerm, const NumericAttribute & toBeSearched) :
            NumericAttribute::Range<T>(*qTerm),
            AttributeVector::SearchContext(toBeSearched),
            _toBeSearched(static_cast<const MultiValueNumericAttribute<B, M> &>(toBeSearched))
        {
        }

        virtual Int64Range getAsIntegerTerm() const {
            return this->getRange();
        }

        bool
        cmp(DocId doc, int32_t & weight) const
        {
            const MultiValueType * buffer;
            for (uint32_t i = 0, m = _toBeSearched._mvMapping.get(doc, buffer);
                 i < m; i++) {
                T v(buffer[i].value());
                if (this->match(v)) {
                    weight = buffer[i].weight();
                    return true;
                }
            }
            return false;
        }

        bool
        cmp(DocId doc) const
        {
            const MultiValueType * buffer;
            for (uint32_t i = 0, m = _toBeSearched._mvMapping.get(doc, buffer);
                 i < m; i++) {
                T v(buffer[i].value());
                if (this->match(v)) {
                    return true;
                }
            }
            return false;
        }

        virtual std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
        {
            if (!valid()) {
                return queryeval::SearchIterator::UP(
                        new queryeval::EmptySearch());
            }
            if (getIsFilter()) {
                return queryeval::SearchIterator::UP
                    (strict
                     ? new FilterAttributeIteratorStrict<SetSearchContext>(*this, matchData)
                 : new FilterAttributeIteratorT<SetSearchContext>(*this, matchData));
            }
            return queryeval::SearchIterator::UP
                (strict
                 ? new AttributeIteratorStrict<SetSearchContext>(*this, matchData)
                 : new AttributeIteratorT<SetSearchContext>(*this, matchData));
        }
    };

    /*
     * Specialization of SearchContext for array type
     */
    class ArraySearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    private:
        const MultiValueNumericAttribute<B, M> & _toBeSearched;

        virtual bool
        onCmp(DocId docId, int32_t & weight) const
        {
            return cmp(docId, weight);
        }

        virtual bool
        onCmp(DocId docId) const
        {
            return cmp(docId);
        }

    protected:
        virtual bool valid() const { return this->isValid(); }

    public:
        ArraySearchContext(QueryTermSimple::UP qTerm, const NumericAttribute & toBeSearched) :
            NumericAttribute::Range<T>(*qTerm),
            AttributeVector::SearchContext(toBeSearched),
            _toBeSearched(static_cast<const MultiValueNumericAttribute<B, M> &>(toBeSearched))
        {
        }

        bool
        cmp(DocId doc, int32_t & weight) const
        {
            uint32_t hitCount = 0;
            const MultiValueType * buffer;
            for (uint32_t i = 0, m = _toBeSearched._mvMapping.get(doc, buffer);
                 i < m; i++) {
                T v = buffer[i].value();
                if (this->match(v)) {
                    hitCount++;
                }
            }
            weight = hitCount;

            return hitCount != 0;
        }

        bool
        cmp(DocId doc) const
        {
            const MultiValueType * buffer;
            for (uint32_t i = 0, m = _toBeSearched._mvMapping.get(doc, buffer);
                 i < m; i++) {
                T v = buffer[i].value();
                if (this->match(v)) {
                    return true;
                }
            }
            return false;
        }

        virtual Int64Range getAsIntegerTerm() const {
            return this->getRange();
        }

        virtual std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict)
        {
            if (!valid()) {
                return queryeval::SearchIterator::UP(
                        new queryeval::EmptySearch());
            }
            if (getIsFilter()) {
                return queryeval::SearchIterator::UP
                    (strict
                     ? new FilterAttributeIteratorStrict<ArraySearchContext>(*this, matchData)
                     : new FilterAttributeIteratorT<ArraySearchContext>(*this, matchData));
            }
            return queryeval::SearchIterator::UP
                (strict
                 ? new AttributeIteratorStrict<ArraySearchContext>(*this, matchData)
                 : new AttributeIteratorT<ArraySearchContext>(*this, matchData));
        }
    };

    MultiValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c =
                               AttributeVector::Config(AttributeVector::BasicType::fromType(T()),
                                                       attribute::CollectionType::ARRAY));
    virtual uint32_t getValueCount(DocId doc) const;
    virtual void onCommit();
    virtual void onUpdateStat();
    virtual void removeOldGenerations(generation_t firstUsed);

    virtual void onGenerationChange(generation_t generation);

    virtual bool onLoad();

    virtual bool
    onLoadEnumerated(typename B::ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimple::UP term, const AttributeVector::SearchContext::Params & params) const override;

    virtual void clearOldValues(DocId doc);
    virtual void setNewValues(DocId doc, const std::vector<WType> & values);

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    virtual T get(DocId doc) const {
        MultiValueType value;
        this->_mvMapping.get(doc, 0, value);
        return value;
    }
    virtual largeint_t getInt(DocId doc) const {
        MultiValueType value;
        this->_mvMapping.get(doc, 0, value);
        return static_cast<largeint_t>(value.value());
    }
    virtual double getFloat(DocId doc) const {
        MultiValueType value;
        this->_mvMapping.get(doc, 0, value);
        return static_cast<double>(value.value());
    }
    virtual EnumHandle getEnum(DocId doc) const {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    virtual uint32_t getAll(DocId doc, T * v, uint32_t sz) const {
        return getHelper(doc, v, sz);
    }
    virtual uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const {
        return getHelper(doc, v, sz);
    }
    virtual uint32_t get(DocId doc, double * v, uint32_t sz) const {
        return getHelper(doc, v, sz);
    }
    template <typename BufferType>
    uint32_t getHelper(DocId doc, BufferType * buffer, uint32_t sz) const {
        const MultiValueType * handle;
        uint32_t ret = this->_mvMapping.get(doc, handle);
        for(size_t i(0), m(std::min(sz, ret)); i < m; i++) {
            buffer[i] = static_cast<BufferType>(handle[i].value());
        }
        return ret;
    }
    virtual uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const {
        return getEnumHelper(doc, e, sz);
    }
    virtual uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const {
        return getEnumHelper(doc, e, sz);
    }
    template <typename E>
    uint32_t getEnumHelper(DocId doc, E * e, uint32_t sz) const {
        uint32_t available = getValueCount(doc);
        uint32_t num2Read = std::min(available, sz);
        for (uint32_t i = 0; i < num2Read; ++i) {
            e[i] = E(std::numeric_limits<uint32_t>::max()); // does not have enum
        }
        return available;
    }
    virtual uint32_t getAll(DocId doc, Weighted * v, uint32_t sz)      const {
        return getWeightedHelper<Weighted, T>(doc, v, sz);
    }
    virtual uint32_t get(DocId doc, WeightedInt * v, uint32_t sz)      const {
        return getWeightedHelper<WeightedInt, largeint_t>(doc, v, sz);
    }
    virtual uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz)    const {
        return getWeightedHelper<WeightedFloat, double>(doc, v, sz);
    }
    template <typename WeightedType, typename ValueType>
    uint32_t getWeightedHelper(DocId doc, WeightedType * buffer, uint32_t sz) const {
        const MultiValueType * handle;
        uint32_t ret = this->_mvMapping.get(doc, handle);
        for(size_t i(0), m(std::min(sz, ret)); i < m; i++) {
            buffer[i] = WeightedType(static_cast<ValueType>(handle[i].value()),
                                     handle[i].weight());
        }
        return ret;
    }

    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
};

}

