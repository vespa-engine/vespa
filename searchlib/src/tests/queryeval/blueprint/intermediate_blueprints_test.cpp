// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mysearch.h"
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/multisearch.h>
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
using BlueprintVector = std::vector<std::unique_ptr<Blueprint>>;

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
    bool   is_strict;
    double hit_rate;

    RememberExecuteInfo() : MyLeaf(), is_strict(false), hit_rate(0.0) {}
    RememberExecuteInfo(FieldSpecBaseList fields) : MyLeaf(std::move(fields)), is_strict(false), hit_rate(0.0) {}

    void fetchPostings(const ExecuteInfo &execInfo) override {
        LeafBlueprint::fetchPostings(execInfo);
        is_strict = execInfo.is_strict();
        hit_rate = execInfo.hit_rate();
    }
};

Blueprint::UP ap(Blueprint *b) { return Blueprint::UP(b); }
Blueprint::UP ap(Blueprint &b) { return Blueprint::UP(&b); }

bool got_global_filter(Blueprint &b) {
    return (static_cast<MyLeaf &>(b)).got_global_filter();
}

void check_sort_order(IntermediateBlueprint &self, BlueprintVector children, std::vector<size_t> order) {
    ASSERT_EQUAL(children.size(), order.size());
    std::vector<const Blueprint *> unordered;
    for (const auto & child: children) {
        unordered.push_back(child.get());
    }
    self.sort(children);
    for (size_t i = 0; i < children.size(); ++i) {
        EXPECT_EQUAL(children[i].get(), unordered[order[i]]);
    }
}

std::vector<std::unique_ptr<Blueprint>>
createLeafs(std::initializer_list<uint32_t> estimates) {
    std::vector<std::unique_ptr<Blueprint>> leafs;
    for (auto estimate : estimates) {
        leafs.emplace_back(MyLeafSpec(estimate).create());
    }
    return leafs;
}

