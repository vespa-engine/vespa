// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchsummary/docsummary/resultclass.h>
#include <vespa/searchsummary/docsummary/summary_elements_selector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>

using namespace search::docsummary;

class MockWriter : public DocsumFieldWriter {
private:
    bool _generated;
public:
    MockWriter(bool generated) : _generated(generated) {}
    bool isGenerated() const override { return _generated; }
    void insert_field(uint32_t, const IDocsumStoreDocument*, GetDocsumsState&,
                      const SummaryElementsSelector&,
                      vespalib::slime::Inserter &) const override {}
};

TEST(ResultClassTest, subset_of_fields_in_class_are_generated)
{    
    ResultClass rc("test");
    rc.addConfigEntry("from_disk");
    rc.addConfigEntry("generated", SummaryElementsSelector::select_all(), std::make_unique<MockWriter>(true));
    rc.addConfigEntry("not_generated", SummaryElementsSelector::select_all(), std::make_unique<MockWriter>(false));

    EXPECT_FALSE(rc.all_fields_generated({}));
    EXPECT_FALSE(rc.all_fields_generated({"from_disk", "generated", "not_generated"}));
    EXPECT_FALSE(rc.all_fields_generated({"generated", "not_generated"}));
    EXPECT_TRUE(rc.all_fields_generated({"generated"}));
    EXPECT_FALSE(rc.all_fields_generated({"not_generated"}));
}

TEST(ResultClassTest, all_fields_in_class_are_generated)
{
    ResultClass rc("test");
    rc.addConfigEntry("generated_1", SummaryElementsSelector::select_all(), std::make_unique<MockWriter>(true));
    rc.addConfigEntry("generated_2", SummaryElementsSelector::select_all(), std::make_unique<MockWriter>(true));

    EXPECT_TRUE(rc.all_fields_generated({}));
    EXPECT_TRUE(rc.all_fields_generated({"generated_1"}));
}

GTEST_MAIN_RUN_ALL_TESTS()
