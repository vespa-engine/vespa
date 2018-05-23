// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multienumattribute.h"
#include "numericbase.h"
#include "primitivereader.h"

namespace search {

/*
 * Implementation of multi value numeric attribute that uses an underlying enum store
 * to store unique numeric values and a multi value mapping to store enum indices for each document.
 * This class is used for both array and weighted set types.
 *
 * B: EnumAttribute<BaseClass>
 * M: MultiValueType (MultiValueMapping template argument)
 */
template <typename B, typename M>
class MultiValueNumericEnumAttribute : public MultiValueEnumAttribute<B, M>
{
protected:
    typedef typename B::BaseClass::DocId           DocId;
    typedef typename B::BaseClass::EnumHandle      EnumHandle;
public:
    typedef typename B::BaseClass::BaseType        T;
protected:
    typedef typename B::BaseClass::largeint_t      largeint_t;
    typedef typename B::BaseClass::LoadedNumericValueT LoadedNumericValueT;
    typedef typename B::BaseClass::LoadedVector    LoadedVector;
    typedef SequentialReadModifyWriteVector<LoadedNumericValueT> LoadedVectorR;
    typedef typename B::BaseClass::Weighted        Weighted;
    typedef typename B::BaseClass::WeightedInt     WeightedInt;
    typedef typename B::BaseClass::WeightedFloat   WeightedFloat;
    typedef typename B::BaseClass::WeightedEnum    WeightedEnum;

    typedef typename MultiValueEnumAttribute<B, M>::MultiValueType WeightedIndex;
    using WeightedIndexArrayRef = typename MultiValueEnumAttribute<B, M>::MultiValueArrayRef;
    typedef attribute::LoadedEnumAttribute         LoadedEnumAttribute;
    typedef attribute::LoadedEnumAttributeVector   LoadedEnumAttributeVector;
    typedef EnumStoreBase::IndexVector             EnumIndexVector;
    typedef EnumStoreBase::EnumVector              EnumVector;
    typedef EnumStoreBase::Index                   EnumIndex;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

protected:
    /*
     * Specialization of SearchContext for weighted set type
     */
    class SetSearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    protected:
        const MultiValueNumericEnumAttribute<B, M> & _toBeSearched;

        int32_t onCmp(DocId docId, int32_t elemId, int32_t & weight) const override {
            return find(docId, elemId, weight);
        }

        int32_t onCmp(DocId docId, int32_t elemId) const override {
            return find(docId, elemId);
        }

        bool valid() const override { return this->isValid(); }

    public:
        SetSearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched);

        int32_t
        find(DocId doc, int32_t elemId, int32_t & weight) const
        {
            WeightedIndexArrayRef indices(_toBeSearched._mvMapping.get(doc));
            for (uint32_t i(elemId); i < indices.size(); i++) {
                T v = _toBeSearched._enumStore.getValue(indices[i].value());
                if (this->match(v)) {
                    weight = indices[i].weight();
                    return i;
                }
            }
            return -1;
        }

        int32_t
        find(DocId doc, int32_t elemId) const
        {
            WeightedIndexArrayRef indices(_toBeSearched._mvMapping.get(doc));
            for (uint32_t i(elemId); i < indices.size(); i++) {
                T v = _toBeSearched._enumStore.getValue(indices[i].value());
                if (this->match(v)) {
                    return i;
                }
            }
            return -1;
        }
        Int64Range getAsIntegerTerm() const override;

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };

    /*
     * Specialization of SearchContext for array type
     */
    class ArraySearchContext : public NumericAttribute::Range<T>, public AttributeVector::SearchContext
    {
    protected:
        const MultiValueNumericEnumAttribute<B, M> & _toBeSearched;

        int32_t onCmp(DocId docId, int32_t elemId, int32_t & weight) const override {
            return find(docId, elemId, weight);
        }

        int32_t onCmp(DocId docId, int32_t elemId) const override {
            return find(docId, elemId);
        }

        bool valid() const override { return this->isValid(); }

    public:
        ArraySearchContext(QueryTermSimpleUP qTerm, const NumericAttribute & toBeSearched);
        Int64Range getAsIntegerTerm() const override;

        int32_t
        find(DocId doc, int32_t elemId, int32_t & weight) const
        {
            WeightedIndexArrayRef indices(_toBeSearched._mvMapping.get(doc));
            for (uint32_t i(elemId); i < indices.size(); i++) {
                T v = _toBeSearched._enumStore.getValue(indices[i].value());
                if (this->match(v)) {
                    weight = 1;
                    return i;
                }
            }
            weight = 0;

            return -1;
        }

        bool
        find(DocId doc, int32_t elemId) const
        {
            WeightedIndexArrayRef indices(_toBeSearched._mvMapping.get(doc));
            for (uint32_t i(elemId); i < indices.size(); i++) {
                T v = _toBeSearched._enumStore.getValue(indices[i].value());
                if (this->match(v)) {
                    return i;
                }
            }

            return -1;
        }

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };


public:
    MultiValueNumericEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);

    bool onLoad() override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    //-------------------------------------------------------------------------
    // Attribute read API
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return T();
        } else {
            return this->_enumStore.getValue(indices[0].value());
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
            buffer[i] = static_cast<BufferType>(this->_enumStore.getValue(indices[i].value()));
        }
        return valueCount;
    }
    uint32_t getAll(DocId doc, T * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
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
            buffer[i] = WeightedType(static_cast<ValueType>(this->_enumStore.getValue(indices[i].value())), indices[i].weight());
        }
        return valueCount;
    }
    uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const override {
        return getWeightedHelper<Weighted, T>(doc, v, sz);
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedInt, largeint_t>(doc, v, sz);
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedFloat, double>(doc, v, sz);
    }

private:
    using AttributeReader = PrimitiveReader<typename B::LoadedValueType>;
    void loadAllAtOnce(AttributeReader & attrReader, size_t numDocs, size_t numValues);
};

} // namespace search
