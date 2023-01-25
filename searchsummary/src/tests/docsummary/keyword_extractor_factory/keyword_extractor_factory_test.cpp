// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchsummary/docsummary/i_keyword_extractor.h>
#include <vespa/searchsummary/docsummary/keyword_extractor_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::docsummary::IKeywordExtractor;
using search::docsummary::IKeywordExtractorFactory;
using search::docsummary::KeywordExtractorFactory;
using search::index::Schema;

using FieldSet = Schema::FieldSet;

class KeywordExtractorFactoryTest : public testing::Test {
    std::unique_ptr<IKeywordExtractorFactory> _factory;
    Schema _schema;

protected:
    KeywordExtractorFactoryTest();
    ~KeywordExtractorFactoryTest() override;

    void make_factory() {
        _factory = std::make_unique<KeywordExtractorFactory>(_schema);
    }

    bool check_index(const vespalib::string &index_name, const vespalib::string& summary_field) {
        if (!_factory) {
            make_factory();
        }
        auto extractor = _factory->make(summary_field);
        return extractor->isLegalIndex(index_name);
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


KeywordExtractorFactoryTest::KeywordExtractorFactoryTest()
    : testing::Test(),
      _factory()
{
}

KeywordExtractorFactoryTest::~KeywordExtractorFactoryTest() = default;

TEST_F(KeywordExtractorFactoryTest, empty_schema)
{
    EXPECT_TRUE(check_index("foo", "foo"));
    EXPECT_FALSE(check_index("bar", "foo"));
    EXPECT_FALSE(check_index("foo", "bar"));
}

TEST_F(KeywordExtractorFactoryTest, field_set_is_checked)
{
    add_field_set("ab", {"cd", "de"});
    add_field_set("gh", {"cd"});
    add_field_set("default", {"de"});
    EXPECT_TRUE(check_index("cd", "cd"));
    EXPECT_TRUE(check_index("ab", "cd"));
    EXPECT_TRUE(check_index("gh", "cd"));
    EXPECT_FALSE(check_index("default", "cd"));
    EXPECT_FALSE(check_index("", "cd"));
    EXPECT_TRUE(check_index("de", "de"));
    EXPECT_TRUE(check_index("ab", "de"));
    EXPECT_FALSE(check_index("gh", "de"));
    EXPECT_TRUE(check_index("default", "de"));
    EXPECT_TRUE(check_index("", "de"));
}

GTEST_MAIN_RUN_ALL_TESTS()
