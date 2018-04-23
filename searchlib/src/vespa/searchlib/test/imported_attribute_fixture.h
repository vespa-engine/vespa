// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "mock_gid_to_lid_mapping.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <algorithm>
#include <future>
#include <map>
#include <memory>
#include <vector>

namespace search {

struct MockDocumentMetaStoreContext : public IDocumentMetaStoreContext {

    struct MockReadGuard : public IDocumentMetaStoreContext::IReadGuard {
        virtual const search::IDocumentMetaStore &get() const override {
            search::IDocumentMetaStore *nullStore = nullptr;
            return static_cast<search::IDocumentMetaStore &>(*nullStore);
        }
    };

    mutable size_t get_read_guard_cnt;

    using SP = std::shared_ptr<MockDocumentMetaStoreContext>;
    MockDocumentMetaStoreContext() : get_read_guard_cnt(0) {}

    virtual IReadGuard::UP getReadGuard() const override {
        ++get_read_guard_cnt;
        return std::make_unique<MockReadGuard>();
    }
};

namespace attribute {

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

MockDocumentMetaStoreContext::SP create_document_meta_store() {
    return std::make_shared<MockDocumentMetaStoreContext>();
}

enum class FastSearchConfig {
    ExplicitlyEnabled,
    Default
};

enum class FilterConfig {
    ExplicitlyEnabled,
    Default
};

template<typename AttrVecType>
std::shared_ptr<AttrVecType> create_typed_attribute(BasicType basic_type,
                                                    CollectionType collection_type,
                                                    FastSearchConfig fast_search = FastSearchConfig::Default,
                                                    FilterConfig filter = FilterConfig::Default,
                                                    vespalib::stringref name = "parent") {
    Config cfg(basic_type, collection_type);
    if (fast_search == FastSearchConfig::ExplicitlyEnabled) {
        cfg.setFastSearch(true);
    }
    if (filter == FilterConfig::ExplicitlyEnabled) {
        cfg.setIsFilter(true);
    }
    return std::dynamic_pointer_cast<AttrVecType>(
            AttributeFactory::createAttribute(name, std::move(cfg)));
}

template<typename AttrVecType>
std::shared_ptr<AttrVecType> create_single_attribute(BasicType type,
                                                     FastSearchConfig fast_search = FastSearchConfig::Default,
                                                     FilterConfig filter = FilterConfig::Default,
                                                     vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::SINGLE, fast_search, filter, name);
}

template<typename AttrVecType>
std::shared_ptr<AttrVecType> create_array_attribute(BasicType type, vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::ARRAY,
                                               FastSearchConfig::Default, FilterConfig::Default, name);
}

template<typename AttrVecType>
std::shared_ptr<AttrVecType> create_wset_attribute(BasicType type,
                                                   FastSearchConfig fast_search = FastSearchConfig::Default,
                                                   vespalib::stringref name = "parent") {
    return create_typed_attribute<AttrVecType>(type, CollectionType::WSET, fast_search, FilterConfig::Default, name);
}

template<typename AttrVecType>
std::shared_ptr<AttrVecType> create_tensor_attribute(const vespalib::eval::ValueType &tensorType,
                                                     vespalib::stringref name = "parent") {
    Config cfg(BasicType::Type::TENSOR, CollectionType::Type::SINGLE);
    cfg.setTensorType(tensorType);
    return std::dynamic_pointer_cast<AttrVecType>(
            AttributeFactory::createAttribute(name, std::move(cfg)));
}

template<typename VectorType>
void add_n_docs_with_undefined_values(VectorType &vec, size_t n) {
    vec.addDocs(n);
    vec.commit();
}

GlobalId dummy_gid(uint32_t doc_index) {
    return DocumentId(vespalib::make_string("id:foo:bar::%u", doc_index)).getGlobalId();
}

std::unique_ptr<QueryTermSimple> word_term(vespalib::stringref term) {
    return std::make_unique<QueryTermSimple>(term, QueryTerm::WORD);
}

struct ReadGuardWrapper {
    std::unique_ptr<AttributeReadGuard> guard;
    ReadGuardWrapper(std::unique_ptr<AttributeReadGuard> guard_) : guard(std::move(guard_)) {}
    const IAttributeVector *operator->() const { return guard->operator->(); }
    const IAttributeVector &operator*() const { return guard->operator*(); }
};

