// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "floatbase.h"
#include "multivalueattribute.h"
#include "search_context.h"
#include <vespa/searchcommon/attribute/config.h>
#include <limits>

namespace search {

/*
 * Implementation of multi value numeric attribute that uses an underlying
 * multi value mapping from MultiValueAttribute.
 *
 * B: Base class
 * M: MultiValueType
 */
template <typename B, typename M>
class MultiValueNumericAttribute : public MultiValueAttribute<B, M> {
private:
    using T = typename B::BaseType;
    using DocId = typename B::DocId;
    using EnumHandle = typename B::EnumHandle;
    using Weighted = typename B::Weighted;
    using WeightedEnum = typename B::WeightedEnum;
    using WeightedFloat = typename B::WeightedFloat;
    using WeightedInt = typename B::WeightedInt;
    using largeint_t = typename B::largeint_t;

    using Change = typename MultiValueAttribute<B, M>::Change;
    using DocumentValues = typename MultiValueAttribute<B, M>::DocumentValues;
    using MValueType = typename MultiValueAttribute<B, M>::ValueType; // = B::BaseType
    using MultiValueArrayRef = typename MultiValueAttribute<B, M>::MultiValueArrayRef;
    using MultiValueMapping = typename MultiValueAttribute<B, M>::MultiValueMapping;
    using MultiValueType = typename MultiValueAttribute<B, M>::MultiValueType; // = B::BaseType

    bool extractChangeData(const Change & c, MValueType & data) override {
        data = static_cast<MValueType>(c._data.get());
        return true;
    }

    T getFromEnum(EnumHandle e) const override;
    bool findEnum(T value, EnumHandle & e) const override;

protected:
    using generation_t = typename B::generation_t;
    using WType = MultiValueType;
    uint32_t get(DocId doc, const WType * & values) const {
        MultiValueArrayRef array(this->_mvMapping.get(doc));
        values = array.data();
        return array.size();
    }

public:
    MultiValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c =
                               AttributeVector::Config(AttributeVector::BasicType::fromType(T()),
                                                       attribute::CollectionType::ARRAY));
    uint32_t getValueCount(DocId doc) const override;
    void onCommit() override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;

    void before_inc_generation(generation_t current_gen) override;
    bool onLoad(vespalib::Executor *executor) override;
    virtual bool onLoadEnumerated(ReaderBase &attrReader);

    std::unique_ptr<attribute::SearchContext>
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    virtual void clearOldValues(DocId doc);
    virtual void setNewValues(DocId doc, const std::vector<WType> & values);

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        MultiValueArrayRef values(this->_mvMapping.get(doc));
        return ((values.size() > 0) ? multivalue::get_value(values[0]) : T());
    }
    largeint_t getInt(DocId doc) const override {
        MultiValueArrayRef values(this->_mvMapping.get(doc));
        return static_cast<largeint_t>((values.size() > 0) ? multivalue::get_value(values[0]) : T());
    }
    double getFloat(DocId doc) const override {
        MultiValueArrayRef values(this->_mvMapping.get(doc));
        return static_cast<double>((values.size() > 0) ? multivalue::get_value(values[0]) : T());
    }
    EnumHandle getEnum(DocId doc) const override {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        return getHelper(doc, v, sz);
    }
    template <typename BufferType>
    uint32_t getHelper(DocId doc, BufferType * buffer, uint32_t sz) const {
        MultiValueArrayRef handle(this->_mvMapping.get(doc));
        uint32_t ret = handle.size();
        for(size_t i(0), m(std::min(sz, ret)); i < m; i++) {
            buffer[i] = static_cast<BufferType>(multivalue::get_value(handle[i]));
        }
        return ret;
    }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        return getEnumHelper(doc, e, sz);
    }
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        return getEnumHelper(doc, e, sz);
    }
    template <typename E>
    uint32_t getEnumHelper(DocId doc, E * e, uint32_t sz) const {
        MultiValueArrayRef values(this->_mvMapping.get(doc));
        uint32_t available = values.size();
        uint32_t num2Read = std::min(available, sz);
        for (uint32_t i = 0; i < num2Read; ++i) {
            e[i] = E(std::numeric_limits<uint32_t>::max()); // does not have enum
        }
        return available;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedInt, largeint_t>(doc, v, sz);
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        return getWeightedHelper<WeightedFloat, double>(doc, v, sz);
    }
    template <typename WeightedType, typename ValueType>
    uint32_t getWeightedHelper(DocId doc, WeightedType * buffer, uint32_t sz) const {
        MultiValueArrayRef handle(this->_mvMapping.get(doc));
        uint32_t ret = handle.size();
        for(size_t i(0), m(std::min(sz, ret)); i < m; i++) {
            buffer[i] = WeightedType(static_cast<ValueType>(multivalue::get_value(handle[i])),
                                     multivalue::get_weight(handle[i]));
        }
        return ret;
    }

    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
};

}
