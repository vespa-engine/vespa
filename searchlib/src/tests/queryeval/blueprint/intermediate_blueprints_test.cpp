// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mysearch.h"
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/multisearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/test/diskindex/testdiskindex.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("blueprint_test");

using namespace search::queryeval;
using namespace search::fef;
using namespace search::query;
using search::BitVector;

struct InvalidSelector : ISourceSelector {
    InvalidSelector() : ISourceSelector(Source()) {}
    void setSource(uint32_t, Source) override { abort(); }
    uint32_t getDocIdLimit() const override { abort(); }
    void compactLidSpace(uint32_t) override { abort(); }
    std::unique_ptr<sourceselector::Iterator> createIterator() const override { abort(); }
};

struct WeightOrder {
    bool operator()(const wand::Term &t1, const wand::Term &t2) const {
        return (t1.weight < t2.weight);
    }
};

struct RememberExecuteInfo : public MyLeaf {
    ExecuteInfo executeInfo;

    using MyLeaf::MyLeaf;

    void fetchPostings(const ExecuteInfo &execInfo) override {
        LeafBlueprint::fetchPostings(execInfo);
        executeInfo = execInfo;
    }
};

Blueprint::UP ap(Blueprint *b) { return Blueprint::UP(b); }
Blueprint::UP ap(Blueprint &b) { return Blueprint::UP(&b); }

bool got_global_filter(Blueprint &b) {
    return (static_cast<MyLeaf &>(b)).got_global_filter();
}

void check_sort_order(IntermediateBlueprint &self, std::vector<Blueprint*> unordered, std::vector<size_t> order) {
    ASSERT_EQUAL(unordered.size(), order.size());
    std::vector<Blueprint::UP> children;
    for (auto *child: unordered) {
        children.push_back(std::unique_ptr<Blueprint>(child));
    }
    self.sort(children);
    for (size_t i = 0; i < children.size(); ++i) {
        EXPECT_EQUAL(children[i].get(), unordered[order[i]]);
    }
}

TEST("test AndNot Blueprint") {
    AndNotBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
    }
    {
        AndNotBlueprint a;
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
        EXPECT_EQUAL(false, a.getState().want_global_filter());
        a.addChild(ap(MyLeafSpec(20).addField(1, 1).want_global_filter().create()));
        EXPECT_EQUAL(true, a.getState().want_global_filter());
        auto empty_global_filter = GlobalFilter::create();
        EXPECT_FALSE(empty_global_filter->is_active());
        a.set_global_filter(*empty_global_filter, 1.0);
        EXPECT_EQUAL(false, got_global_filter(a.getChild(0)));
        EXPECT_EQUAL(true,  got_global_filter(a.getChild(1)));
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(10).create(),
                          MyLeafSpec(20).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {0, 2, 3, 1});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(false, b.inheritStrict(1));
        EXPECT_EQUAL(false, b.inheritStrict(2));
        EXPECT_EQUAL(false, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test And propagates updated histestimate") {
    AndBlueprint bp;
    bp.setSourceId(2);
    bp.addChild(ap(MyLeafSpec(20).create<RememberExecuteInfo>()->setSourceId(2)));
    bp.addChild(ap(MyLeafSpec(200).create<RememberExecuteInfo>()->setSourceId(2)));
    bp.addChild(ap(MyLeafSpec(2000).create<RememberExecuteInfo>()->setSourceId(2)));
    bp.optimize_self();
    bp.setDocIdLimit(5000);
    bp.fetchPostings(ExecuteInfo::create(true));
    EXPECT_EQUAL(3u, bp.childCnt());
    for (uint32_t i = 0; i < bp.childCnt(); i++) {
        const RememberExecuteInfo & child = dynamic_cast<const RememberExecuteInfo &>(bp.getChild(i));
        EXPECT_EQUAL((i == 0), child.executeInfo.isStrict());
    }
    EXPECT_EQUAL(1.0, dynamic_cast<const RememberExecuteInfo &>(bp.getChild(0)).executeInfo.hitRate());
    EXPECT_EQUAL(1.0/250, dynamic_cast<const RememberExecuteInfo &>(bp.getChild(1)).executeInfo.hitRate());
    EXPECT_EQUAL(1.0/(250*25), dynamic_cast<const RememberExecuteInfo &>(bp.getChild(2)).executeInfo.hitRate());
}

