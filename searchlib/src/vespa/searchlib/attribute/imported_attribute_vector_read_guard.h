// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_read_guard.h"
#include "attributeguard.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::attribute {

class BitVectorSearchCache;
class ImportedAttributeVector;
class ReferenceAttribute;

/*
 * Short lived attribute vector that does not store values on its own.
 *
 * Read guards are held on
 * - target attribute, to ensure that reads are safe.
 * - target document meta store, to avoid target lids being reused.
 * - reference attribute, to ensure that access to lid mapping is safe.
 *
 * Extra information for direct lid to target lid mapping with
 * boundary check is setup during construction.
 */
class ImportedAttributeVectorReadGuard : public IAttributeVector,
                                         public AttributeReadGuard,
                                         public IMultiValueAttribute
{
public:
    using MetaStoreReadGuard = search::IDocumentMetaStoreContext::IReadGuard;
    ImportedAttributeVectorReadGuard(std::shared_ptr<MetaStoreReadGuard> targetMetaStoreReadGuard, const ImportedAttributeVector &imported_attribute, bool stableEnumGuard);
    ~ImportedAttributeVectorReadGuard() override;

    const vespalib::string &getName() const override;
    uint32_t getNumDocs() const override;
    uint32_t getValueCount(uint32_t doc) const override;
    uint32_t getMaxValueCount() const override;
    largeint_t getInt(DocId doc) const override;
    double getFloat(DocId doc) const override;
    const char *getString(DocId doc, char *buffer, size_t sz) const override;
    EnumHandle getEnum(DocId doc) const override;
    uint32_t get(DocId docId, largeint_t *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, double *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, const char **buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, EnumHandle *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedInt *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedFloat *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedString *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedConstChar *buffer, uint32_t sz) const override;
    uint32_t get(DocId docId, WeightedEnum *buffer, uint32_t sz) const override;
    bool findEnum(const char * value, EnumHandle & e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;

    const char * getStringFromEnum(EnumHandle e) const override;
    std::unique_ptr<ISearchContext> createSearchContext(std::unique_ptr<QueryTermSimple> term,
                                                        const SearchContextParams &params) const override;
    const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;
    const tensor::ITensorAttribute *asTensorAttribute() const override;
    const attribute::IMultiValueAttribute* as_multi_value_attribute() const override;
    BasicType::Type getBasicType() const override;
    size_t getFixedWidth() const override;
    CollectionType::Type getCollectionType() const override;
    bool hasEnum() const override;
    bool getIsFilter() const override;
    bool getIsFastSearch() const override;
    uint32_t getCommittedDocIdLimit() const override;
    bool isImported() const override;
    bool isUndefined(DocId doc) const override;
    template <typename MultiValueType>
    const IMultiValueReadView<MultiValueType>* make_read_view_helper(MultiValueTag<MultiValueType> tag, vespalib::Stash& stash) const;
    const IArrayReadView<int8_t>* make_read_view(ArrayTag<int8_t> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<int16_t>* make_read_view(ArrayTag<int16_t> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<int32_t>* make_read_view(ArrayTag<int32_t> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<int64_t>* make_read_view(ArrayTag<int64_t> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<float>* make_read_view(ArrayTag<float> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<double>* make_read_view(ArrayTag<double> tag, vespalib::Stash& stash) const override;
    const IArrayReadView<const char*>* make_read_view(ArrayTag<const char*> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<int8_t>* make_read_view(WeightedSetTag<int8_t> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<int16_t>* make_read_view(WeightedSetTag<int16_t> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<int32_t>* make_read_view(WeightedSetTag<int32_t> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<int64_t>* make_read_view(WeightedSetTag<int64_t> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<float>* make_read_view(WeightedSetTag<float> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<double>* make_read_view(WeightedSetTag<double> tag, vespalib::Stash& stash) const override;
    const IWeightedSetReadView<const char*>* make_read_view(WeightedSetTag<const char*> tag, vespalib::Stash& stash) const override;
    const IArrayEnumReadView* make_read_view(ArrayEnumTag tag, vespalib::Stash& stash) const override;
    const IWeightedSetEnumReadView* make_read_view(WeightedSetEnumTag tag, vespalib::Stash& stash) const override;
private:
    using AtomicTargetLid = vespalib::datastore::AtomicValueWrapper<uint32_t>;
    using TargetLids = vespalib::ConstArrayRef<AtomicTargetLid>;
    std::shared_ptr<MetaStoreReadGuard>  _target_document_meta_store_read_guard;
    const ImportedAttributeVector       &_imported_attribute;
    TargetLids                           _targetLids;
    AttributeGuard                       _reference_attribute_guard;
    std::unique_ptr<attribute::AttributeReadGuard> _target_attribute_guard;
    const ReferenceAttribute            &_reference_attribute;
protected:
    const IAttributeVector              &_target_attribute;

    uint32_t getTargetLid(uint32_t lid) const {
        // Check range to avoid reading memory beyond end of mapping array
        return lid < _targetLids.size() ? _targetLids[lid].load_acquire() : 0u;
    }

    long onSerializeForAscendingSort(DocId doc, void * serTo, long available,
                                     const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available,
                                      const common::BlobConverter * bc) const override;
};

}
