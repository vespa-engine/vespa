// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-summary.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::SummaryManager;
using search::AttributeFactory;
using search::MatchingElementsFields;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::test::MockAttributeManager;
using search::index::Schema;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using vespa::config::search::SummaryConfig;
using vespa::config::search::SummaryConfigBuilder;
using vespa::config::search::summary::JuniperrcConfig;
using SIF = Schema::IndexField;
using Select = SummaryConfigBuilder::Classes::Fields::Elements::Select;

namespace {

SummaryConfig make_summary_config() {
    SummaryConfigBuilder builder;
    builder.defaultsummaryid = 0;
    builder.classes.resize(1);
    auto& cls = builder.classes.back();
    cls.id = 0;
    cls.name = "default";
    for (const char* name : {"people", "url"}) {
        cls.fields.emplace_back();
        cls.fields.back().name = name;
        cls.fields.back().elements.select = Select::BY_MATCH;
    }
    return SummaryConfig(builder);
}

Schema make_schema() {
    Schema s;
    s.addUriIndexFields(SIF("url", DataType::STRING));
    s.addIndexField(SIF("people.name", DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(SIF("people.value.title", DataType::STRING, CollectionType::ARRAY));
    return s;
}

std::shared_ptr<MockAttributeManager> make_attribute_manager() {
    auto mgr = std::make_shared<MockAttributeManager>();
    mgr->addAttribute(AttributeFactory::createAttribute(
        "people.age", Config(BasicType::INT32, search::attribute::CollectionType::ARRAY)));
    return mgr;
}

} // namespace

TEST(SummarySetupTest, matched_elements_only_maps_struct_field_attributes_and_indexes_to_enclosing_field) {
    SummaryManager::SummarySetup setup(".", make_summary_config(), JuniperrcConfig(), make_attribute_manager(), {},
                                       {}, {}, make_schema());
    const auto*                  cls = setup.getResultConfig().lookupResultClass("default");
    ASSERT_NE(nullptr, cls);
    const MatchingElementsFields& fields = *cls->get_matching_elements_fields();
    EXPECT_TRUE(fields.has_field("people"));
    // struct field attribute
    EXPECT_TRUE(fields.has_field("people.age"));
    EXPECT_EQ("people", fields.enclosing_field("people.age"));
    // struct field indexes from the schema, also nested
    EXPECT_TRUE(fields.has_field("people.name"));
    EXPECT_EQ("people", fields.enclosing_field("people.name"));
    EXPECT_TRUE(fields.has_field("people.value.title"));
    EXPECT_EQ("people", fields.enclosing_field("people.value.title"));
    // uri parts are not struct fields
    EXPECT_TRUE(fields.has_field("url"));
    EXPECT_FALSE(fields.has_field("url.host"));
}

GTEST_MAIN_RUN_ALL_TESTS()
