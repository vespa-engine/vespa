// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumattribute.h"
#include <vespa/vespalib/util/rcuvector.h>

namespace search {

class ReaderBase;

/**
 * Implementation of single value enum attribute that uses an underlying enum store
 * to store unique values.
 *
 * B: EnumAttribute<BaseClass>
 */
class SingleValueEnumAttributeBase {
protected:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using AtomicEntryRefVector = vespalib::RcuVectorBase<AtomicEntryRef>;
    using DocId = AttributeVector::DocId;
    using EntryRef = vespalib::datastore::EntryRef;
    using EnumHandle = AttributeVector::EnumHandle;
    using EnumIndex = IEnumStore::Index;
    using EnumIndexRemapper = IEnumStore::EnumIndexRemapper;
    using GenerationHolder = vespalib::GenerationHolder;
    using EnumRefs = attribute::IAttributeVector::EnumRefs;
public:
    using EnumIndexCopyVector = vespalib::Array<EnumIndex>;
protected:

    EntryRef acquire_enum_entry_ref(DocId docId) const noexcept { return _enumIndices.acquire_elem_ref(docId).load_acquire(); }
    EnumHandle getE(DocId doc) const noexcept { return acquire_enum_entry_ref(doc).ref(); }
    EnumRefs make_read_view(size_t read_size) const noexcept { return _enumIndices.make_read_view(read_size); }
    SingleValueEnumAttributeBase(const attribute::Config & c, GenerationHolder &genHolder, const vespalib::alloc::Alloc& initial_alloc);
    ~SingleValueEnumAttributeBase();
    AttributeVector::DocId addDoc(bool & incGeneration);

    AtomicEntryRefVector _enumIndices;

    EnumIndexCopyVector getIndicesCopy(uint32_t size) const;
    void remap_enum_store_refs(const EnumIndexRemapper& remapper, AttributeVector& v);
};

template <typename B>
class SingleValueEnumAttribute : public B, public SingleValueEnumAttributeBase {
protected:
    using Change = typename B::Change;
    using ChangeVector = typename B::ChangeVector;
    using DocId = typename B::DocId;
    using EnumStore = typename B::EnumStore;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;
    using LoadedVector = typename B::LoadedVector;
    using ValueModifier = typename B::ValueModifier;
    using WeightedEnum = typename B::WeightedEnum;
    using generation_t = typename B::generation_t;

    using B::getGenerationHolder;

private:
    void considerUpdateAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter);
    void applyUpdateValueChange(const Change& c, EnumStoreBatchUpdater& updater);
    EnumRefs make_enum_read_view() const noexcept override {
        return make_read_view(this->getCommittedDocIdLimit());
    }

protected:
    // from EnumAttribute
    void considerAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter) override;

    // implemented by single value numeric enum attribute.
    virtual void considerUpdateAttributeChange(const Change & c) { (void) c; }
    virtual void considerArithmeticAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter) { (void) c; (void) inserter; }

    virtual void applyValueChanges(EnumStoreBatchUpdater& updater) ;
    virtual void applyArithmeticValueChange(const Change& c, EnumStoreBatchUpdater& updater) {
        (void) c; (void) updater;
    }
    void updateEnumRefCounts(const Change& c, EnumIndex newIdx, EnumIndex oldIdx, EnumStoreBatchUpdater& updater);

    virtual void freezeEnumDictionary() {
        this->getEnumStore().freeze_dictionary();
    }

    virtual void mergeMemoryStats(vespalib::MemoryUsage & total) { (void) total; }

    void fillValues(LoadedVector & loaded) override;

    void load_enumerated_data(ReaderBase& attrReader,
                              enumstore::EnumeratedPostingsLoader& loader, size_t num_values) override;

    void load_enumerated_data(ReaderBase& attrReader,
                              enumstore::EnumeratedLoader& loader) override;

    /**
     * Called when a new document has been added.
     *
     * Can be overridden by subclasses that need to resize structures
     * as a result of this.
     *
     * Should return true if underlying structures were resized.
     **/
    bool onAddDoc(DocId doc) override;

public:
    SingleValueEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~SingleValueEnumAttribute();

    bool addDoc(DocId & doc) override;
    uint32_t getValueCount(DocId doc) const override;
    void onCommit() override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    EnumHandle getEnum(DocId doc) const override {
       return getE(doc);
    }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        if (sz > 0) {
            e[0] = getE(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        if (sz > 0) {
            e[0] = WeightedEnum(getE(doc), 1);
        }
        return 1;
    }

    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    void onAddDocs(DocId lidLimit) override;
};

} // namespace search