TEST("test AndNot Blueprint") {
    AndNotBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(5, false);
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
    check_sort_order(b, createLeafs({10, 20, 40, 30}), {0, 2, 3, 1});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(false, b.inheritStrict(1));
    EXPECT_EQUAL(false, b.inheritStrict(2));
    EXPECT_EQUAL(false, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

template <typename BP>
void optimize(std::unique_ptr<BP> &ref) {
    auto optimized = Blueprint::optimize(std::move(ref));
    ref.reset(dynamic_cast<BP*>(optimized.get()));
    ASSERT_TRUE(ref);
    optimized.release();
}

TEST("test And propagates updated histestimate") {
    auto bp = std::make_unique<AndBlueprint>();
    bp->setSourceId(2);
    bp->addChild(ap(MyLeafSpec(20).create<RememberExecuteInfo>()->setSourceId(2)));
    bp->addChild(ap(MyLeafSpec(200).create<RememberExecuteInfo>()->setSourceId(2)));
    bp->addChild(ap(MyLeafSpec(2000).create<RememberExecuteInfo>()->setSourceId(2)));
    optimize(bp);
    bp->setDocIdLimit(5000);
    bp->fetchPostings(ExecuteInfo::TRUE);
    EXPECT_EQUAL(3u, bp->childCnt());
    for (uint32_t i = 0; i < bp->childCnt(); i++) {
        const auto & child = dynamic_cast<const RememberExecuteInfo &>(bp->getChild(i));
        EXPECT_EQUAL((i == 0), child.is_strict);
    }
    EXPECT_EQUAL(1.0, dynamic_cast<const RememberExecuteInfo &>(bp->getChild(0)).hit_rate);
    EXPECT_EQUAL(1.0/250, dynamic_cast<const RememberExecuteInfo &>(bp->getChild(1)).hit_rate);
    EXPECT_EQUAL(1.0/(250*25), dynamic_cast<const RememberExecuteInfo &>(bp->getChild(2)).hit_rate);
}

TEST("test Or propagates updated histestimate") {
    auto bp = std::make_unique<OrBlueprint>();
    bp->setSourceId(2);
    bp->addChild(ap(MyLeafSpec(5000).create<RememberExecuteInfo>()->setSourceId(2)));
    bp->addChild(ap(MyLeafSpec(2000).create<RememberExecuteInfo>()->setSourceId(2)));
    bp->addChild(ap(MyLeafSpec(800).create<RememberExecuteInfo>()->setSourceId(2)));
    bp->addChild(ap(MyLeafSpec(20).create<RememberExecuteInfo>()->setSourceId(2)));
    optimize(bp);
    bp->setDocIdLimit(5000);
    bp->fetchPostings(ExecuteInfo::TRUE);
    EXPECT_EQUAL(4u, bp->childCnt());
    for (uint32_t i = 0; i < bp->childCnt(); i++) {
        const auto & child = dynamic_cast<const RememberExecuteInfo &>(bp->getChild(i));
        EXPECT_TRUE(child.is_strict);
    }
    EXPECT_EQUAL(1.0, dynamic_cast<const RememberExecuteInfo &>(bp->getChild(0)).hit_rate);
    EXPECT_APPROX(0.5, dynamic_cast<const RememberExecuteInfo &>(bp->getChild(1)).hit_rate, 1e-6);
    EXPECT_APPROX(0.5*3.0/5.0, dynamic_cast<const RememberExecuteInfo &>(bp->getChild(2)).hit_rate, 1e-6);
    EXPECT_APPROX(0.5*3.0*42.0/(5.0*50.0), dynamic_cast<const RememberExecuteInfo &>(bp->getChild(3)).hit_rate, 1e-6);
}

TEST("test And Blueprint") {
    AndBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.emplace_back(0, true);
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
    check_sort_order(b, createLeafs({20, 40, 10, 30}), {2, 0, 3, 1});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(false, b.inheritStrict(1));
    EXPECT_EQUAL(false, b.inheritStrict(2));
    EXPECT_EQUAL(false, b.inheritStrict(-1));

    // createSearch tested by iterator unit test
}

TEST("test Or Blueprint") {
    OrBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(0, true);
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
    check_sort_order(b, createLeafs({10, 20, 40, 30}), {2, 3, 1, 0});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(true, b.inheritStrict(1));
    EXPECT_EQUAL(true, b.inheritStrict(2));
    EXPECT_EQUAL(true, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

TEST("test Near Blueprint") {
    NearBlueprint b(7);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.emplace_back(0, true);
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
    }
    {
        NearBlueprint a(7);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    check_sort_order(b, createLeafs({40, 10, 30, 20}), {1, 3, 2, 0});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(false, b.inheritStrict(1));
    EXPECT_EQUAL(false, b.inheritStrict(2));
    EXPECT_EQUAL(false, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

TEST("test ONear Blueprint") {
    ONearBlueprint b(8);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(5u, b.combine(est).estHits);
        est.emplace_back(0, true);
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
    }
    {
        ONearBlueprint a(8);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    check_sort_order(b, createLeafs({20, 10, 40, 30}), {0, 1, 2, 3});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(false, b.inheritStrict(1));
    EXPECT_EQUAL(false, b.inheritStrict(2));
    EXPECT_EQUAL(false, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

TEST("test Rank Blueprint") {
    RankBlueprint b;
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(0, true);
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
    check_sort_order(b, createLeafs({20, 10, 40, 30}), {0, 1, 2, 3});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(false, b.inheritStrict(1));
    EXPECT_EQUAL(false, b.inheritStrict(2));
    EXPECT_EQUAL(false, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

TEST("test SourceBlender Blueprint") {
    auto selector = std::make_unique<InvalidSelector>(); // not needed here
    SourceBlenderBlueprint b(*selector);
    { // combine
        std::vector<Blueprint::HitEstimate> est;
        EXPECT_EQUAL(true, b.combine(est).empty);
        EXPECT_EQUAL(0u, b.combine(est).estHits);
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(0, true);
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
    check_sort_order(b, createLeafs({20, 10, 40, 30}), {0, 1, 2, 3});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(true, b.inheritStrict(1));
    EXPECT_EQUAL(true, b.inheritStrict(2));
    EXPECT_EQUAL(true, b.inheritStrict(-1));
    // createSearch tested by iterator unit test
}

std::unique_ptr<IntermediateBlueprint>
addLeafsWithSourceId(std::unique_ptr<IntermediateBlueprint> parent, std::initializer_list<std::pair<uint32_t, uint32_t>> list) {
    for (const auto & leaf : list) {
        parent->addChild(ap(MyLeafSpec(leaf.first).create()->setSourceId(leaf.second)));
    }
    return parent;
}

std::unique_ptr<IntermediateBlueprint>
addLeafsWithCostTier(std::unique_ptr<IntermediateBlueprint> parent, std::initializer_list<std::pair<uint32_t, uint32_t>> list) {
    for (const auto & leaf : list) {
        parent->addChild(ap(MyLeafSpec(leaf.first).create()->cost_tier(leaf.second)));
    }
    return parent;
}

std::unique_ptr<IntermediateBlueprint>
addLeafsWithSourceId(uint32_t sourceId, std::unique_ptr<IntermediateBlueprint> parent, std::initializer_list<std::pair<uint32_t, uint32_t>> list) {
    parent->setSourceId(sourceId);
    return addLeafsWithSourceId(std::move(parent), list);
}

void
addLeafs(IntermediateBlueprint & parent, std::initializer_list<uint32_t> estimates) {
    for (const auto & estimate : estimates) {
        parent.addChild(ap(MyLeafSpec(estimate).create()));
    }
}

struct EstimateWithStrict {
    EstimateWithStrict(uint32_t estimate, bool strict) noexcept : _estimate(estimate), _strict(strict) {}
    EstimateWithStrict(uint32_t estimate) noexcept : EstimateWithStrict(estimate, false) {}
    uint32_t _estimate;
    bool     _strict;
};

std::unique_ptr<IntermediateBlueprint>
addLeafs(std::unique_ptr<IntermediateBlueprint> parent, std::initializer_list<EstimateWithStrict> list) {
    for (const auto & leaf : list) {
        parent->addChild(ap(MyLeafSpec(leaf._estimate, leaf._strict).create()));
    }
    return parent;
}

struct SourceBlenderTestFixture {
    InvalidSelector selector_1; // the one
    InvalidSelector selector_2; // not the one
    void addChildrenForSBTest(IntermediateBlueprint & parent);
    void addChildrenForSimpleSBTest(IntermediateBlueprint & parent);
};

void
optimize_and_compare(Blueprint::UP top, Blueprint::UP expect) {
    EXPECT_NOT_EQUAL(expect->asString(), top->asString());
    top = Blueprint::optimize(std::move(top));
    EXPECT_EQUAL(expect->asString(), top->asString());
    expect = Blueprint::optimize(std::move(expect));
    EXPECT_EQUAL(expect->asString(), top->asString());
}

void SourceBlenderTestFixture::addChildrenForSBTest(IntermediateBlueprint & parent) {
    addLeafs(parent, {2,1,3});
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{200, 2}, {100, 1}, {300, 3}}));
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{20, 2}, {10, 1}, {30, 3}}));
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_2), {{10, 1}, {20, 2}}));
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{2000, 2}, {1000, 1}}));
}

