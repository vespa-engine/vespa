// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/config-attributes.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_load_memory_calculator.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/tensor/direct_tensor_posting_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <string>

using search::AttributeVector;
using search::CommitParam;
using search::attribute::AttributeHeader;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::PersistentPredicateParams;
using search::tensor::DirectTensorPostingAttribute;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;

namespace proton {

namespace {

AttributesConfig::Attribute build_single_config(const std::string& name, bool fast_search) {
    AttributesConfigBuilder::Attribute builder;
    builder.name = name;
    builder.datatype = AttributesConfig::Attribute::Datatype::INT32;
    builder.collectiontype = AttributesConfig::Attribute::Collectiontype::WEIGHTEDSET;
    builder.fastsearch = fast_search;
    return builder;
}

AttributesConfig build_config(bool fast_search) {
    AttributesConfigBuilder builder;
    builder.attribute.emplace_back(build_single_config("a1", fast_search));
    builder.attribute.emplace_back(build_single_config("a2", fast_search));
    return builder;
}

std::shared_ptr<AttributeVector> build_attribute_vector(const std::string&              name,
                                                        const AttributeConfigInspector& attribute_config_inspector,
                                                        uint32_t                        docs) {
    auto attribute_vector =
        search::AttributeFactory::createAttribute(name, *attribute_config_inspector.get_config(name));
    attribute_vector->addReservedDoc();
    for (uint32_t wanted_doc_id = 1; wanted_doc_id <= docs; ++wanted_doc_id) {
        uint32_t doc_id = 0;
        attribute_vector->addDoc(doc_id);
        CHECK(doc_id == wanted_doc_id);
        attribute_vector->clearDoc(doc_id);
        auto& integer_attribute_vector = dynamic_cast<search::IntegerAttribute&>(*attribute_vector);
        integer_attribute_vector.append(doc_id, 10, 1);
        integer_attribute_vector.append(doc_id, 11, 1);
    }
    attribute_vector->commit(CommitParam::UpdateStats::FORCE);
    return attribute_vector;
}

const std::string sparse_type_spec("tensor(x{})");

Config make_tensor_config(bool label_postings) {
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE, label_postings);
    cfg.setTensorType(ValueType::from_spec(sparse_type_spec));
    cfg.set_tensor_label_postings(label_postings);
    return cfg;
}

AttributeHeader make_tensor_header(std::optional<uint64_t> total_subspaces) {
    AttributeHeader header("my_attr", BasicType::TENSOR, CollectionType::SINGLE,
                           ValueType::from_spec(sparse_type_spec), false, PersistentPredicateParams(), std::nullopt,
                           10, 10, 10, 4096, 1, 0);
    if (total_subspaces.has_value()) {
        header.set_tensor_counts(total_subspaces.value(), total_subspaces.value());
    }
    return header;
}

} // namespace

size_t sample_transient_usage(bool old_fast_search, bool new_fast_search) {
    auto old_config = build_config(old_fast_search);
    auto old_inspector = std::make_shared<AttributeConfigInspector>(old_config);
    auto av1 = build_attribute_vector("a1", *old_inspector, 1);
    EXPECT_EQ(av1->getEnumeratedSave(), old_fast_search);
    auto new_config = build_config(new_fast_search);
    auto new_inspector = std::make_shared<AttributeConfigInspector>(new_config);
    av1->update_config(*new_inspector->get_config("a1"));
    AttributeLoadMemoryCalculator calc;
    return calc(*av1).transient();
}

TEST(AttributeLoadMemoryCalculator, plain_attribute_vector_requires_no_transient_memory_for_load) {
    EXPECT_EQ(0, sample_transient_usage(false, false));
}

TEST(AttributeLoadMemoryCalculator, fast_search_attribute_vector_requires_transient_memory_for_load) {
    EXPECT_EQ(24u, sample_transient_usage(true, true));
}

TEST(AttributeLoadMemoryCalculator,
     fast_search_attribute_vector_requires_more_transient_memory_for_load_from_unenumerated) {
    EXPECT_EQ(40u, sample_transient_usage(false, true));
}

TEST(AttributeLoadMemoryCalculator, tensor_label_postings_require_transient_memory_for_load) {
    auto  attribute_vector = search::AttributeFactory::createAttribute("my_attr", make_tensor_config(true));
    auto& posting_attribute = dynamic_cast<DirectTensorPostingAttribute&>(*attribute_vector);
    attribute_vector->addReservedDoc();
    for (uint32_t wanted_doc_id = 1; wanted_doc_id <= 2; ++wanted_doc_id) {
        uint32_t doc_id = 0;
        attribute_vector->addDoc(doc_id);
        CHECK(doc_id == wanted_doc_id);
    }
    auto spec1 = TensorSpec(sparse_type_spec).add({{"x", "a"}}, 1.0).add({{"x", "b"}}, 2.0);
    auto spec2 = TensorSpec(sparse_type_spec).add({{"x", "b"}}, 3.0);
    posting_attribute.setTensor(1, *vespalib::eval::value_from_spec(spec1, FastValueBuilderFactory::get()));
    posting_attribute.setTensor(2, *vespalib::eval::value_from_spec(spec2, FastValueBuilderFactory::get()));
    attribute_vector->commit(CommitParam::UpdateStats::FORCE);
    AttributeLoadMemoryCalculator calc;
    EXPECT_EQ(3 * 12u, calc(*attribute_vector).transient());
}

TEST(AttributeLoadMemoryCalculator, tensor_label_postings_transient_memory_from_header) {
    AttributeLoadMemoryCalculator calc;
    EXPECT_EQ(DirectTensorPostingAttribute::rebuild_transient_memory(1000),
              calc(make_tensor_header(1000), make_tensor_config(true)).transient());
    EXPECT_EQ(12000u, calc(make_tensor_header(1000), make_tensor_config(true)).transient());
    EXPECT_EQ(4096u, calc(make_tensor_header(1000), make_tensor_config(true)).permanent());
}

TEST(AttributeLoadMemoryCalculator, tensor_without_label_postings_requires_no_transient_memory_for_load) {
    AttributeLoadMemoryCalculator calc;
    EXPECT_EQ(0u, calc(make_tensor_header(1000), make_tensor_config(false)).transient());
}

TEST(AttributeLoadMemoryCalculator, tensor_label_postings_transient_memory_is_unknown_for_old_header) {
    AttributeLoadMemoryCalculator calc;
    EXPECT_EQ(0u, calc(make_tensor_header(std::nullopt), make_tensor_config(true)).transient());
}

} // namespace proton

GTEST_MAIN_RUN_ALL_TESTS()
