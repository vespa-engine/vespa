// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_transient_memory_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/string.h>

using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using search::AttributeVector;
using search::attribute::Config;

namespace proton {

namespace {

AttributesConfig::Attribute build_single_config(const vespalib::string& name, bool fast_search)
{
    AttributesConfigBuilder::Attribute builder;
    builder.name = name;
    builder.datatype = AttributesConfig::Attribute::Datatype::INT32;
    builder.collectiontype = AttributesConfig::Attribute::Collectiontype::WEIGHTEDSET;
    builder.fastsearch = fast_search;
    return builder;
}

AttributesConfig build_config(bool fast_search)
{
    AttributesConfigBuilder builder;
    builder.attribute.emplace_back(build_single_config("a1", fast_search));
    builder.attribute.emplace_back(build_single_config("a2", fast_search));
    return builder;
}

std::shared_ptr<AttributeVector> build_attribute_vector(const vespalib::string& name, const AttributeConfigInspector& attribute_config_inspector, uint32_t docs)
{
    auto attribute_vector = search::AttributeFactory::createAttribute(name, *attribute_config_inspector.get_config(name));
    attribute_vector->addReservedDoc();
    for (uint32_t wanted_doc_id = 1; wanted_doc_id <= docs; ++wanted_doc_id) {
        uint32_t doc_id = 0;
        attribute_vector->addDoc(doc_id);
        assert(doc_id == wanted_doc_id);
        attribute_vector->clearDoc(doc_id);
        auto &integer_attribute_vector = dynamic_cast<search::IntegerAttribute &>(*attribute_vector);
        integer_attribute_vector.append(doc_id, 10, 1);
        integer_attribute_vector.append(doc_id, 11, 1);
    }
    attribute_vector->commit(true);
    return attribute_vector;
}

}

size_t
sample_usage(bool old_fast_search, bool new_fast_search)
{
    auto old_config = build_config(old_fast_search);
    auto old_inspector = std::make_shared<AttributeConfigInspector>(old_config);
    auto av1 = build_attribute_vector("a1", *old_inspector, 1);
    EXPECT_EQ(av1->getEnumeratedSave(), old_fast_search);
    auto new_config = build_config(new_fast_search);
    auto new_inspector = std::make_shared<AttributeConfigInspector>(new_config);
    AttributeTransientMemoryCalculator calc;
    return calc(*av1, *new_inspector->get_config("a1"));
}

TEST(AttributeTransientMemoryCalculator, plain_attribute_vector_requires_no_transient_memory_for_load)
{
    EXPECT_EQ(0, sample_usage(false, false));
}

TEST(AttributeTransientMemoryCalculator, fast_search_attribute_vector_requires_transient_memory_for_load)
{
    EXPECT_EQ(24u, sample_usage(true, true));
}

TEST(AttributeTransientMemoryCalculator, fast_search_attribute_vector_requires_more_transient_memory_for_load_from_unenumerated)
{
    EXPECT_EQ(40u, sample_usage(false, true));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