void SourceBlenderTestFixture::addChildrenForSimpleSBTest(IntermediateBlueprint & parent) {
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{200, 2}, {100, 1}, {300, 3}}));
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{20, 2}, {10, 1}, {30, 3}}));
    parent.addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(selector_1), {{2000, 2}, {1000, 1}}));
}

TEST_F("test SourceBlender below AND partial optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<AndBlueprint>();
    f.addChildrenForSBTest(*top);

    auto expect = std::make_unique<AndBlueprint>();
    addLeafs(*expect, {1,2,3});
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_2), {{10, 1}, {20, 2}}));

    auto blender = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    blender->addChild(addLeafsWithSourceId(3, std::make_unique<AndBlueprint>(), {{30,  3}, {300, 3}}));
    blender->addChild(addLeafsWithSourceId(2, std::make_unique<AndBlueprint>(), {{20,   2}, {200,  2}, {2000, 2}}));
    blender->addChild(addLeafsWithSourceId(1, std::make_unique<AndBlueprint>(), {{10,   1}, {100,  1}, {1000, 1}}));
    expect->addChild(std::move(blender));

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST_F("test AND replaced by source blender after full optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<AndBlueprint>();
    f.addChildrenForSimpleSBTest(*top);

    auto expect = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    expect->addChild(addLeafsWithSourceId(3, std::make_unique<AndBlueprint>(), {{30,  3}, {300, 3}}));
    expect->addChild(addLeafsWithSourceId(2, std::make_unique<AndBlueprint>(), {{20,   2}, {200,  2}, {2000, 2}}));
    expect->addChild(addLeafsWithSourceId(1, std::make_unique<AndBlueprint>(), {{10,   1}, {100,  1}, {1000, 1}}));

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST_F("test SourceBlender below OR partial optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<OrBlueprint>();
    f.addChildrenForSBTest(*top);
    //-------------------------------------------------------------------------
    auto expect = std::make_unique<OrBlueprint>();
    auto blender = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    blender->addChild(addLeafsWithSourceId(3, std::make_unique<OrBlueprint>(), {{300, 3}, {30,  3}}));
    blender->addChild(addLeafsWithSourceId(2, std::make_unique<OrBlueprint>(), {{2000, 2}, {200,  2}, {20,   2}}));
    blender->addChild(addLeafsWithSourceId(1, std::make_unique<OrBlueprint>(), {{1000, 1}, {100,  1}, {10,   1}}));
    expect->addChild(std::move(blender));
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_2), {{10, 1}, {20, 2}}));
    addLeafs(*expect, {3, 2, 1});

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST_F("test OR replaced by source blender after full optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<OrBlueprint>();
    f.addChildrenForSimpleSBTest(*top);

    auto expect = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    expect->addChild(addLeafsWithSourceId(3, std::make_unique<OrBlueprint>(), {{300, 3}, {30,  3}}));
    expect->addChild(addLeafsWithSourceId(2, std::make_unique<OrBlueprint>(), {{2000, 2}, {200,  2}, {20,   2}}));
    expect->addChild(addLeafsWithSourceId(1, std::make_unique<OrBlueprint>(), {{1000, 1}, {100,  1}, {10,   1}}));

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST_F("test SourceBlender below AND_NOT optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<AndNotBlueprint>();
    top->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_1), {{42, 1}}));
    f.addChildrenForSBTest(*top);

    //-------------------------------------------------------------------------
    auto expect = std::make_unique<AndNotBlueprint>();
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_1), {{42, 1}}));
    auto blender = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    blender->addChild(addLeafsWithSourceId(3, std::make_unique<OrBlueprint>(), {{300, 3}, {30,  3}}));
    blender->addChild(addLeafsWithSourceId(2, std::make_unique<OrBlueprint>(), {{2000, 2}, {200,  2}, {20,   2}}));
    blender->addChild(addLeafsWithSourceId(1, std::make_unique<OrBlueprint>(), {{1000, 1}, {100,  1}, {10,   1}}));
    expect->addChild(std::move(blender));
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_2), {{10, 1}, {20, 2}}));
    addLeafs(*expect, {3, 2, 1});

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST_F("test SourceBlender below RANK optimization", SourceBlenderTestFixture) {
    auto top = std::make_unique<RankBlueprint>();
    top->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_1), {{42, 1}}));
    f.addChildrenForSBTest(*top);

    //-------------------------------------------------------------------------
    auto expect = std::make_unique<RankBlueprint>();
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_1), {{42, 1}}));
    addLeafs(*expect, {2, 1, 3});
    expect->addChild(addLeafsWithSourceId(std::make_unique<SourceBlenderBlueprint>(f.selector_2), {{10, 1}, {20, 2}}));
    auto blender = std::make_unique<SourceBlenderBlueprint>(f.selector_1);
    blender->addChild(addLeafsWithSourceId(3, std::make_unique<OrBlueprint>(), {{300, 3}, {30,  3}}));
    blender->addChild(addLeafsWithSourceId(2, std::make_unique<OrBlueprint>(), {{2000, 2}, {200,  2}, {20,   2}}));
    blender->addChild(addLeafsWithSourceId(1, std::make_unique<OrBlueprint>(), {{1000, 1}, {100,  1}, {10,   1}}));
    expect->addChild(std::move(blender));

    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("test empty root node optimization and safeness") {
    // tests leaf node elimination
    auto top1(ap(MyLeafSpec(0, true).create()));
    // tests intermediate node elimination
    auto top2 = addLeafs(std::make_unique<AndBlueprint>(), {{0, true}, 10, 20});
    // tests safety of empty AND_NOT child removal
    auto top3 = addLeafs(std::make_unique<AndNotBlueprint>(), {{0, true}, 10, 20});
    // tests safety of empty RANK child removal
    auto top4 = addLeafs(std::make_unique<RankBlueprint>(), {{0, true}, 10, 20});
    // tests safety of empty OR child removal
    auto top5 = addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, {0, true}, {0, true}});

    //-------------------------------------------------------------------------
    auto expect_up = std::make_unique<EmptyBlueprint>();
    EXPECT_EQUAL(expect_up->asString(), Blueprint::optimize(std::move(top1))->asString());
    EXPECT_EQUAL(expect_up->asString(), Blueprint::optimize(std::move(top2))->asString());
    EXPECT_EQUAL(expect_up->asString(), Blueprint::optimize(std::move(top3))->asString());
    EXPECT_EQUAL(expect_up->asString(), Blueprint::optimize(std::move(top4))->asString());
    EXPECT_EQUAL(expect_up->asString(), Blueprint::optimize(std::move(top5))->asString());
}

