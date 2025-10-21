// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <memory>
#include <vespa/vespalib/gtest/gtest.h>

#include <string>
#include <vespa/juniper/query_item.h>
#include <vespa/juniper/queryhandle.h>
#include <vespa/juniper/queryvisitor.h>
#include <vespa/juniper/specialtokenregistry.h>

using namespace juniper;

struct MyQueryItem : QueryItem {
    MyQueryItem() : QueryItem() {}
    ~MyQueryItem() override = default;

    std::string_view get_index() const override { return {}; }
    int              get_weight() const override { return 0; }
    ItemCreator      get_creator() const override { return ItemCreator::CREA_ORIG; }
};

class MyQuery : public juniper::IQuery {
protected:
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

struct MySpecialTokenQuery : MyQuery {
    explicit MySpecialTokenQuery(const std::string& term) : MyQuery(term) {}
    bool Traverse(IQueryVisitor* v) const override {
        MyQueryItem item;
        v->visitKeyword(&item, _term, false, true);
        return true;
    }
};

template <typename QueryType>
struct Fixture {
    QueryType     query;
    QueryHandle   handle;
    QueryVisitor  visitor;
    explicit Fixture(const std::string& term)
      : query(term),
        handle(query, ""),
        visitor(query, &handle) {}
    ~Fixture();
};

template <typename QueryType>
Fixture<QueryType>::~Fixture() = default;

TEST(QueryVisitorTest, require_that_terms_are_picked_up_by_the_query_visitor) {
    Fixture<MyQuery> f("my_term");
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
    Fixture<MyQuery> f("");
    QueryExpr* query = f.visitor.GetQuery();
    ASSERT_TRUE(query == nullptr);
}

TEST(QueryVisitorTest, special_token_registry_ignores_empty_terms) {
    Fixture<MySpecialTokenQuery> f("\x80"); // intentionally invalid UTF-8
    auto query = std::unique_ptr<QueryExpr>(f.visitor.GetQuery());
    ASSERT_TRUE(query != nullptr);
    SpecialTokenRegistry registry(query.get());
    EXPECT_EQ(registry.getSpecialTokens().size(), 0);
}

GTEST_MAIN_RUN_ALL_TESTS()