TEST("test And Blueprint") {
    AndBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
    }
    {
        AndBlueprint a;
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
        EXPECT_EQUAL(false, a.getState().want_global_filter());
        a.addChild(ap(MyLeafSpec(20).addField(1, 1).want_global_filter().create()));
        EXPECT_EQUAL(true, a.getState().want_global_filter());
        auto empty_global_filter = GlobalFilter::create();
        a.set_global_filter(*empty_global_filter, 1.0);
        EXPECT_EQUAL(false, got_global_filter(a.getChild(0)));
        EXPECT_EQUAL(true,  got_global_filter(a.getChild(1)));
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(20).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(30).create()},
                         {2, 0, 3, 1});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(false, b.inheritStrict(1));
        EXPECT_EQUAL(false, b.inheritStrict(2));
        EXPECT_EQUAL(false, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test Or Blueprint") {
    OrBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
    }
    {
        OrBlueprint &o = *(new OrBlueprint());
        o.addChild(ap(MyLeafSpec(1).addField(1, 1).create()));
        o.addChild(ap(MyLeafSpec(2).addField(2, 2).create()));

        Blueprint::UP a(&o);
        ASSERT_TRUE(a->getState().numFields() == 2);
        EXPECT_EQUAL(1u, a->getState().field(0).getFieldId());
        EXPECT_EQUAL(2u, a->getState().field(1).getFieldId());
        EXPECT_EQUAL(1u, a->getState().field(0).getHandle());
        EXPECT_EQUAL(2u, a->getState().field(1).getHandle());
        EXPECT_EQUAL(2u, a->getState().estimate().estHits);

        o.addChild(ap(MyLeafSpec(5).addField(2, 2).create()));
        ASSERT_TRUE(a->getState().numFields() == 2);
        EXPECT_EQUAL(1u, a->getState().field(0).getFieldId());
        EXPECT_EQUAL(2u, a->getState().field(1).getFieldId());
        EXPECT_EQUAL(1u, a->getState().field(0).getHandle());
        EXPECT_EQUAL(2u, a->getState().field(1).getHandle());
        EXPECT_EQUAL(5u, a->getState().estimate().estHits);

        o.addChild(ap(MyLeafSpec(5).addField(2, 3).create()));
        EXPECT_EQUAL(0u, a->getState().numFields());
        o.removeChild(3);
        EXPECT_EQUAL(2u, a->getState().numFields());
        o.addChild(ap(MyLeafSpec(0, true).create()));
        EXPECT_EQUAL(0u, a->getState().numFields());

        EXPECT_EQUAL(false, o.getState().want_global_filter());
        o.addChild(ap(MyLeafSpec(20).addField(1, 1).want_global_filter().create()));
        EXPECT_EQUAL(true, o.getState().want_global_filter());
        auto empty_global_filter = GlobalFilter::create();
        o.set_global_filter(*empty_global_filter, 1.0);
        EXPECT_EQUAL(false, got_global_filter(o.getChild(0)));
        EXPECT_EQUAL(true,  got_global_filter(o.getChild(o.childCnt() - 1)));
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(10).create(),
                          MyLeafSpec(20).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {2, 3, 1, 0});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(true, b.inheritStrict(1));
        EXPECT_EQUAL(true, b.inheritStrict(2));
        EXPECT_EQUAL(true, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test Near Blueprint") {
    NearBlueprint b(7);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
    }
    {
        NearBlueprint a(7);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(40).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(30).create(),
                          MyLeafSpec(20).create()},
                         {1, 3, 2, 0});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(false, b.inheritStrict(1));
        EXPECT_EQUAL(false, b.inheritStrict(2));
        EXPECT_EQUAL(false, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test ONear Blueprint") {
    ONearBlueprint b(8);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
    }
    {
        ONearBlueprint a(8);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(20).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {0, 1, 2, 3});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(false, b.inheritStrict(1));
        EXPECT_EQUAL(false, b.inheritStrict(2));
        EXPECT_EQUAL(false, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test Rank Blueprint") {
    RankBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
    }
    {
        RankBlueprint a;
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());

        EXPECT_EQUAL(false, a.getState().want_global_filter());
        a.addChild(ap(MyLeafSpec(20).addField(1, 1).want_global_filter().create()));
        EXPECT_EQUAL(true, a.getState().want_global_filter());
        auto empty_global_filter = GlobalFilter::create();
        a.set_global_filter(*empty_global_filter, 1.0);
        EXPECT_EQUAL(false, got_global_filter(a.getChild(0)));
        EXPECT_EQUAL(true,  got_global_filter(a.getChild(1)));
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(20).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {0, 1, 2, 3});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(false, b.inheritStrict(1));
        EXPECT_EQUAL(false, b.inheritStrict(2));
        EXPECT_EQUAL(false, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test SourceBlender Blueprint") {
    auto selector = std::make_unique<InvalidSelector>(); // not needed here
    SourceBlenderBlueprint b(*selector);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
    }
    {
        SourceBlenderBlueprint &o = *(new SourceBlenderBlueprint(*selector));
        o.addChild(ap(MyLeafSpec(1).addField(1, 1).create()));
        o.addChild(ap(MyLeafSpec(2).addField(2, 2).create()));

        Blueprint::UP a(&o);
        ASSERT_TRUE(a->getState().numFields() == 2);
        EXPECT_EQUAL(1u, a->getState().field(0).getFieldId());
        EXPECT_EQUAL(2u, a->getState().field(1).getFieldId());
        EXPECT_EQUAL(1u, a->getState().field(0).getHandle());
        EXPECT_EQUAL(2u, a->getState().field(1).getHandle());
        EXPECT_EQUAL(2u, a->getState().estimate().estHits);

        o.addChild(ap(MyLeafSpec(5).addField(2, 2).create()));
        ASSERT_TRUE(a->getState().numFields() == 2);
        EXPECT_EQUAL(1u, a->getState().field(0).getFieldId());
        EXPECT_EQUAL(2u, a->getState().field(1).getFieldId());
        EXPECT_EQUAL(1u, a->getState().field(0).getHandle());
        EXPECT_EQUAL(2u, a->getState().field(1).getHandle());
        EXPECT_EQUAL(5u, a->getState().estimate().estHits);

        o.addChild(ap(MyLeafSpec(5).addField(2, 3).create()));
        EXPECT_EQUAL(0u, a->getState().numFields());
        o.removeChild(3);
        EXPECT_EQUAL(2u, a->getState().numFields());
        o.addChild(ap(MyLeafSpec(0, true).create()));
        EXPECT_EQUAL(0u, a->getState().numFields());
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(20).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {0, 1, 2, 3});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(true, b.inheritStrict(1));
        EXPECT_EQUAL(true, b.inheritStrict(2));
        EXPECT_EQUAL(true, b.inheritStrict(-1));
    }
    // createSearch tested by iterator unit test
}

