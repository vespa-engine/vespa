// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for customtypevisitor.

#include <vespa/log/log.h>
LOG_SETUP("customtypevisitor_test");

#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/vespalib/testkit/testapp.h>

using std::string;

using namespace search::query;

namespace {

class Test : public vespalib::TestApp {
    const char *current_state;
    virtual void DumpState(bool) {
      fprintf(stderr, "%s: ERROR: in %s\n", GetName(), current_state);
    }

    template <class T> void requireThatNodeIsVisited();

public:
    int Main() override;
};

template <class Base>
struct InitTerm : Base {
    InitTerm() : Base(typename Base::Type(), "view", 0, Weight(0)) {}
};

struct MyAnd : And {};
struct MyAndNot : AndNot {};
struct MyEquiv : Equiv {};
struct MyNear : Near { MyNear() : Near(1) {} };
struct MyONear : ONear { MyONear() : ONear(1) {} };
struct MyOr : Or {};
struct MyPhrase : Phrase { MyPhrase() : Phrase("view", 0, Weight(42)) {} };
struct MyRank : Rank {};
struct MyNumberTerm : InitTerm<NumberTerm>  {};
struct MyLocationTerm : InitTerm<LocationTerm> {};
struct MyPrefixTerm : InitTerm<PrefixTerm>  {};
struct MyRangeTerm : InitTerm<RangeTerm> {};
struct MyStringTerm : InitTerm<StringTerm>  {};
struct MySubstrTerm : InitTerm<SubstringTerm>  {};
struct MySuffixTerm : InitTerm<SuffixTerm>  {};
struct MyWeakAnd : WeakAnd { MyWeakAnd() : WeakAnd(1234, "view") {} };
struct MyWeightedSetTerm : WeightedSetTerm { MyWeightedSetTerm() : WeightedSetTerm("view", 0, Weight(42)) {} };
struct MyDotProduct : DotProduct { MyDotProduct() : DotProduct("view", 0, Weight(42)) {} };
struct MyWandTerm : WandTerm { MyWandTerm() : WandTerm("view", 0, Weight(42), 57, 67, 77.7) {} };
struct MyPredicateQuery : InitTerm<PredicateQuery> {};
struct MyRegExpTerm : InitTerm<RegExpTerm>  {};

struct MyQueryNodeTypes {
    typedef MyAnd And;
    typedef MyAndNot AndNot;
    typedef MyEquiv Equiv;
    typedef MyNumberTerm NumberTerm;
    typedef MyLocationTerm LocationTerm;
    typedef MyNear Near;
    typedef MyONear ONear;
    typedef MyOr Or;
    typedef MyPhrase Phrase;
    typedef MyPrefixTerm PrefixTerm;
    typedef MyRangeTerm RangeTerm;
    typedef MyRank Rank;
    typedef MyStringTerm StringTerm;
    typedef MySubstrTerm SubstringTerm;
    typedef MySuffixTerm SuffixTerm;
    typedef MyWeakAnd WeakAnd;
    typedef MyWeightedSetTerm WeightedSetTerm;
    typedef MyDotProduct DotProduct;
    typedef MyWandTerm WandTerm;
    typedef MyPredicateQuery PredicateQuery;
    typedef MyRegExpTerm RegExpTerm;
};

class MyCustomVisitor : public CustomTypeVisitor<MyQueryNodeTypes>
{
public:
    template <typename T>
    bool &isVisited() {
        static bool b;
        return b;
    }

    template <typename T> void setVisited() { isVisited<T>() = true; }

    virtual void visit(MyAnd &) override { setVisited<MyAnd>(); }
    virtual void visit(MyAndNot &) override { setVisited<MyAndNot>(); }
    virtual void visit(MyEquiv &) override { setVisited<MyEquiv>(); }
    virtual void visit(MyNumberTerm &) override { setVisited<MyNumberTerm>(); }
    virtual void visit(MyLocationTerm &) override { setVisited<MyLocationTerm>(); }
    virtual void visit(MyNear &) override { setVisited<MyNear>(); }
    virtual void visit(MyONear &) override { setVisited<MyONear>(); }
    virtual void visit(MyOr &) override { setVisited<MyOr>(); }
    virtual void visit(MyPhrase &) override { setVisited<MyPhrase>(); }
    virtual void visit(MyPrefixTerm &) override { setVisited<MyPrefixTerm>(); }
    virtual void visit(MyRangeTerm &) override { setVisited<MyRangeTerm>(); }
    virtual void visit(MyRank &) override { setVisited<MyRank>(); }
    virtual void visit(MyStringTerm &) override { setVisited<MyStringTerm>(); }
    virtual void visit(MySubstrTerm &) override { setVisited<MySubstrTerm>(); }
    virtual void visit(MySuffixTerm &) override { setVisited<MySuffixTerm>(); }
    virtual void visit(MyWeakAnd &) override { setVisited<MyWeakAnd>(); }
    virtual void visit(MyWeightedSetTerm &) override { setVisited<MyWeightedSetTerm>(); }
    virtual void visit(MyDotProduct &) override { setVisited<MyDotProduct>(); }
    virtual void visit(MyWandTerm &) override { setVisited<MyWandTerm>(); }
    virtual void visit(MyPredicateQuery &) override { setVisited<MyPredicateQuery>(); }
    virtual void visit(MyRegExpTerm &) override { setVisited<MyRegExpTerm>(); }
};

template <class T>
void Test::requireThatNodeIsVisited() {
    MyCustomVisitor visitor;
    Node::UP query(new T);
    visitor.isVisited<T>() = false;
    query->accept(visitor);
    ASSERT_TRUE(visitor.isVisited<T>());
}

#define TEST_CALL(func) \
    current_state = #func; \
    func();

int
Test::Main()
{
    TEST_INIT("customtypevisitor_test");

    TEST_CALL(requireThatNodeIsVisited<MyAnd>);
    TEST_CALL(requireThatNodeIsVisited<MyAndNot>);
    TEST_CALL(requireThatNodeIsVisited<MyNear>);
    TEST_CALL(requireThatNodeIsVisited<MyONear>);
    TEST_CALL(requireThatNodeIsVisited<MyOr>);
    TEST_CALL(requireThatNodeIsVisited<MyPhrase>);
    TEST_CALL(requireThatNodeIsVisited<MyRangeTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyRank>);
    TEST_CALL(requireThatNodeIsVisited<MyNumberTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyPrefixTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyStringTerm>);
    TEST_CALL(requireThatNodeIsVisited<MySubstrTerm>);
    TEST_CALL(requireThatNodeIsVisited<MySuffixTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyWeightedSetTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyDotProduct>);
    TEST_CALL(requireThatNodeIsVisited<MyWandTerm>);
    TEST_CALL(requireThatNodeIsVisited<MyPredicateQuery>);
    TEST_CALL(requireThatNodeIsVisited<MyRegExpTerm>);

    TEST_DONE();
}
}  // namespace

TEST_APPHOOK(Test);
