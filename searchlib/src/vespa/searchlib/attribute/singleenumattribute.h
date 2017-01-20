// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/common/rcuvector.h>

namespace search {

class ReaderBase;
/*
 * Implementation of single value enum attribute that uses an underlying enum store
 * to store unique values.
 *
 * B: EnumAttribute<BaseClass>
 */

class SingleValueEnumAttributeBase
{
protected:
    typedef EnumStoreBase::Index      EnumIndex;
    typedef search::attribute::RcuVectorBase<EnumIndex> EnumIndexVector;
    typedef AttributeVector::DocId        DocId;
    typedef AttributeVector::EnumHandle   EnumHandle;
    typedef vespalib::GenerationHolder GenerationHolder;

public:
    using EnumIndexCopyVector = vespalib::Array<EnumIndex>;

    EnumStoreBase::Index getEnumIndex(DocId docId) const { return _enumIndices[docId]; }
    EnumHandle getE(DocId doc) const { return _enumIndices[doc].ref(); }
protected:
    SingleValueEnumAttributeBase(const attribute::Config & c, GenerationHolder &genHolder);
    ~SingleValueEnumAttributeBase();
    AttributeVector::DocId addDoc(bool & incGeneration);

    EnumIndexVector _enumIndices;

    EnumIndexCopyVector getIndicesCopy(uint32_t size) const;
};

template <typename B>
class SingleValueEnumAttribute : public B, public SingleValueEnumAttributeBase
{
protected:
    typedef typename B::DocId                   DocId;
    typedef typename B::WeightedEnum            WeightedEnum;
    typedef typename B::Change                  Change;
    typedef typename B::ChangeVector            ChangeVector;
    typedef typename B::ChangeVector::const_iterator  ChangeVectorIterator;
    typedef typename B::generation_t            generation_t;
    typedef typename B::EnumModifier            EnumModifier;
    typedef typename B::ValueModifier           ValueModifier;
    typedef typename B::EnumStore               EnumStore;
    typedef typename B::LoadedVector            LoadedVector;
    typedef typename B::UniqueSet               UniqueSet;
    typedef attribute::LoadedEnumAttributeVector LoadedEnumAttributeVector;
    typedef attribute::LoadedEnumAttribute LoadedEnumAttribute;
    using B::getGenerationHolder;

private:
    void considerUpdateAttributeChange(const Change & c, UniqueSet & newUniques);
    void applyUpdateValueChange(const Change & c, EnumStoreBase::IndexVector & unused);

protected:
    // from EnumAttribute
    void considerAttributeChange(const Change & c, UniqueSet & newUniques) override;
    void reEnumerate() override;

    // implemented by single value numeric enum attribute.
    virtual void considerUpdateAttributeChange(const Change & c) { (void) c; }
    virtual void considerArithmeticAttributeChange(const Change & c, UniqueSet & newUniques) { (void) c; (void) newUniques; }

    // update enum index vector with new values according to change vector
    virtual void applyValueChanges(EnumStoreBase::IndexVector & unused) ;
    virtual void applyArithmeticValueChange(const Change & c, EnumStoreBase::IndexVector & unused) {
        (void) c; (void) unused;
    }
    void updateEnumRefCounts(const Change & c, EnumIndex newIdx, EnumIndex oldIdx, EnumStoreBase::IndexVector & unused);

    virtual void freezeEnumDictionary() {
        this->getEnumStore().freezeTree();
    }

    virtual void mergeMemoryStats(MemoryUsage & total) { (void) total; }
    virtual void fillValues(LoadedVector & loaded);

    void fillEnumIdx(ReaderBase &attrReader,
                     const EnumStoreBase::IndexVector &eidxs,
                     LoadedEnumAttributeVector &loaded) override;
    
    void fillEnumIdx(ReaderBase &attrReader,
                     const EnumStoreBase::IndexVector &eidxs,
                     EnumStoreBase::EnumVector &enumHist) override;
    
    /**
     * Called when a new document has been added.
     *
     * Can be overridden by subclasses that need to resize structures
     * as a result of this.
     *
     * Should return true if underlying structures were resized.
     **/
    virtual bool onAddDoc(DocId doc) { (void) doc; return false; }

public:
    SingleValueEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~SingleValueEnumAttribute();

    bool addDoc(DocId & doc) override;
    uint32_t getValueCount(DocId doc) const override;
    void onCommit() override;
    void onUpdateStat() override;
    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;
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

    void clearDocs(DocId lidLow, DocId lidLimit) override;
    void onShrinkLidSpace() override;
    std::unique_ptr<AttributeSaver> onInitSave() override;
    void onAddDocs(DocId lidLimit) override;
};

} // namespace search

