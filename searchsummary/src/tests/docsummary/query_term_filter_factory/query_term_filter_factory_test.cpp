// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchsummary/docsummary/i_query_term_filter.h>
#include <vespa/searchsummary/docsummary/query_term_filter_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::docsummary::IQueryTermFilter;
using search::docsummary::IQueryTermFilterFactory;
using search::docsummary::QueryTermFilterFactory;
using search::index::Schema;

using FieldSet = Schema::FieldSet;

class QueryTermFilterFactoryTest : public testing::Test {
    std::unique_ptr<IQueryTermFilterFactory> _factory;
    Schema _schema;

protected:
    QueryTermFilterFactoryTest();
    ~QueryTermFilterFactoryTest() override;

    void make_factory() {
        _factory = std::make_unique<QueryTermFilterFactory>(_schema);
    }

    bool check_view(const vespalib::string& view, const vespalib::string& summary_field) {
        if (!_factory) {
            make_factory();
        }
        auto query_term_filter = _factory->make(summary_field);
        return query_term_filter->use_view(view);
    }

    void add_field_set(const vespalib::string& field_set_name, const std::vector<vespalib::string>& field_names) {
        FieldSet field_set(field_set_name);
        for (auto& field_name : field_names) {
            field_set.addField(field_name);
        }
        _schema.addFieldSet(field_set);
        _factory.reset();
    }
};


QueryTermFilterFactoryTest::QueryTermFilterFactoryTest()
    : testing::Test(),
      _factory()
{
}

QueryTermFilterFactoryTest::~QueryTermFilterFactoryTest() = default;

TEST_F(QueryTermFilterFactoryTest, empty_schema)
{
    EXPECT_TRUE(check_view("foo", "foo"));
    EXPECT_FALSE(check_view("bar", "foo"));
    EXPECT_FALSE(check_view("foo", "bar"));
}

TEST_F(QueryTermFilterFactoryTest, field_set_is_checked)
{
    add_field_set("ab", {"cd", "de"});
    add_field_set("gh", {"cd"});
    add_field_set("default", {"de"});
    EXPECT_TRUE(check_view("cd", "cd"));
    EXPECT_TRUE(check_view("ab", "cd"));
    EXPECT_TRUE(check_view("gh", "cd"));
    EXPECT_FALSE(check_view("default", "cd"));
    EXPECT_FALSE(check_view("", "cd"));
    EXPECT_TRUE(check_view("de", "de"));
    EXPECT_TRUE(check_view("ab", "de"));
    EXPECT_FALSE(check_view("gh", "de"));
    EXPECT_TRUE(check_view("default", "de"));
    EXPECT_TRUE(check_view("", "de"));
}

GTEST_MAIN_RUN_ALL_TESTS()
