// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attribute_fixture.h"
#include "mock_gid_to_lid_mapping.h"
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <future>

namespace search {

namespace {
    struct MockReadGuard : public IDocumentMetaStoreContext::IReadGuard {
        virtual const search::IDocumentMetaStore &get() const override {
            search::IDocumentMetaStore *nullStore = nullptr;
            return static_cast<search::IDocumentMetaStore &>(*nullStore);
        }
    };
}

IDocumentMetaStoreContext::IReadGuard::UP
MockDocumentMetaStoreContext::getReadGuard() const {
    ++get_read_guard_cnt;
    return std::make_unique<MockReadGuard>();
}

}

namespace search::attribute {

using document::DocumentId;
using document::GlobalId;
using DocId             = IAttributeVector::DocId;
using WeightedInt       = IAttributeVector::WeightedInt;
using WeightedFloat     = IAttributeVector::WeightedFloat;
using WeightedString    = IAttributeVector::WeightedString;
using WeightedConstChar = IAttributeVector::WeightedConstChar;
using WeightedEnum      = IAttributeVector::WeightedEnum;
using test::MockGidToLidMapperFactory;

std::shared_ptr<ReferenceAttribute> create_reference_attribute(vespalib::stringref name = "ref") {
    return std::make_shared<ReferenceAttribute>(name, Config(BasicType::REFERENCE));
}

std::shared_ptr<MockDocumentMetaStoreContext>
create_target_document_meta_store() {
    return std::make_shared<MockDocumentMetaStoreContext>();
}

std::shared_ptr<MockDocumentMetaStoreContext>
create_document_meta_store() {
    return std::make_shared<MockDocumentMetaStoreContext>();
}

GlobalId dummy_gid(uint32_t doc_index) {
    return DocumentId(vespalib::make_string("id:foo:bar::%u", doc_index)).getGlobalId();
}

std::unique_ptr<QueryTermSimple> word_term(vespalib::stringref term) {
    return std::make_unique<QueryTermUCS4>(term, QueryTermSimple::Type::WORD);
}


void
ImportedAttributeFixture::map_reference(DocId from_lid, GlobalId via_gid, DocId to_lid) {
    assert(from_lid < reference_attr->getNumDocs());
    mapper_factory->_map[via_gid] = to_lid;
    if (to_lid != 0) {
        reference_attr->notifyReferencedPut(via_gid, to_lid);
    } else {
        reference_attr->notifyReferencedRemove(via_gid);
    }
    reference_attr->update(from_lid, via_gid);
    reference_attr->commit();
}


std::shared_ptr<ImportedAttributeVector>
ImportedAttributeFixture::create_attribute_vector_from_members(vespalib::stringref name) {
    return ImportedAttributeVectorFactory::create(name, reference_attr, document_meta_store, target_attr, target_document_meta_store, use_search_cache);
}


void
ImportedAttributeFixture::reset_with_new_target_attr(std::shared_ptr<AttributeVector> new_target) {
    target_attr = std::move(new_target);
    imported_attr = create_attribute_vector_from_members();
}


void
ImportedAttributeFixture::set_up_attribute_vectors_before_adding_mappings() {
    // Make a sneaky assumption that no tests try to use a lid > 9
    add_n_docs_with_undefined_values(*reference_attr, 10);
    target_attr->addReservedDoc();
    add_n_docs_with_undefined_values(*target_attr, 10);
}

ImportedAttributeFixture::ImportedAttributeFixture(bool use_search_cache_, FastSearchConfig fastSearch)
        : use_search_cache(use_search_cache_),
          target_attr(create_single_attribute<IntegerAttribute>(BasicType::INT32, fastSearch)),
          target_document_meta_store(create_target_document_meta_store()),
          reference_attr(create_reference_attribute()),
          document_meta_store(create_document_meta_store()),
          imported_attr(create_attribute_vector_from_members()),
          mapper_factory(std::make_shared<MockGidToLidMapperFactory>())
{
    reference_attr->setGidToLidMapperFactory(mapper_factory);
}

ImportedAttributeFixture::~ImportedAttributeFixture() = default;

bool has_active_enum_guards(AttributeVector &attr) {
    return std::async(std::launch::async, [&attr] { return attr.hasActiveEnumGuards(); }).get();
}

}
