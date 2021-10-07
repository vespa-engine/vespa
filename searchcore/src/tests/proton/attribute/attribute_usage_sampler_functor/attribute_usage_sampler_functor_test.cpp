// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_context.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_sampler_functor.h>
#include <vespa/searchcore/proton/common/transient_resource_usage_provider.h>
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

class AttributeUsageSamplerFunctorTest : public ::testing::Test {
protected:
    AttributeUsageFilter                          _filter;
    std::shared_ptr<TransientResourceUsageProvider> _transient_usage_provider;
public:
    AttributeUsageSamplerFunctorTest();
    ~AttributeUsageSamplerFunctorTest();
protected:
    void sample_usage(bool sample_a1, bool sample_a2, bool old_fast_search, bool new_fast_search = true);
    size_t get_transient_memory_usage() const { return _transient_usage_provider->get_transient_memory_usage(); }
};

AttributeUsageSamplerFunctorTest::AttributeUsageSamplerFunctorTest()
    : _filter(),
      _transient_usage_provider(std::make_shared<TransientResourceUsageProvider>())
{
}

AttributeUsageSamplerFunctorTest::~AttributeUsageSamplerFunctorTest() = default;

void
AttributeUsageSamplerFunctorTest::sample_usage(bool sample_a1, bool sample_a2, bool old_fast_search, bool new_fast_search)
{
    auto old_config = build_config(old_fast_search);
    auto old_inspector = std::make_shared<AttributeConfigInspector>(old_config);
    auto av1 = build_attribute_vector("a1", *old_inspector, 1);
    auto av2 = build_attribute_vector("a2", *old_inspector, 3);
    EXPECT_EQ(av1->getEnumeratedSave(), old_fast_search);
    auto new_config = build_config(new_fast_search);
    auto new_inspector = std::make_shared<AttributeConfigInspector>(new_config);
    auto context = std::make_shared<AttributeUsageSamplerContext>(_filter, new_inspector, _transient_usage_provider);
    if (sample_a1) {
        AttributeUsageSamplerFunctor functor1(context, "ready");
        functor1(*av1);
    }
    if (sample_a2) {
        AttributeUsageSamplerFunctor functor2(context, "ready");
        functor2(*av2);
    }
}

TEST_F(AttributeUsageSamplerFunctorTest, plain_attribute_vector_requires_no_transient_memory_for_load)
{
    sample_usage(true, true, false, false);
    EXPECT_EQ(0u, get_transient_memory_usage());
}

TEST_F(AttributeUsageSamplerFunctorTest, fast_search_attribute_vector_requires_transient_memory_for_load)
{
    sample_usage(true, false, true, true);
    EXPECT_EQ(24u, get_transient_memory_usage());
}

TEST_F(AttributeUsageSamplerFunctorTest, fast_search_attribute_vector_requires_more_transient_memory_for_load_from_unenumerated)
{
    sample_usage(true, false, false, true);
    EXPECT_EQ(40u, get_transient_memory_usage());
}

TEST_F(AttributeUsageSamplerFunctorTest, transient_memory_aggregation_function_for_attribute_usage_sampler_context_is_max)
{
    sample_usage(true, false, true, true);
    EXPECT_EQ(24u, get_transient_memory_usage());
    sample_usage(false, true, true, true);
    EXPECT_EQ(72u, get_transient_memory_usage());
    sample_usage(true, true, true, true);
    EXPECT_EQ(72u, get_transient_memory_usage());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