struct ImportedAttributeFixture {
    bool use_search_cache;
    std::shared_ptr<AttributeVector> target_attr;
    std::shared_ptr<ReferenceAttribute> reference_attr;
    MockDocumentMetaStoreContext::SP document_meta_store;
    std::shared_ptr<ImportedAttributeVector> imported_attr;
    std::shared_ptr<MockGidToLidMapperFactory> mapper_factory;

    ImportedAttributeFixture(bool use_search_cache_ = false);

    virtual ~ImportedAttributeFixture();

    ReadGuardWrapper get_imported_attr() const {
        return ReadGuardWrapper(imported_attr->makeReadGuard(false));
    }

    void map_reference(DocId from_lid, GlobalId via_gid, DocId to_lid) {
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

    static vespalib::stringref default_imported_attr_name() {
        return "imported";
    }

    std::shared_ptr<ImportedAttributeVector>
    create_attribute_vector_from_members(vespalib::stringref name = default_imported_attr_name()) {
        return ImportedAttributeVectorFactory::create(name, reference_attr, target_attr, document_meta_store, use_search_cache);
    }

    template<typename AttrVecType>
    std::shared_ptr<AttrVecType> target_attr_as() {
        auto ptr = std::dynamic_pointer_cast<AttrVecType>(target_attr);
        assert(ptr.get() != nullptr);
        return ptr;
    }

    void reset_with_new_target_attr(std::shared_ptr<AttributeVector> new_target) {
        target_attr = std::move(new_target);
        imported_attr = create_attribute_vector_from_members();
    }

    template<typename ValueType>
    struct LidToLidMapping {
        DocId _from_lid;
        GlobalId _via_gid;
        DocId _to_lid;
        ValueType _value_in_target_attr;

        LidToLidMapping(DocId from_lid,
                        GlobalId via_gid,
                        DocId to_lid,
                        ValueType value_in_target_attr)
                : _from_lid(from_lid),
                  _via_gid(via_gid),
                  _to_lid(to_lid),
                  _value_in_target_attr(std::move(value_in_target_attr)) {}
    };

    void set_up_attribute_vectors_before_adding_mappings() {
        // Make a sneaky assumption that no tests try to use a lid > 9
        add_n_docs_with_undefined_values(*reference_attr, 10);
        target_attr->addReservedDoc();
        add_n_docs_with_undefined_values(*target_attr, 10);
    }

    template<typename AttrVecType, typename MappingsType, typename ValueAssigner>
    void set_up_and_map(const MappingsType &mappings, ValueAssigner assigner) {
        set_up_attribute_vectors_before_adding_mappings();
        auto subtyped_target = target_attr_as<AttrVecType>();
        for (auto &m : mappings) {
            map_reference(m._from_lid, m._via_gid, m._to_lid);
            assigner(*subtyped_target, m);
        }
        subtyped_target->commit();
    }

    template<typename AttrVecType, typename ValueType>
    void reset_with_single_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<ValueType>> &mappings,
            FastSearchConfig fast_search = FastSearchConfig::Default,
            FilterConfig filter = FilterConfig::Default) {
        reset_with_new_target_attr(create_single_attribute<AttrVecType>(type, fast_search, filter));
        // Fun experiment: rename `auto& mapping` to `auto& m` and watch GCC howl about
        // shadowing a variable... that exists in the set_up_and_map function!
        set_up_and_map<AttrVecType>(mappings, [this](auto &target_vec, auto &mapping) {
            ASSERT_TRUE(target_vec.update(mapping._to_lid, mapping._value_in_target_attr));
        });
    }

