// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <memory>
#include <vespa/vespalib/gtest/gtest.h>

#include <string>
#include <vespa/juniper/query_item.h>
#include <vespa/juniper/queryhandle.h>
#include <vespa/juniper/queryvisitor.h>

using namespace juniper;

struct MyQueryItem : public QueryItem {
    MyQueryItem() : QueryItem() {}
    ~MyQueryItem() override = default;

    std::string_view get_index() const override { return {}; }
    int              get_weight() const override { return 0; }
    ItemCreator      get_creator() const override { return ItemCreator::CREA_ORIG; }
};

class MyQuery : public juniper::IQuery {
private:
    std::string _term;

public:
    explicit MyQuery(const std::string& term) : _term(term) {}

    bool Traverse(IQueryVisitor* v) const override {
        MyQueryItem item;
        v->visitKeyword(&item, _term, false, false);
        return true;
    }
    bool UsefulIndex(const QueryItem*) const override { return true; }
};

struct Fixture {
    MyQuery       query;
    QueryHandle   handle;
    QueryVisitor  visitor;
    explicit Fixture(const std::string& term)
      : query(term),
        handle(query, ""),
        visitor(query, &handle) {}
    ~Fixture();
};
Fixture::~Fixture() = default;

TEST(QueryVisitorTest, require_that_terms_are_picked_up_by_the_query_visitor) {
    Fixture f("my_term");
    auto query = std::unique_ptr<QueryExpr>(f.visitor.GetQuery());
    ASSERT_TRUE(query != nullptr);
    QueryNode* node = query->AsNode();
    ASSERT_TRUE(node != nullptr);
    EXPECT_EQ(1, node->_arity);
    QueryTerm* term = node->_children[0]->AsTerm();
    ASSERT_TRUE(term != nullptr);
    EXPECT_EQ("my_term", std::string(term->term()));
}

TEST(QueryVisitorTest, require_that_empty_terms_are_ignored_by_the_query_visitor) {
    Fixture f("");
    QueryExpr* query = f.visitor.GetQuery();
    ASSERT_TRUE(query == nullptr);
}

GTEST_MAIN_RUN_ALL_TESTS()
