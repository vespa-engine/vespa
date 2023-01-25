// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchsummary/docsummary/i_query_term_filter.h>
#include <vespa/vsm/vsm/query_term_filter_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::docsummary::IQueryTermFilter;
using search::docsummary::IQueryTermFilterFactory;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespa::config::search::vsm::VsmfieldsConfigBuilder;
using vespa::config::search::vsm::VsmsummaryConfig;
using vespa::config::search::vsm::VsmsummaryConfigBuilder;
using vsm::QueryTermFilterFactory;

class QueryTermFilterFactoryTest : public testing::Test {
    std::unique_ptr<IQueryTermFilterFactory> _factory;
    VsmfieldsConfigBuilder _fields;
    VsmsummaryConfigBuilder _summary;
protected:
    QueryTermFilterFactoryTest();
    ~QueryTermFilterFactoryTest() override;

    void make_factory() {
        _factory = std::make_unique<QueryTermFilterFactory>(_fields, _summary);
    }

    bool check_view(const vespalib::string& view, const vespalib::string& summary_field) {
        if (!_factory) {
            make_factory();
        }
        auto query_term_filter = _factory->make(summary_field);
        return query_term_filter->use_view(view);
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


QueryTermFilterFactoryTest::QueryTermFilterFactoryTest()
    : testing::Test(),
      _factory()
{
}

QueryTermFilterFactoryTest::~QueryTermFilterFactoryTest() = default;

TEST_F(QueryTermFilterFactoryTest, empty_config)
{
    EXPECT_FALSE(check_view("foo", "foo"));
}

TEST_F(QueryTermFilterFactoryTest, implied_identity_mapping_for_summary_field)
{
    add_index("foo", {"bar"});
    EXPECT_FALSE(check_view("foo", "foo"));
    EXPECT_TRUE(check_view("foo", "bar"));
}

TEST_F(QueryTermFilterFactoryTest, two_source_fields_for_summary_field)
{
    add_index("bar", {"bar"});
    add_index("baz", {"baz"});
    add_summary_field("foo", {"bar", "baz"});
    EXPECT_FALSE(check_view("foo", "foo"));
    EXPECT_TRUE(check_view("bar", "foo"));
    EXPECT_TRUE(check_view("bar", "bar"));
    EXPECT_TRUE(check_view("baz", "foo"));
    EXPECT_TRUE(check_view("baz", "baz"));
}

TEST_F(QueryTermFilterFactoryTest, two_source_fields_for_summary_field_and_multiple_indexes)
{
    add_index("bar", {"bar"});
    add_index("baz", {"baz"});
    add_index("both", {"bar", "baz"});
    add_index("default", {"baz"});
    add_summary_field("foo", {"bar", "baz"});
    EXPECT_FALSE(check_view("foo", "foo"));
    EXPECT_TRUE(check_view("both", "foo"));
    EXPECT_TRUE(check_view("bar", "foo"));
    EXPECT_TRUE(check_view("baz", "foo"));
    EXPECT_TRUE(check_view("default", "foo"));
    EXPECT_TRUE(check_view("", "foo"));
    EXPECT_TRUE(check_view("both", "bar"));
    EXPECT_TRUE(check_view("bar", "bar"));
    EXPECT_FALSE(check_view("baz", "bar"));
    EXPECT_FALSE(check_view("default", "bar"));
    EXPECT_FALSE(check_view("", "bar"));
    EXPECT_TRUE(check_view("both", "baz"));
    EXPECT_FALSE(check_view("bar", "baz"));
    EXPECT_TRUE(check_view("baz", "baz"));
    EXPECT_TRUE(check_view("default", "baz"));
    EXPECT_TRUE(check_view("", "baz"));
}

GTEST_MAIN_RUN_ALL_TESTS()
