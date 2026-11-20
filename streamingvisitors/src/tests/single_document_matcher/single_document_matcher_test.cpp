// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchvisitor/searchenvironment.h>
#include <vespa/searchvisitor/single_document_matcher.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>

using namespace document;
using namespace search::query;

namespace streaming {

namespace {

// Reuses the streaming config and document type of the search visitor test (schema cfg/test.sd).
std::string cfg_dir() {
    return TEST_PATH("../searchvisitor/cfg");
}

std::string number_term_query(const std::string& term) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addNumberTerm(term, "id", 1, Weight(100));
    QueryToProtobuf converter;
    return converter.serialize(*builder.build()).SerializeAsString();
}

Document make_doc(const DocumentTypeRepo& repo, int id) {
    Document doc(repo, *repo.getDocumentType("test"), DocumentId("id:test:test::" + std::to_string(id)));
    doc.setValue("id", std::make_unique<IntFieldValue>(id));
    return doc;
}

std::string range_query(int64_t from, int64_t to) {
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addRangeTerm(Range(from, to), "id", 1, Weight(100));
    QueryToProtobuf converter;
    return converter.serialize(*builder.build()).SerializeAsString();
}

std::span<const char> span_of(const std::string& s) {
    return {s.data(), s.size()};
}

} // namespace

class SingleDocumentMatcherTest : public testing::Test {
protected:
    SearchEnvironment                       _env;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    Document                                _doc;
    SingleDocumentMatcher                   _matcher;

    SingleDocumentMatcherTest()
        : _env(::config::ConfigUri("dir:" + cfg_dir()), nullptr, ""),
          _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(cfg_dir() + "/documenttypes.cfg"))),
          _doc(make_doc(*_repo, 5)),
          _matcher(_env.get_snapshot("mycl"), _repo, _doc) {}
    ~SingleDocumentMatcherTest() override { _env.clear_thread_local_env_map(); }
};

TEST_F(SingleDocumentMatcherTest, matching_query_is_ranked_with_the_given_profile) {
    auto result = _matcher.evaluate(span_of(number_term_query("5")), {}, "default");
    ASSERT_TRUE(result.has_value());
    EXPECT_TRUE(result->matched);
    EXPECT_DOUBLE_EQ(15.0, result->score); // attribute(id) + 10
}

TEST_F(SingleDocumentMatcherTest, range_query_matches) {
    EXPECT_TRUE(_matcher.evaluate(span_of(range_query(3, 7)), {}, "default")->matched);
    EXPECT_FALSE(_matcher.evaluate(span_of(range_query(6, 9)), {}, "default")->matched);
}

TEST_F(SingleDocumentMatcherTest, non_matching_query_does_not_match) {
    auto result = _matcher.evaluate(span_of(number_term_query("6")), {}, "default");
    ASSERT_TRUE(result.has_value());
    EXPECT_FALSE(result->matched);
}

TEST_F(SingleDocumentMatcherTest, evaluations_do_not_affect_each_other) {
    for (int i = 0; i < 3; ++i) {
        EXPECT_FALSE(_matcher.evaluate(span_of(number_term_query("6")), {}, "default")->matched);
        EXPECT_TRUE(_matcher.evaluate(span_of(number_term_query("5")), {}, "default")->matched);
    }
}

TEST_F(SingleDocumentMatcherTest, unknown_rank_profile_is_rejected) {
    EXPECT_FALSE(_matcher.evaluate(span_of(number_term_query("5")), {}, "no_such_profile").has_value());
}

TEST_F(SingleDocumentMatcherTest, invalid_query_tree_is_rejected) {
    std::string garbage = "not a protobuf query tree";
    EXPECT_FALSE(_matcher.evaluate(span_of(garbage), {}, "default").has_value());
}

TEST_F(SingleDocumentMatcherTest, invalid_properties_are_rejected) {
    std::string garbage("\x00\x00\x00\x01xx", 6); // one map, truncated
    EXPECT_FALSE(_matcher.evaluate(span_of(number_term_query("5")), span_of(garbage), "default").has_value());
}

} // namespace streaming

GTEST_MAIN_RUN_ALL_TESTS()
