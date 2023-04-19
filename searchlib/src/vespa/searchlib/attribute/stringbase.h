// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "no_loaded_vector.h"
#include "attributevector.h"
#include "i_enum_store.h"
#include "loadedenumvalue.h"
#include "string_search_context.h"

namespace search {

class ReaderBase;

/**
 * Base class for all string attributes.
 */
class StringAttribute : public AttributeVector
{
public:
    using EnumIndex = IEnumStore::Index;
    using EnumVector = IEnumStore::EnumVector;
    using LoadedValueType = const char*;
    using LoadedVector = NoLoadedVector;
    using OffsetVector = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;
public:
    bool append(DocId doc, const vespalib::string & v, int32_t weight) {
        return AttributeVector::append(_changes, doc, StringChangeData(v), weight);
    }
    template<typename Accessor>
    bool append(DocId doc, Accessor & ac) {
        return AttributeVector::append(_changes, doc, ac);
    }
    bool remove(DocId doc, const vespalib::string & v, int32_t weight) {
        return AttributeVector::remove(_changes, doc, StringChangeData(v), weight);
    }
    bool update(DocId doc, const vespalib::string & v) {
        return AttributeVector::update(_changes, doc, StringChangeData(v));
    }
    bool apply(DocId doc, const ArithmeticValueUpdate & op);
    bool applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust) override;
    bool applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust) override;
    bool findEnum(const char * value, EnumHandle & e) const override = 0;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override = 0;
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override;
    uint32_t get(DocId doc, double * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override;
    uint32_t clearDoc(DocId doc) override;
    static size_t countZero(const char * bt, size_t sz);
    virtual const char * getFromEnum(EnumHandle e) const = 0;
    virtual const char *get(DocId doc) const = 0;
    largeint_t getInt(DocId doc)  const override { return strtoll(get(doc), nullptr, 0); }
    double getFloat(DocId doc)    const override;
    vespalib::ConstArrayRef<char> get_raw(DocId) const override;
    static const char * defaultValue() { return ""; }
protected:
    StringAttribute(const vespalib::string & name);
    StringAttribute(const vespalib::string & name, const Config & c);
    ~StringAttribute() override;
    using Change = ChangeTemplate<StringChangeData>;
    using ChangeVector = ChangeVectorT<Change>;
    using EnumEntryType = const char*;
    ChangeVector _changes;
    const Change _defaultValue;
    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    bool onAddDoc(DocId doc) override;

    vespalib::MemoryUsage getChangeVectorMemoryUsage() const override;

    bool get_match_is_cased() const noexcept;
private:
    virtual void load_posting_lists(LoadedVector& loaded);
    virtual void load_enum_store(LoadedVector& loaded);
    virtual void fillValues(LoadedVector & loaded);

    virtual void load_enumerated_data(ReaderBase &attrReader, enumstore::EnumeratedPostingsLoader& loader, size_t num_values);
    virtual void load_enumerated_data(ReaderBase &attrReader, enumstore::EnumeratedLoader& loader);
    virtual void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader);

    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
};

}