TEST("and with one empty child is optimized away") {
    auto selector = std::make_unique<InvalidSelector>();
    Blueprint::UP top = ap((new SourceBlenderBlueprint(*selector))->
                           addChild(ap(MyLeafSpec(10).create())).
                           addChild(addLeafs(std::make_unique<AndBlueprint>(), {{0, true}, 10, 20})));
    top = Blueprint::optimize(std::move(top));
    Blueprint::UP expect_up(ap((new SourceBlenderBlueprint(*selector))->
                          addChild(ap(MyLeafSpec(10).create())).
                          addChild(std::make_unique<EmptyBlueprint>())));
    EXPECT_EQUAL(expect_up->asString(), top->asString());
}

struct make {
    make(make &&) = delete;
    make &operator=(make &&) = delete;
    static constexpr uint32_t invalid_source = -1;
    uint32_t source_tag = invalid_source;
    std::unique_ptr<IntermediateBlueprint> making;
    make(std::unique_ptr<IntermediateBlueprint> making_in) : making(std::move(making_in)) {}
    operator std::unique_ptr<Blueprint>() && noexcept { return std::move(making); }
    make &&source(uint32_t source_id) && {
        source_tag = source_id;
        return std::move(*this);
    }
    make &&add(std::unique_ptr<Blueprint> child) && {
        if (source_tag != invalid_source) {
            child->setSourceId(source_tag);
            source_tag = invalid_source;
        }
        if (auto *weak_and = making->asWeakAnd()) {
            weak_and->addTerm(std::move(child), 1);
        } else {
            making->addChild(std::move(child));
        }
        return std::move(*this);
    }
    make &&leaf(uint32_t estimate) && {
        return std::move(*this).add(ap(MyLeafSpec(estimate).create()));
    }
    make &&leafs(std::initializer_list<uint32_t> estimates) && {
        for (uint32_t estimate: estimates) {
            std::move(*this).leaf(estimate);
        }
        return std::move(*this);
    }
    static make OR() { return make(std::make_unique<OrBlueprint>()); }
    static make AND() { return make(std::make_unique<AndBlueprint>()); }
    static make RANK() { return make(std::make_unique<RankBlueprint>()); }
    static make ANDNOT() { return make(std::make_unique<AndNotBlueprint>()); }
    static make SB(ISourceSelector &selector) { return make(std::make_unique<SourceBlenderBlueprint>(selector)); }
    static make NEAR(uint32_t window) { return make(std::make_unique<NearBlueprint>(window)); }
    static make ONEAR(uint32_t window) { return make(std::make_unique<ONearBlueprint>(window)); }
    static make WEAKAND(uint32_t n) { return make(std::make_unique<WeakAndBlueprint>(n)); }
};

