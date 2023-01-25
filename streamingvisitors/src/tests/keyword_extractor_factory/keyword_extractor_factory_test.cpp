// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchsummary/docsummary/i_keyword_extractor.h>
#include <vespa/vsm/vsm/keyword_extractor_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::docsummary::IKeywordExtractor;
using search::docsummary::IKeywordExtractorFactory;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmfieldsConfigBuilder;
using vespa::config::search::vsm::VsmsummaryConfig;
using vespa::config::search::vsm::VsmsummaryConfigBuilder;
using vsm::KeywordExtractorFactory;

class KeywordExtractorFactoryTest : public testing::Test {
    std::unique_ptr<IKeywordExtractorFactory> _factory;
    VsmfieldsConfigBuilder _fields;
    VsmsummaryConfigBuilder _summary;
protected:
    KeywordExtractorFactoryTest();
    ~KeywordExtractorFactoryTest() override;

    void make_factory() {
        _factory = std::make_unique<KeywordExtractorFactory>(_fields, _summary);
    }

    bool check_index(const vespalib::string &index_name, const vespalib::string& summary_field) {
        if (!_factory) {
            make_factory();
        }
        auto extractor = _factory->make(summary_field);
        return extractor->isLegalIndex(index_name);
    }

    void add_summary_field(const vespalib::string& summary_field_name, const std::vector<vespalib::string>& field_names)
    {
        VsmsummaryConfigBuilder::Fieldmap field_map;
        field_map.summary = summary_field_name;
        for (auto& field_name : field_names) {
            VsmsummaryConfigBuilder::Fieldmap::Document document;
            document.field = field_name;
            field_map.document.emplace_back(document);
        }
        _summary.fieldmap.emplace_back(field_map);
        _factory.reset();
    }
    void add_index(const vespalib::string& index_name, const std::vector<vespalib::string>& field_names)
    {
        if (_fields.documenttype.empty()) {
            _fields.documenttype.resize(1);
            _fields.documenttype.back().name = "dummy";
        }
        VsmfieldsConfigBuilder::Documenttype::Index index;
        index.name = index_name;
        for (auto& field_name : field_names) {
            VsmfieldsConfigBuilder::Documenttype::Index::Field field;
            field.name = field_name;
            index.field.emplace_back(field);
        }
        _fields.documenttype.back().index.emplace_back(index);
        _factory.reset();
    }
};


KeywordExtractorFactoryTest::KeywordExtractorFactoryTest()
    : testing::Test(),
      _factory()
{
}

KeywordExtractorFactoryTest::~KeywordExtractorFactoryTest() = default;

TEST_F(KeywordExtractorFactoryTest, empty_config)
{
    EXPECT_FALSE(check_index("foo", "foo"));
}

TEST_F(KeywordExtractorFactoryTest, implied_identity_mapping_for_summary_field)
{
    add_index("foo", {"bar"});
    EXPECT_FALSE(check_index("foo", "foo"));
    EXPECT_TRUE(check_index("foo", "bar"));
}

TEST_F(KeywordExtractorFactoryTest, two_source_fields_for_summary_field)
{
    add_index("bar", {"bar"});
    add_index("baz", {"baz"});
    add_summary_field("foo", {"bar", "baz"});
    EXPECT_FALSE(check_index("foo", "foo"));
    EXPECT_TRUE(check_index("bar", "foo"));
    EXPECT_TRUE(check_index("bar", "bar"));
    EXPECT_TRUE(check_index("baz", "foo"));
    EXPECT_TRUE(check_index("baz", "baz"));
}

TEST_F(KeywordExtractorFactoryTest, two_source_fields_for_summary_field_and_multiple_indexes)
{
    add_index("bar", {"bar"});
    add_index("baz", {"baz"});
    add_index("both", {"bar", "baz"});
    add_index("default", {"baz"});
    add_summary_field("foo", {"bar", "baz"});
    EXPECT_FALSE(check_index("foo", "foo"));
    EXPECT_TRUE(check_index("both", "foo"));
    EXPECT_TRUE(check_index("bar", "foo"));
    EXPECT_TRUE(check_index("baz", "foo"));
    EXPECT_TRUE(check_index("default", "foo"));
    EXPECT_TRUE(check_index("", "foo"));
    EXPECT_TRUE(check_index("both", "bar"));
    EXPECT_TRUE(check_index("bar", "bar"));
    EXPECT_FALSE(check_index("baz", "bar"));
    EXPECT_FALSE(check_index("default", "bar"));
    EXPECT_FALSE(check_index("", "bar"));
    EXPECT_TRUE(check_index("both", "baz"));
    EXPECT_FALSE(check_index("bar", "baz"));
    EXPECT_TRUE(check_index("baz", "baz"));
    EXPECT_TRUE(check_index("default", "baz"));
    EXPECT_TRUE(check_index("", "baz"));
}

GTEST_MAIN_RUN_ALL_TESTS()