TEST("test SourceBlender below AND optimization") {
    auto selector_1 = std::make_unique<InvalidSelector>(); // the one
    auto selector_2 = std::make_unique<InvalidSelector>(); // not the one
    //-------------------------------------------------------------------------
    AndBlueprint *top = new AndBlueprint();
    Blueprint::UP top_bp(top);
    top->addChild(ap(MyLeafSpec(2).create()));
    top->addChild(ap(MyLeafSpec(1).create()));
    top->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    AndBlueprint *expect = new AndBlueprint();
    Blueprint::UP expect_bp(expect);
    expect->addChild(ap(MyLeafSpec(1).create()));
    expect->addChild(ap(MyLeafSpec(2).create()));
    expect->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        expect->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender(new SourceBlenderBlueprint(*selector_1));
        {
            AndBlueprint *sub_and = new AndBlueprint();
            sub_and->setSourceId(3);
            sub_and->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
            sub_and->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
            blender->addChild(ap(sub_and));
        }
        {
            AndBlueprint *sub_and = new AndBlueprint();
            sub_and->setSourceId(2);
            sub_and->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
            blender->addChild(ap(sub_and));
        }
        {
            AndBlueprint *sub_and = new AndBlueprint();
            sub_and->setSourceId(1);
            sub_and->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
            blender->addChild(ap(sub_and));
        }
        expect->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_bp->asString(), top_bp->asString());
    top_bp = Blueprint::optimize(std::move(top_bp));
    EXPECT_EQUAL(expect_bp->asString(), top_bp->asString());
    expect_bp = Blueprint::optimize(std::move(expect_bp));
    EXPECT_EQUAL(expect_bp->asString(), top_bp->asString());
}