TEST("AND AND collapsing") {
    Blueprint::UP top = make::AND().leafs({1,3,5}).add(make::AND().leafs({2,4}));
    Blueprint::UP expect = make::AND().leafs({1,2,3,4,5});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("OR OR collapsing") {
    Blueprint::UP top = make::OR().leafs({1,3,5}).add(make::OR().leafs({2,4}));
    Blueprint::UP expect = make::OR().leafs({5,4,3,2,1});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("AND_NOT AND_NOT collapsing") {
    Blueprint::UP top = make::ANDNOT().add(make::ANDNOT().leafs({1,3,5})).leafs({2,4});
    Blueprint::UP expect = make::ANDNOT().leafs({1,5,4,3,2});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("AND_NOT AND AND_NOT collapsing") {
    Blueprint::UP top = make::ANDNOT()
        .add(make::AND()
             .add(make::ANDNOT().leafs({1,5,6}))
             .leafs({3,2})
             .add(make::ANDNOT().leafs({4,8,9})))
        .leaf(7);
    Blueprint::UP expect = make::ANDNOT()
        .add(make::AND().leafs({1,2,3,4}))
        .leafs({9,8,7,6,5});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("AND_NOT AND AND_NOT collapsing into full source blender optimization") {
    InvalidSelector sel;
    Blueprint::UP top =
        make::ANDNOT()
        .add(make::AND()
             .add(make::ANDNOT()
                  .add(make::SB(sel)
                       .source(1).leaf(1)
                       .source(2).leaf(2))
                  .leaf(5))
             .add(make::SB(sel)
                  .source(1).leaf(3)
                  .source(2).leaf(4)))
        .leaf(6);
    Blueprint::UP expect =
        make::ANDNOT()
        .add(make::SB(sel)
             .source(1).add(make::AND()
                            .source(1).leaf(1)
                            .source(1).leaf(3))
             .source(2).add(make::AND()
                            .source(2).leaf(2)
                            .source(2).leaf(4)))
        .leafs({6,5});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("test single child optimization") {
    InvalidSelector selector;
    //-------------------------------------------------------------------------
    Blueprint::UP top = make::ANDNOT().add(make::AND().add(make::RANK().add(make::OR().add(make::SB(selector).source(2).add(make::RANK().leaf(42))))));
    //-------------------------------------------------------------------------
    Blueprint::UP expect = make::SB(selector).source(2).leaf(42);
    //-------------------------------------------------------------------------
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("test empty OR child optimization") {
    Blueprint::UP top = addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, 20, {0, true}, 10, {0, true}, 0, 30, {0, true}});
    Blueprint::UP expect = addLeafs(std::make_unique<OrBlueprint>(), {30, 20, 10, 0});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("test empty AND_NOT child optimization") {
    Blueprint::UP top = addLeafs(std::make_unique<AndNotBlueprint>(), {42, 20, {0, true}, 10, {0, true}, 0, 30, {0, true}});
    Blueprint::UP expect = addLeafs(std::make_unique<AndNotBlueprint>(), {42, 30, 20, 10, 0});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("test empty RANK child optimization") {
    Blueprint::UP top = addLeafs(std::make_unique<RankBlueprint>(), {42, 20, {0, true}, 10, {0, true}, 0, 30, {0, true}});
    Blueprint::UP expect = addLeafs(std::make_unique<RankBlueprint>(), {42, 20, 10, 0, 30});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("require that replaced blueprints retain source id") {
    //-------------------------------------------------------------------------
    // replace empty root with empty search
    Blueprint::UP top1_up(ap(MyLeafSpec(0, true).create()->setSourceId(13)));
    auto expect1_up = std::make_unique<EmptyBlueprint>();
    expect1_up->setSourceId(13);
    //-------------------------------------------------------------------------
    // replace self with single child
    Blueprint::UP top2_up(ap(dynamic_cast<AndBlueprint&>((new AndBlueprint())->setSourceId(42)).
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
        est.emplace_back(10, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(10u, b.combine(est).estHits);
        est.emplace_back(20, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(5, false);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
        est.emplace_back(0, true);
        EXPECT_EQUAL(false, b.combine(est).empty);
        EXPECT_EQUAL(20u, b.combine(est).estHits);
    }
    {
        WeakAndBlueprint a(1000);
        a.addChild(ap(MyLeafSpec(10).addField(1, 1).create()));
        EXPECT_EQUAL(0u, a.exposeFields().size());
    }
    check_sort_order(b, createLeafs({20, 10, 40, 30}), {0, 1, 2, 3});
    EXPECT_EQUAL(true, b.inheritStrict(0));
    EXPECT_EQUAL(true, b.inheritStrict(1));
    EXPECT_EQUAL(true, b.inheritStrict(2));
    EXPECT_EQUAL(true, b.inheritStrict(-1));
    {
        FieldSpec field("foo", 1, 1);
        FakeResult x = FakeResult().doc(1).doc(2).doc(5);
        FakeResult y = FakeResult().doc(2);
        FakeResult z = FakeResult().doc(1).doc(4);
        {
            WeakAndBlueprint wa(456);
            MatchData::UP md = MatchData::makeTestInstance(100, 10);
            wa.addTerm(std::make_unique<FakeBlueprint>(field, x), 120);
            wa.addTerm(std::make_unique<FakeBlueprint>(field, z), 140);
            wa.addTerm(std::make_unique<FakeBlueprint>(field, y), 130);
            {
                wa.fetchPostings(ExecuteInfo::TRUE);
                SearchIterator::UP search = wa.createSearch(*md, true);
                EXPECT_TRUE(dynamic_cast<WeakAndSearch*>(search.get()) != nullptr);
                auto &s = dynamic_cast<WeakAndSearch&>(*search);
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
                EXPECT_TRUE(dynamic_cast<WeakAndSearch*>(search.get()) != nullptr);
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
    return {term, "field", 0, search::query::Weight(0)};
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
        const auto & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::BitVectorIteratorStrictT<false>", e.getChildren()[0]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[1]->getClassName());
        EXPECT_EQUAL("search::diskindex::ZcRareWordPosOccIterator<true, false>", e.getChildren()[2]->getClassName());
    }

    md->resolveTermField(12)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const auto & e = dynamic_cast<const MultiSearch &>(*search);
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
        const auto & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::(anonymous namespace)::FullUnpack>",
                     e.getChildren()[0]->getClassName());
    }

    md->resolveTermField(2)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const auto & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::(anonymous namespace)::SelectiveUnpack>",
                     e.getChildren()[0]->getClassName());
    }

    md->resolveTermField(1)->tagAsNotNeeded();
    md->resolveTermField(3)->tagAsNotNeeded();
    search = top_up->createSearch(*md, true);
    EXPECT_EQUAL("search::queryeval::EquivImpl<true>", search->getClassName());
    {
        const auto & e = dynamic_cast<const MultiSearch &>(*search);
        EXPECT_EQUAL("search::queryeval::OrLikeSearch<true, search::queryeval::NoUnpack>",
                     e.getChildren()[0]->getClassName());
    }
}

TEST("require that children of near are not optimized") {
    auto top_up = ap((new NearBlueprint(10))->
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {20, {0, true}})).
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, 30})));
    auto expect_up = ap((new NearBlueprint(10))->
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {20, {0, true}})).
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, 30})));
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that children of onear are not optimized") {
    auto top_up = ap((new ONearBlueprint(10))->
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {20, {0, true}})).
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, 30})));
    auto expect_up = ap((new ONearBlueprint(10))->
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {20, {0, true}})).
            addChild(addLeafs(std::make_unique<OrBlueprint>(), {{0, true}, 30})));
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that ANDNOT without children is optimized to empty search") {
    Blueprint::UP top_up = std::make_unique<AndNotBlueprint>();
    auto expect_up = std::make_unique<EmptyBlueprint>();
    top_up = Blueprint::optimize(std::move(top_up));
    EXPECT_EQUAL(expect_up->asString(), top_up->asString());
}

