// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <memory>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/juniper/queryhandle.h>
#include <vespa/juniper/queryvisitor.h>
#include <vespa/juniper/query_item.h>
#include <vespa/vespalib/stllike/string.h>

using namespace juniper;

struct MyQueryItem : public QueryItem
{
    MyQueryItem()
        : QueryItem()
    { }
    ~MyQueryItem() override = default;

    vespalib::stringref get_index() const override { return {}; }
    int get_weight() const override { return 0; }
    ItemCreator get_creator() const override { return ItemCreator::CREA_ORIG; }
};

class MyQuery : public juniper::IQuery
{
private:
    vespalib::string _term;

public:
    MyQuery(const vespalib::string &term) : _term(term) {}

    virtual bool Traverse(IQueryVisitor* v) const override {
        MyQueryItem item;
        v->VisitKeyword(&item, _term.c_str(), _term.size());
        return true;
    }
    virtual bool UsefulIndex(const QueryItem*) const override {
        return true;
    }
};

struct Fixture
{
    MyQuery query;
    QueryModifier modifier;
    QueryHandle handle;
    QueryVisitor visitor;
    Fixture(const vespalib::string &term)
        : query(term),
          modifier(),
          handle(query, "", modifier),
          visitor(query, &handle, modifier)
    {}
};

TEST_F("require that terms are picked up by the query visitor", Fixture("my_term"))
{
    auto query = std::unique_ptr<QueryExpr>(f.visitor.GetQuery());
    ASSERT_TRUE(query != nullptr);
    QueryNode *node = query->AsNode();
    ASSERT_TRUE(node != nullptr);
    EXPECT_EQUAL(1, node->_arity);
    QueryTerm *term = node->_children[0]->AsTerm();
    ASSERT_TRUE(term != nullptr);
    EXPECT_EQUAL("my_term", vespalib::string(term->term()));
}

TEST_F("require that empty terms are ignored by the query visitor", Fixture(""))
{
    QueryExpr *query = f.visitor.GetQuery();
    ASSERT_TRUE(query == nullptr);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
