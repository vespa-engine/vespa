// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping.h"
#include "attributevector.h"

namespace search {

/*
 * Implementation of multi value attribute using an underlying multi value mapping
 *
 * B: Base class
 * M: MultiValueType
 */
template <typename B, typename M>
class MultiValueAttribute : public B
{
protected:
    typedef typename B::DocId                             DocId;
    typedef typename B::Change                            Change;
    typedef typename B::ChangeVector                      ChangeVector;
    typedef typename B::ChangeVector::const_iterator      ChangeVectorIterator;

    using MultiValueType = M;
    using MultiValueMapping = attribute::MultiValueMapping<MultiValueType>;
    typedef typename MultiValueType::ValueType            ValueType;
    typedef std::vector<MultiValueType>                   ValueVector;
    using MultiValueArrayRef = vespalib::ConstArrayRef<MultiValueType>;
    typedef typename ValueVector::iterator                ValueVectorIterator;
    typedef std::vector<std::pair<DocId, ValueVector> >   DocumentValues;

    MultiValueMapping _mvMapping;

    MultiValueMapping &       getMultiValueMapping()       { return _mvMapping; }
    const MultiValueMapping & getMultiValueMapping() const { return _mvMapping; }

    /*
     * Iterate through the change vector and calculate new values for documents with changes
     */
    void applyAttributeChanges(DocumentValues & docValues);

    virtual bool extractChangeData(const Change & c, ValueType & data) = 0;

    /**
     * Called when a new document has been added.
     * Can be overridden by subclasses that need to resize structures as a result of this.
     * Should return true if underlying structures were resized.
     **/
    bool onAddDoc(DocId doc) override { (void) doc; return false; }

    vespalib::AddressSpace getMultiValueAddressSpaceUsage() const override;

public:
    MultiValueAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~MultiValueAttribute() override;

    bool addDoc(DocId & doc) override;
    uint32_t getValueCount(DocId doc) const override;
    const attribute::MultiValueMappingBase *getMultiValueBase() const override {
        return &getMultiValueMapping();
    }

private:
    int32_t getWeight(DocId doc, uint32_t idx) const override;

    uint64_t getTotalValueCount() const override;

    void apply_attribute_changes_to_array(DocumentValues& docValues);
    void apply_attribute_changes_to_wset(DocumentValues& docValues);

public:
    void clearDocs(DocId lidLow, DocId lidLimit) override;
    void onShrinkLidSpace() override ;
    void onAddDocs(DocId lidLimit) override;
};

} // namespace search