TEST("require that highest cost tier sorts last for OR") {
    Blueprint::UP top = addLeafsWithCostTier(std::make_unique<OrBlueprint>(), {{50, 1}, {30, 3}, {20, 2}, {10, 1}});
    Blueprint::UP expect = addLeafsWithCostTier(std::make_unique<OrBlueprint>(), {{50, 1}, {10, 1}, {20, 2}, {30, 3}});
    optimize_and_compare(std::move(top), std::move(expect));
}

TEST("require that highest cost tier sorts last for AND") {
    Blueprint::UP top = addLeafsWithCostTier(std::make_unique<AndBlueprint>(), {{10, 1}, {20, 3}, {30, 2}, {50, 1}});
    Blueprint::UP expect = addLeafsWithCostTier(std::make_unique<AndBlueprint>(), {{10, 1}, {50, 1}, {30, 2}, {20, 3}});
    optimize_and_compare(std::move(top), std::move(expect));
}

template<typename BP>
void
verifyCostTierInheritance(uint8_t expected, uint8_t expected_reverse) {
    auto bp1 = std::make_unique<BP>();
    bp1->addChild(ap(MyLeafSpec(10).cost_tier(1).create())).
         addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
         addChild(ap(MyLeafSpec(30).cost_tier(3).create()));
    auto bp2 = std::make_unique<BP>();
    bp2->addChild(ap(MyLeafSpec(10).cost_tier(3).create())).
         addChild(ap(MyLeafSpec(20).cost_tier(2).create())).
         addChild(ap(MyLeafSpec(30).cost_tier(1).create()));
    EXPECT_EQUAL(bp1->getState().cost_tier(), expected);
    EXPECT_EQUAL(bp2->getState().cost_tier(), expected_reverse);
}