TEST("test SourceBlender below OR optimization") {
    auto selector_1 = std::make_unique<InvalidSelector>(); // the one
    auto selector_2 = std::make_unique<InvalidSelector>(); // not the one
    //-------------------------------------------------------------------------
    OrBlueprint *top = new OrBlueprint();
    Blueprint::UP top_up(top);
    top->addChild(ap(MyLeafSpec(2).create()));
    top->addChild(ap(MyLeafSpec(1).create()));
    top->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    OrBlueprint *expect = new OrBlueprint();
    Blueprint::UP expect_up(expect);
    {
        SourceBlenderBlueprint *blender(new SourceBlenderBlueprint(*selector_1));
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(3);
            sub_and->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
            sub_and->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(2);
            sub_and->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(1);
            sub_and->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
            blender->addChild(ap(sub_and));
        }
        expect->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        expect->addChild(ap(blender));
    }
    expect->addChild(ap(MyLeafSpec(3).create()));
    expect->addChild(ap(MyLeafSpec(2).create()));
    expect->addChild(ap(MyLeafSpec(1).create()));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test SourceBlender below AND_NOT optimization") {
    auto selector_1 = std::make_unique<InvalidSelector>(); // the one
    auto selector_2 = std::make_unique<InvalidSelector>(); // not the one
    //-------------------------------------------------------------------------
    AndNotBlueprint *top = new AndNotBlueprint();
    Blueprint::UP top_up(top);
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(42).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    top->addChild(ap(MyLeafSpec(2).create()));
    top->addChild(ap(MyLeafSpec(1).create()));
    top->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    AndNotBlueprint *expect = new AndNotBlueprint();
    Blueprint::UP expect_up(expect);
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(42).create()->setSourceId(1)));
        expect->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender(new SourceBlenderBlueprint(*selector_1));
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(3);
            sub_and->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
            sub_and->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(2);
            sub_and->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(1);
            sub_and->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
            blender->addChild(ap(sub_and));
        }
        expect->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        expect->addChild(ap(blender));
    }
    expect->addChild(ap(MyLeafSpec(3).create()));
    expect->addChild(ap(MyLeafSpec(2).create()));
    expect->addChild(ap(MyLeafSpec(1).create()));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test SourceBlender below RANK optimization") {
    auto selector_1 = std::make_unique<InvalidSelector>(); // the one
    auto selector_2 = std::make_unique<InvalidSelector>(); // not the one
    //-------------------------------------------------------------------------
    RankBlueprint *top = new RankBlueprint();
    Blueprint::UP top_up(top);
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(42).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    top->addChild(ap(MyLeafSpec(2).create()));
    top->addChild(ap(MyLeafSpec(1).create()));
    top->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        top->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
        blender->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
        top->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    RankBlueprint *expect = new RankBlueprint();
    Blueprint::UP expect_up(expect);
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_1);
        blender->addChild(ap(MyLeafSpec(42).create()->setSourceId(1)));
        expect->addChild(ap(blender));
    }
    expect->addChild(ap(MyLeafSpec(2).create()));
    expect->addChild(ap(MyLeafSpec(1).create()));
    expect->addChild(ap(MyLeafSpec(3).create()));
    {
        SourceBlenderBlueprint *blender = new SourceBlenderBlueprint(*selector_2);
        blender->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
        blender->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
        expect->addChild(ap(blender));
    }
    {
        SourceBlenderBlueprint *blender(new SourceBlenderBlueprint(*selector_1));
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(3);
            sub_and->addChild(ap(MyLeafSpec(300).create()->setSourceId(3)));
            sub_and->addChild(ap(MyLeafSpec(30).create()->setSourceId(3)));        
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(2);
            sub_and->addChild(ap(MyLeafSpec(2000).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(200).create()->setSourceId(2)));
            sub_and->addChild(ap(MyLeafSpec(20).create()->setSourceId(2)));
            blender->addChild(ap(sub_and));
        }
        {
            OrBlueprint *sub_and = new OrBlueprint();
            sub_and->setSourceId(1);
            sub_and->addChild(ap(MyLeafSpec(1000).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(100).create()->setSourceId(1)));
            sub_and->addChild(ap(MyLeafSpec(10).create()->setSourceId(1)));
            blender->addChild(ap(sub_and));
        }
        expect->addChild(ap(blender));
    }
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test empty root node optimization and safeness") {
    //-------------------------------------------------------------------------
    // tests leaf node elimination
    Blueprint::UP top1_up(ap(MyLeafSpec(0, true).create()));
    //-------------------------------------------------------------------------
    // tests intermediate node elimination
    Blueprint::UP top2_up(ap((new AndBlueprint())->
                             addChild(ap(MyLeafSpec(0, true).create())).
                             addChild(ap(MyLeafSpec(10).create())).
                             addChild(ap(MyLeafSpec(20).create()))));
    //-------------------------------------------------------------------------
    // tests safety of empty AND_NOT child removal
    Blueprint::UP top3_up(ap((new AndNotBlueprint())->
                             addChild(ap(MyLeafSpec(0, true).create())).
                             addChild(ap(MyLeafSpec(10).create())).
                             addChild(ap(MyLeafSpec(20).create()))));
    //-------------------------------------------------------------------------
    // tests safety of empty RANK child removal
    Blueprint::UP top4_up(ap((new RankBlueprint())->
                             addChild(ap(MyLeafSpec(0, true).create())).
                             addChild(ap(MyLeafSpec(10).create())).
                             addChild(ap(MyLeafSpec(20).create()))));
    //-------------------------------------------------------------------------
    // tests safety of empty OR child removal
    Blueprint::UP top5_up(ap((new OrBlueprint())->
                             addChild(ap(MyLeafSpec(0, true).create())).
                             addChild(ap(MyLeafSpec(0, true).create())).
                             addChild(ap(MyLeafSpec(0, true).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(new EmptyBlueprint());
    //-------------------------------------------------------------------------
    top1_up = Blueprint::optimize(std::move(top1_up));
    top2_up = Blueprint::optimize(std::move(top2_up));
    top3_up = Blueprint::optimize(std::move(top3_up));
    top4_up = Blueprint::optimize(std::move(top4_up));
    top5_up = Blueprint::optimize(std::move(top5_up));
    EXPECT_EQUAL(expect_up->asString(), top1_up->asString());
    EXPECT_EQUAL(expect_up->asString(), top2_up->asString());
    EXPECT_EQUAL(expect_up->asString(), top3_up->asString());
    EXPECT_EQUAL(expect_up->asString(), top4_up->asString());
    EXPECT_EQUAL(expect_up->asString(), top5_up->asString());
}

TEST("and with one empty child is optimized away") {
    auto selector = std::make_unique<InvalidSelector>();
    Blueprint::UP top(ap((new SourceBlenderBlueprint(*selector))->
                          addChild(ap(MyLeafSpec(10).create())).
                          addChild(ap((new AndBlueprint())->
                                          addChild(ap(MyLeafSpec(0, true).create())).
                                          addChild(ap(MyLeafSpec(10).create())).
                                          addChild(ap(MyLeafSpec(20).create()))))));
    top = Blueprint::optimize(std::move(top));
    Blueprint::UP expect_up(ap((new SourceBlenderBlueprint(*selector))->
                          addChild(ap(MyLeafSpec(10).create())).
                          addChild(ap(new EmptyBlueprint()))));
    EXPECT_EQUAL(expect_up->asString(), top->asString());
}

TEST("test single child optimization") {
    auto selector = std::make_unique<InvalidSelector>();
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new AndNotBlueprint())->
               addChild(ap((new AndBlueprint())->
                           addChild(ap((new OrBlueprint())->
                                           addChild(ap((new SourceBlenderBlueprint(*selector))->
                                                           addChild(ap((new RankBlueprint())->
                                                                           addChild(ap(MyLeafSpec(42).create()))))))))))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new SourceBlenderBlueprint(*selector))->
               addChild(ap(MyLeafSpec(42).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test empty OR child optimization") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new OrBlueprint())->
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(0).create())).
               addChild(ap(MyLeafSpec(30).create())).
               addChild(ap(MyLeafSpec(0, true).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new OrBlueprint())->
               addChild(ap(MyLeafSpec(30).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test empty AND_NOT child optimization") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new AndNotBlueprint())->
               addChild(ap(MyLeafSpec(42).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(0).create())).
               addChild(ap(MyLeafSpec(30).create())).
               addChild(ap(MyLeafSpec(0, true).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new AndNotBlueprint())->
               addChild(ap(MyLeafSpec(42).create())).
               addChild(ap(MyLeafSpec(30).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("test empty RANK child optimization") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new RankBlueprint())->
               addChild(ap(MyLeafSpec(42).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0, true).create())).
               addChild(ap(MyLeafSpec(0).create())).
               addChild(ap(MyLeafSpec(30).create())).
               addChild(ap(MyLeafSpec(0, true).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new RankBlueprint())->
               addChild(ap(MyLeafSpec(42).create())).
               addChild(ap(MyLeafSpec(20).create())).
               addChild(ap(MyLeafSpec(10).create())).
               addChild(ap(MyLeafSpec(0).create())).
               addChild(ap(MyLeafSpec(30).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that replaced blueprints retain source id") {
    //-------------------------------------------------------------------------
    // replace empty root with empty search
    Blueprint::UP top1_up(ap(MyLeafSpec(0, true).create()->setSourceId(13)));
    Blueprint::UP expect1_up(new EmptyBlueprint());
    expect1_up->setSourceId(13);
    //-------------------------------------------------------------------------
    // replace self with single child
    Blueprint::UP top2_up(ap(static_cast<AndBlueprint&>((new AndBlueprint())->setSourceId(42)).
                             addChild(ap(MyLeafSpec(30).create()->setSourceId(55)))));
    Blueprint::UP expect2_up(ap(MyLeafSpec(30).create()->setSourceId(42)));
    //-------------------------------------------------------------------------
    top1_up = Blueprint::optimize(std::move(top1_up));
    top2_up = Blueprint::optimize(std::move(top2_up));
    EXPECT_EQUAL(expect1_up->asString(), top1_up->asString());
    EXPECT_EQUAL(expect2_up->asString(), top2_up->asString());
    EXPECT_EQUAL(13u, top1_up->getSourceId());
    EXPECT_EQUAL(42u, top2_up->getSourceId());
}

TEST("test Equiv Blueprint") {
    FieldSpecBaseList fields;
    search::fef::MatchDataLayout subLayout;
    fields.add(FieldSpecBase(1, 1));
    fields.add(FieldSpecBase(2, 2));
    fields.add(FieldSpecBase(3, 3));
    EquivBlueprint b(fields, subLayout);
    {
        EquivBlueprint &o = *(new EquivBlueprint(fields, subLayout));
        o.addTerm(ap(MyLeafSpec(5).addField(1, 4).create()), 1.0);
        o.addTerm(ap(MyLeafSpec(10).addField(1, 5).create()), 1.0);
        o.addTerm(ap(MyLeafSpec(20).addField(1, 6).create()), 1.0);
        o.addTerm(ap(MyLeafSpec(50).addField(2, 7).create()), 1.0);

        Blueprint::UP a(&o);
        ASSERT_TRUE(a->getState().numFields() == 3);
        EXPECT_EQUAL(1u, a->getState().field(0).getFieldId());
        EXPECT_EQUAL(2u, a->getState().field(1).getFieldId());
        EXPECT_EQUAL(3u, a->getState().field(2).getFieldId());

        EXPECT_EQUAL(1u, a->getState().field(0).getHandle());
        EXPECT_EQUAL(2u, a->getState().field(1).getHandle());
        EXPECT_EQUAL(3u, a->getState().field(2).getHandle());

        EXPECT_EQUAL(50u, a->getState().estimate().estHits);
        EXPECT_EQUAL(false, a->getState().estimate().empty);
    }
    // createSearch tested by iterator unit test
}


TEST("test WeakAnd Blueprint") {
    WeakAndBlueprint b(1000);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(10, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(20, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(5, false));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.push_back(Blueprint::HitEstimate(0, true));
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
    }
    {
        WeakAndBlueprint a(1000);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    {
        check_sort_order(b,
                         {MyLeafSpec(20).create(),
                          MyLeafSpec(10).create(),
                          MyLeafSpec(40).create(),
                          MyLeafSpec(30).create()},
                         {0, 1, 2, 3});
    }
    {
        EXPECT_EQUAL(true, b.inheritStrict(0));
        EXPECT_EQUAL(true, b.inheritStrict(1));
        EXPECT_EQUAL(true, b.inheritStrict(2));
        EXPECT_EQUAL(true, b.inheritStrict(-1));
    }
    {
        FieldSpec field("foo", 1, 1);
        FakeResult x = FakeResult().doc(1).doc(2).doc(5);
        FakeResult y = FakeResult().doc(2);
        FakeResult z = FakeResult().doc(1).doc(4);
        {
            WeakAndBlueprint wa(456);
            MatchData::UP md = MatchData::makeTestInstance(100, 10);
            wa.addTerm(Blueprint::UP(new FakeBlueprint(field, x)), 120);
            wa.addTerm(Blueprint::UP(new FakeBlueprint(field, z)), 140);
            wa.addTerm(Blueprint::UP(new FakeBlueprint(field, y)), 130);
            {
                wa.fetchPostings(ExecuteInfo::TRUE);
                SearchIterator::UP search = wa.createSearch(*md, true);
                EXPECT_TRUE(dynamic_cast<WeakAndSearch*>(search.get()) != 0);
                WeakAndSearch &s = dynamic_cast<WeakAndSearch&>(*search);
                EXPECT_EQUAL(456u, s.getN());
                ASSERT_EQUAL(3u, s.getTerms().size());
                EXPECT_GREATER(s.get_max_score(0), 0.0);
                EXPECT_GREATER(s.get_max_score(1), 0.0);
                EXPECT_GREATER(s.get_max_score(2), 0.0);
                wand::Terms terms = s.getTerms();
                std::sort(terms.begin(), terms.end(), WeightOrder());
                EXPECT_EQUAL(120, terms[0].weight);
                EXPECT_EQUAL(3u, terms[0].estHits);
                EXPECT_EQUAL(0u, terms[0].maxScore); // NB: not set
                EXPECT_EQUAL(130, terms[1].weight);
                EXPECT_EQUAL(1u, terms[1].estHits);
                EXPECT_EQUAL(0u, terms[1].maxScore); // NB: not set
                EXPECT_EQUAL(140, terms[2].weight);
                EXPECT_EQUAL(2u, terms[2].estHits);
                EXPECT_EQUAL(0u, terms[2].maxScore); // NB: not set
            }
            {
                wa.fetchPostings(ExecuteInfo::FALSE);
                SearchIterator::UP search = wa.createSearch(*md, false);
                EXPECT_TRUE(dynamic_cast<WeakAndSearch*>(search.get()) != 0);
                EXPECT_TRUE(search->seek(1));
                EXPECT_TRUE(search->seek(2));
                EXPECT_FALSE(search->seek(3));
                EXPECT_TRUE(search->seek(4));
                EXPECT_TRUE(search->seek(5));
                EXPECT_FALSE(search->seek(6));
            }
        }
    }
}

TEST("require_that_unpack_of_or_over_multisearch_is_optimized") {
    Blueprint::UP child1(
               ap((new OrBlueprint())->
                       addChild(ap(MyLeafSpec(20).addField(1,1).create())).
                       addChild(ap(MyLeafSpec(20).addField(2,2).create())).
                       addChild(ap(MyLeafSpec(10).addField(3,3).create()))));
    Blueprint::UP child2(
               ap((new OrBlueprint())->
                       addChild(ap(MyLeafSpec(20).addField(4,4).create())).
                       addChild(ap(MyLeafSpec(20).addField(5,5).create())).
                       addChild(ap(MyLeafSpec(10).addField(6,6).create()))));
    Blueprint::UP top_up(
               ap((new OrBlueprint())->
                       addChild(std::move(child1)).
                       addChild(std::move(child2))));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(2)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(4)->tagAsNotNeeded();
    md->resolveTermField(6)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(5)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::NoUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
}

TEST("require_that_unpack_of_or_is_optimized") {
    Blueprint::UP top_up(
               ap((new OrBlueprint())->
                       addChild(ap(MyLeafSpec(20).addField(1,1).create())).
                       addChild(ap(MyLeafSpec(20).addField(2,2).create())).
                       addChild(ap(MyLeafSpec(10).addField(3,3).create()))));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(2)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::OrLikeSearch<false, search::queryeval::NoUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
}

TEST("require_that_unpack_of_and_is_optimized") {
    Blueprint::UP top_up(
               ap((new AndBlueprint())->
                       addChild(ap(MyLeafSpec(20).addField(1,1).create())).
                       addChild(ap(MyLeafSpec(20).addField(2,2).create())).
                       addChild(ap(MyLeafSpec(10).addField(3,3).create()))));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(2)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::NoUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
}

TEST("require_that_unpack_optimization_is_honoured_by_parents") {
    Blueprint::UP top_up(
               ap((new AndBlueprint())->
                   addChild(ap((new OrBlueprint())->
                       addChild(ap(MyLeafSpec(20).addField(1,1).create())).
                       addChild(ap(MyLeafSpec(20).addField(2,2).create())).
                       addChild(ap(MyLeafSpec(10).addField(3,3).create()))))));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(2)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::FullUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::NoUnpack>",
                 top_up->createSearch(*md, false)->getClassName());
}

namespace {

SimpleStringTerm
makeTerm(const std::string & term)
{
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

}

TEST("require that children does not optimize when parents refuse them to") {
    FakeRequestContext requestContext;
    search::diskindex::TestDiskIndex index;
    std::filesystem::create_directory(std::filesystem::path("index"));
    index.buildSchema();
    index.openIndex("index/1", false, true, false, false, false);
    FieldSpecBaseList fields;
    fields.add(FieldSpecBase(1, 11));
    fields.add(FieldSpecBase(2, 22));
    search::fef::MatchDataLayout subLayout;
    search::fef::TermFieldHandle idxth21 = subLayout.allocTermField(2);
    search::fef::TermFieldHandle idxth22 = subLayout.allocTermField(2);
    search::fef::TermFieldHandle idxth1 = subLayout.allocTermField(1);
    Blueprint::UP top_up(
            ap((new EquivBlueprint(fields, subLayout))->
               addTerm(index.getIndex().createBlueprint(requestContext,
                                                        FieldSpec("f2", 2, idxth22, true),
                                                        makeTerm("w2")),
                       1.0).
               addTerm(index.getIndex().createBlueprint(requestContext,
                                                        FieldSpec("f1", 1, idxth1),
                                                        makeTerm("w1")),
                       1.0).
               addTerm(index.getIndex().createBlueprint(requestContext,
                                                        FieldSpec("f2", 2, idxth21), makeTerm("w2")),
                       1.0)));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    SearchIterator::UP search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const MultiSearch & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::BitVectorIteratorStrictT<false>", e.getChildren()[0]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[1]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[2]->getClassName());
    }

    md->resolveTermField(12)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const MultiSearch & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::BitVectorIteratorStrictT<false>", e.getChildren()[0]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[1]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[2]->getClassName());
    }
}

TEST("require_that_unpack_optimization_is_not_overruled_by_equiv") {
    FieldSpecBaseList fields;
    fields.add(FieldSpecBase(1, 1));
    fields.add(FieldSpecBase(2, 2));
    fields.add(FieldSpecBase(3, 3));
    search::fef::MatchDataLayout subLayout;
    search::fef::TermFieldHandle idxth1 = subLayout.allocTermField(1);
    search::fef::TermFieldHandle idxth2 = subLayout.allocTermField(2);
    search::fef::TermFieldHandle idxth3 = subLayout.allocTermField(3);
    Blueprint::UP top_up(
            ap((new EquivBlueprint(fields, subLayout))->
               addTerm(ap((new OrBlueprint())->
                          addChild(ap(MyLeafSpec(20).addField(1,idxth1).create())).
                          addChild(ap(MyLeafSpec(20).addField(2,idxth2).create())).
                          addChild(ap(MyLeafSpec(10).addField(3,idxth3).create()))),
                       1.0)));
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    top_up->fetchPostings(ExecuteInfo::FALSE);
    SearchIterator::UP search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const MultiSearch & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::(anonymous namespace)::FullUnpack>",
                     e.getChildren()[0]->getClassName());
    }

    md->resolveTermField(2)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const MultiSearch & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                     e.getChildren()[0]->getClassName());
    }

    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const MultiSearch & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::NoUnpack>",
                     e.getChildren()[0]->getClassName());
    }
}

TEST("require that children of near are not optimized") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new NearBlueprint(10))->
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(20).create())).
                           addChild(ap(MyLeafSpec(0, true).create())))).
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(0, true).create())).
                           addChild(ap(MyLeafSpec(30).create()))))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new NearBlueprint(10))->
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(20).create())).
                           addChild(ap(MyLeafSpec(0, true).create())))).
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(0, true).create())).
                           addChild(ap(MyLeafSpec(30).create()))))));
    //-------------------------------------------------------------------------
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that children of onear are not optimized") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new ONearBlueprint(10))->
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(20).create()->estimate(20))).
                           addChild(ap(MyLeafSpec(0, true).create()->estimate(0, true))))).
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(0, true).create()->estimate(0, true))).
                           addChild(ap(MyLeafSpec(30).create()->estimate(30)))))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new ONearBlueprint(10))->
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(20).create())).
                           addChild(ap(MyLeafSpec(0, true).create())))).
               addChild(ap((new OrBlueprint())->
                           addChild(ap(MyLeafSpec(0, true).create())).
                           addChild(ap(MyLeafSpec(30).create()))))));
    //-------------------------------------------------------------------------
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that ANDNOT without children is optimized to empty search") {
    Blueprint::UP top_up(new AndNotBlueprint());
    Blueprint::UP expect_up(new EmptyBlueprint());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that highest cost tier sorts last for OR") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new OrBlueprint())->
               addChild(ap(MyLeafSpec(50).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(3).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(10).cost_tier(1).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new OrBlueprint())->
               addChild(ap(MyLeafSpec(50).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(10).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(3).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that highest cost tier sorts last for AND") {
    //-------------------------------------------------------------------------
    Blueprint::UP top_up(
            ap((new AndBlueprint())->
               addChild(ap(MyLeafSpec(10).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(3).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(50).cost_tier(1).create()))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect_up(
            ap((new AndBlueprint())->
               addChild(ap(MyLeafSpec(10).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(50).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(3).create()))));
    //-------------------------------------------------------------------------
    EXPECT_NOT_EQUAL(expect_up->asString(), top_up->asString());
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
    expect_up = Blueprint::optimize(std::move(expect_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that intermediate cost tier is minimum cost tier of children") {
    Blueprint::UP bp1(
            ap((new AndBlueprint())->
               addChild(ap(MyLeafSpec(10).cost_tier(1).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(3).create()))));
    Blueprint::UP bp2(
            ap((new AndBlueprint())->
               addChild(ap(MyLeafSpec(10).cost_tier(3).create())).
               addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
               addChild(ap(MyLeafSpec(30).cost_tier(2).create()))));
    EXPECT_EQUAL(bp1->getState().cost_tier(), 1u);
    EXPECT_EQUAL(bp2->getState().cost_tier(), 2u);
}

void verify_or_est(const std::vector<Blueprint::HitEstimate> &child_estimates, Blueprint::HitEstimate expect) {
    OrBlueprint my_or;
    my_or.setDocIdLimit(32);
    auto my_est = my_or.combine(child_estimates);
    EXPECT_EQUAL(my_est.empty, expect.empty);
    EXPECT_EQUAL(my_est.estHits, expect.estHits);
}

TEST("require that OR blueprint use saturated sum as estimate") {
    TEST_DO(verify_or_est({{0, true},{0, true},{0, true}}, {0, true}));
    TEST_DO(verify_or_est({{0, true},{0, false},{0, true}}, {0, false}));
    TEST_DO(verify_or_est({{4, false},{6, false},{5, false}}, {15, false}));
    TEST_DO(verify_or_est({{5, false},{20, false},{10, false}}, {32, false}));
    TEST_DO(verify_or_est({{100, false},{300, false},{200, false}}, {300, false}));
}

TEST_MAIN() { TEST_DEBUG("lhs.out", "rhs.out"); TEST_RUN_ALL(); }