    template<typename AttrVecType, typename ValueType>
    void reset_with_array_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<ValueType>>> &mappings) {
        reset_with_new_target_attr(create_array_attribute<AttrVecType>(type));
        set_up_and_map<AttrVecType>(mappings, [this](auto &target_vec, auto &mapping) {
            constexpr uint32_t weight = 1;
            for (const auto &v : mapping._value_in_target_attr) {
                ASSERT_TRUE(target_vec.append(mapping._to_lid, v, weight));
            }
        });
    }

    template<typename AttrVecType, typename WeightedValueType>
    void reset_with_wset_value_reference_mappings(
            BasicType type,
            const std::vector<LidToLidMapping<std::vector<WeightedValueType>>> &mappings,
            FastSearchConfig fast_search = FastSearchConfig::Default) {
        reset_with_new_target_attr(create_wset_attribute<AttrVecType>(type, fast_search));
        set_up_and_map<AttrVecType>(mappings, [this](auto &target_vec, auto &mapping) {
            for (const auto &v : mapping._value_in_target_attr) {
                ASSERT_TRUE(target_vec.append(mapping._to_lid, v.value(), v.weight()));
            }
        });
    }

    template<typename AttrVecType, typename ValueType>
    void reset_with_tensor_reference_mappings(const vespalib::eval::ValueType &tensorType,
                                              const std::vector<LidToLidMapping<ValueType>> &mappings) {
        reset_with_new_target_attr(create_tensor_attribute<AttrVecType>(tensorType));
        set_up_and_map<AttrVecType>(mappings, [this](auto &target_vec, auto &mapping) {
            target_vec.setTensor(mapping._to_lid, *mapping._value_in_target_attr);
        });
    }
};

ImportedAttributeFixture::ImportedAttributeFixture(bool use_search_cache_)
        : use_search_cache(use_search_cache_),
          target_attr(create_single_attribute<IntegerAttribute>(BasicType::INT32)),
          reference_attr(create_reference_attribute()),
          document_meta_store(create_document_meta_store()),
          imported_attr(create_attribute_vector_from_members()),
          mapper_factory(std::make_shared<MockGidToLidMapperFactory>()) {
    reference_attr->setGidToLidMapperFactory(mapper_factory);
}

ImportedAttributeFixture::~ImportedAttributeFixture() {}

template<typename AttrValueType, typename PredicateType>
void assert_multi_value_matches(const ImportedAttributeFixture &f,
                                DocId lid,
                                const std::vector<AttrValueType> &expected,
                                PredicateType predicate) {
    AttributeContent<AttrValueType> content;
    content.fill(*f.get_imported_attr(), lid);
    EXPECT_EQUAL(expected.size(), content.size());
    std::vector<AttrValueType> actual(content.begin(), content.end());
    EXPECT_TRUE(std::equal(expected.begin(), expected.end(),
                           actual.begin(), actual.end(), predicate));
}

template<typename AttrValueType>
void assert_multi_value_matches(const ImportedAttributeFixture &f,
                                DocId lid,
                                const std::vector<AttrValueType> &expected) {
    assert_multi_value_matches(f, lid, expected, std::equal_to<AttrValueType>());
}

// Simple wrappers to avoid ugly "f.template reset..." syntax.
template<typename AttrVecType, typename ValueType>
void reset_with_single_value_reference_mappings(
        ImportedAttributeFixture &f,
        BasicType type,
        const std::vector<ImportedAttributeFixture::LidToLidMapping<ValueType>> &mappings,
        FastSearchConfig fast_search = FastSearchConfig::Default,
        FilterConfig filter = FilterConfig::Default) {
    f.reset_with_single_value_reference_mappings<AttrVecType, ValueType>(type, mappings, fast_search, filter);
}

template<typename AttrVecType, typename ValueType>
void reset_with_array_value_reference_mappings(
        ImportedAttributeFixture &f,
        BasicType type,
        const std::vector<ImportedAttributeFixture::LidToLidMapping<std::vector<ValueType>>> &mappings) {
    f.reset_with_array_value_reference_mappings<AttrVecType, ValueType>(type, mappings);
}

template<typename AttrVecType, typename WeightedValueType>
void reset_with_wset_value_reference_mappings(
        ImportedAttributeFixture &f,
        BasicType type,
        const std::vector<ImportedAttributeFixture::LidToLidMapping<std::vector<WeightedValueType>>> &mappings,
        FastSearchConfig fast_search = FastSearchConfig::Default) {
    f.reset_with_wset_value_reference_mappings<AttrVecType, WeightedValueType>(type, mappings, fast_search);
}

bool has_active_enum_guards(AttributeVector &attr) {
    return std::async(std::launch::async, [&attr] { return attr.hasActiveEnumGuards(); }).get();
}

} // attribute
} // search