TEST("require that AND cost tier is minimum cost tier of children") {
    verifyCostTierInheritance<AndBlueprint>(1, 1);
}

TEST("require that OR cost tier is maximum cost tier of children") {
    verifyCostTierInheritance<OrBlueprint>(3, 3);
}

TEST("require that Rank cost tier is first childs cost tier") {
    verifyCostTierInheritance<RankBlueprint>(1, 3);
}

TEST("require that AndNot cost tier is first childs cost tier") {
    verifyCostTierInheritance<AndNotBlueprint>(1, 3);
}

struct MySourceBlender {
    InvalidSelector selector;
    SourceBlenderBlueprint sb;
    MySourceBlender() : selector(), sb(selector) {}
    IntermediateBlueprint &
    addChild(Blueprint::UP child) {
        return sb.addChild(std::move(child));
    }
    const Blueprint::State &getState() const {
        return sb.getState();
    }

};

TEST("require that SourceBlender cost tier is maximum cost tier of children") {
    verifyCostTierInheritance<MySourceBlender>(3, 3);
}

void
verify_or_est(const std::vector<Blueprint::HitEstimate> &child_estimates, Blueprint::HitEstimate expect) {
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

void verify_relative_estimate(make &&mk, double expect) {
    EXPECT_EQUAL(mk.making->estimate(), 0.0);
    Blueprint::UP bp = std::move(mk).leafs({200,300,950});
    bp->setDocIdLimit(1000);
    EXPECT_EQUAL(bp->estimate(), expect);
}

TEST("relative estimate for OR") {
    verify_relative_estimate(make::OR(), 1.0-0.8*0.7*0.5);
}

TEST("relative estimate for AND") {
    verify_relative_estimate(make::AND(), 0.2*0.3*0.5);
}

TEST("relative estimate for RANK") {
    verify_relative_estimate(make::RANK(), 0.2);
}

TEST("relative estimate for ANDNOT") {
    verify_relative_estimate(make::ANDNOT(), 0.2);
}

TEST("relative estimate for SB") {
    InvalidSelector sel;
    verify_relative_estimate(make::SB(sel), 1.0-0.8*0.7*0.5);
}

TEST("relative estimate for NEAR") {
    verify_relative_estimate(make::NEAR(1), 0.2*0.3*0.5);
}

TEST("relative estimate for ONEAR") {
    verify_relative_estimate(make::ONEAR(1), 0.2*0.3*0.5);
}

TEST("relative estimate for WEAKAND") {
    verify_relative_estimate(make::WEAKAND(1000), 1.0-0.8*0.7*0.5);
    verify_relative_estimate(make::WEAKAND(50), 0.05);
}

TEST_MAIN() { TEST_DEBUG("lhs.out", "rhs.out"); TEST_RUN_ALL(); }
