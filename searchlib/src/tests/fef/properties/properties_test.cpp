// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <limits>

using namespace search::fef;
using namespace search::fef::indexproperties;

struct CopyVisitor : public IPropertiesVisitor
{
    Properties &dst;
    CopyVisitor(Properties &p) : dst(p) {}
    virtual void visitProperty(const Property::Value &key,
                               const Property &values) override
    {
        for (uint32_t i = 0; i < values.size(); ++i) {
            dst.add(key, values.getAt(i));
        }
    }
};

Properties make_props(std::initializer_list<std::pair<const char *, std::initializer_list<const char *> > > entries) {
    Properties props;
    for (const auto &entry: entries) {
        vespalib::string key = entry.first;
        for (vespalib::string value: entry.second) {
            props.add(key, value);
        }
    }
    return props;
}

TEST("require that namespace visitation works") {
    Properties props = make_props({    {"foo",   {"outside"}},
                                       {"foo.a", {"a_value"}},
                                       {"foo.b", {"b_value"}},
                                       {"foo.",  {"outside"}}
                                   });
    Properties result;
    CopyVisitor copy_visitor(result);
    props.visitNamespace("foo", copy_visitor);
    EXPECT_EQUAL(2u, result.numKeys());
    EXPECT_EQUAL(result.lookup("a").get(), Property::Value("a_value"));
    EXPECT_EQUAL(result.lookup("b").get(), Property::Value("b_value"));
}

TEST("test stuff") {
    { // empty lookup result
        Property p;

        EXPECT_EQUAL(p.found(), false);
        EXPECT_EQUAL(p.get(), Property::Value(""));
        EXPECT_EQUAL(p.get("fb"), Property::Value("fb"));
        EXPECT_EQUAL(p.size(), 0u);
        EXPECT_EQUAL(p.getAt(0), Property::Value(""));
    }
    { // add / count / remove
        Properties p = make_props({    {"a", {"a1", "a2", "a3"}},
                                       {"b", {"b1", "b2"}},
                                       {"c", {"c1"}}
                                   });
        const Properties &pc = p;

        EXPECT_EQUAL(pc.numKeys(), 3u);
        EXPECT_EQUAL(pc.numValues(), 6u);
        EXPECT_EQUAL(pc.count("a"), 3u);
        EXPECT_EQUAL(pc.count("b"), 2u);
        EXPECT_EQUAL(pc.count("c"), 1u);
        EXPECT_EQUAL(pc.count("d"), 0u);

        p.remove("d");

        EXPECT_EQUAL(pc.numKeys(), 3u);
        EXPECT_EQUAL(pc.numValues(), 6u);
        EXPECT_EQUAL(pc.count("a"), 3u);
        EXPECT_EQUAL(pc.count("b"), 2u);
        EXPECT_EQUAL(pc.count("c"), 1u);
        EXPECT_EQUAL(pc.count("d"), 0u);

        p.remove("c");

        EXPECT_EQUAL(pc.numKeys(), 2u);
        EXPECT_EQUAL(pc.numValues(), 5u);
        EXPECT_EQUAL(pc.count("a"), 3u);
        EXPECT_EQUAL(pc.count("b"), 2u);
        EXPECT_EQUAL(pc.count("c"), 0u);
        EXPECT_EQUAL(pc.count("d"), 0u);

        p.remove("b");

        EXPECT_EQUAL(pc.numKeys(), 1u);
        EXPECT_EQUAL(pc.numValues(), 3u);
        EXPECT_EQUAL(pc.count("a"), 3u);
        EXPECT_EQUAL(pc.count("b"), 0u);
        EXPECT_EQUAL(pc.count("c"), 0u);
        EXPECT_EQUAL(pc.count("d"), 0u);

        p.remove("a");

        EXPECT_EQUAL(pc.numKeys(), 0u);
        EXPECT_EQUAL(pc.numValues(), 0u);
        EXPECT_EQUAL(pc.count("a"), 0u);
        EXPECT_EQUAL(pc.count("b"), 0u);
        EXPECT_EQUAL(pc.count("c"), 0u);
        EXPECT_EQUAL(pc.count("d"), 0u);
    }
    { // lookup / import / visit / compare / hash
        Properties p;

        p.add("x",       "x1");
        p.add("a.x",     "x2");
        p.add("a.b.x",   "x3");
        p.add("a.b.c.x", "x4");

        p.add("list", "e1").add("list", "e2").add("list", "e3");

        EXPECT_EQUAL(p.numKeys(), 5u);
        EXPECT_EQUAL(p.numValues(), 7u);

        EXPECT_EQUAL(p.lookup("x").found(),       true);
        EXPECT_EQUAL(p.lookup("a.x").found(),     true);
        EXPECT_EQUAL(p.lookup("a.b.x").found(),   true);
        EXPECT_EQUAL(p.lookup("a.b.c.x").found(), true);
        EXPECT_EQUAL(p.lookup("list").found(),    true);
        EXPECT_EQUAL(p.lookup("y").found(),       false);

        EXPECT_EQUAL(p.lookup("x").get(),       Property::Value("x1"));
        EXPECT_EQUAL(p.lookup("a.x").get(),     Property::Value("x2"));
        EXPECT_EQUAL(p.lookup("a.b.x").get(),   Property::Value("x3"));
        EXPECT_EQUAL(p.lookup("a.b.c.x").get(), Property::Value("x4"));
        EXPECT_EQUAL(p.lookup("list").get(),    Property::Value("e1"));
        EXPECT_EQUAL(p.lookup("y").get(),       Property::Value(""));

        EXPECT_EQUAL(p.lookup("x").get(),                Property::Value("x1"));
        EXPECT_EQUAL(p.lookup("a", "x").get(),           Property::Value("x2"));
        EXPECT_EQUAL(p.lookup("a", "b", "x").get(),      Property::Value("x3"));
        EXPECT_EQUAL(p.lookup("a", "b", "c", "x").get(), Property::Value("x4"));

        EXPECT_EQUAL(p.lookup("x").get("fallback"), Property::Value("x1"));
        EXPECT_EQUAL(p.lookup("y").get("fallback"), Property::Value("fallback"));

        EXPECT_EQUAL(p.lookup("y").size(), 0u);
        EXPECT_EQUAL(p.lookup("x").size(), 1u);
        EXPECT_EQUAL(p.lookup("list").size(), 3u);
        EXPECT_EQUAL(p.lookup("list").getAt(0), Property::Value("e1"));
        EXPECT_EQUAL(p.lookup("list").getAt(1), Property::Value("e2"));
        EXPECT_EQUAL(p.lookup("list").getAt(2), Property::Value("e3"));
        EXPECT_EQUAL(p.lookup("list").getAt(3), Property::Value(""));

        Properties p2;

        p2.add("x", "new_x");
        p2.add("y", "y1");
        p2.add("list", "foo").add("list", "bar");

        EXPECT_EQUAL(p2.numKeys(), 3u);
        EXPECT_EQUAL(p2.numValues(), 4u);

        p.import(p2);

        EXPECT_EQUAL(p.numKeys(), 6u);
        EXPECT_EQUAL(p.numValues(), 7u);

        EXPECT_EQUAL(p.lookup("y").size(), 1u);
        EXPECT_EQUAL(p.lookup("y").get(), Property::Value("y1"));

        EXPECT_EQUAL(p.lookup("x").size(), 1u);
        EXPECT_EQUAL(p.lookup("x").get(), Property::Value("new_x"));

        EXPECT_EQUAL(p.lookup("z").size(), 0u);

        EXPECT_EQUAL(p.lookup("a", "x").size(), 1u);
        EXPECT_EQUAL(p.lookup("a", "x").get(), Property::Value("x2"));

        EXPECT_EQUAL(p.lookup("list").size(), 2u);
        EXPECT_EQUAL(p.lookup("list").getAt(0), Property::Value("foo"));
        EXPECT_EQUAL(p.lookup("list").getAt(1), Property::Value("bar"));
        EXPECT_EQUAL(p.lookup("list").getAt(2), Property::Value(""));

        Properties p3;

        EXPECT_TRUE(!(p  == p2));
        EXPECT_TRUE(!(p  == p3));
        EXPECT_TRUE(!(p2 == p));
        EXPECT_TRUE(!(p3 == p));
        EXPECT_TRUE(!(p2 == p3));
        EXPECT_TRUE(!(p3 == p2));

        CopyVisitor cv(p3);
        p.visitProperties(cv);

        EXPECT_EQUAL(p3.numKeys(), 6u);
        EXPECT_EQUAL(p3.numValues(), 7u);

        EXPECT_TRUE(p == p3);
        EXPECT_TRUE(p3 == p);
        EXPECT_EQUAL(p.hashCode(), p3.hashCode());

        p.clear();
        EXPECT_EQUAL(p.numKeys(), 0u);
        EXPECT_EQUAL(p.numValues(), 0u);
        EXPECT_TRUE(!(p == p3));
        EXPECT_TRUE(!(p3 == p));

        Properties p4;
        CopyVisitor cv2(p4);
        p.visitProperties(cv);
        EXPECT_EQUAL(p4.numKeys(), 0u);
        EXPECT_EQUAL(p4.numValues(), 0u);
        EXPECT_TRUE(p == p4);
        EXPECT_TRUE(p4 == p);
        EXPECT_EQUAL(p.hashCode(), p4.hashCode());
    }

    { // test index properties known by the framework
        { // vespa.eval.lazy_expressions
            EXPECT_EQUAL(eval::LazyExpressions::NAME, vespalib::string("vespa.eval.lazy_expressions"));
            {
                Properties p;
                EXPECT_TRUE(eval::LazyExpressions::check(p, true));
                EXPECT_TRUE(!eval::LazyExpressions::check(p, false));
            }
            {
                Properties p;
                p.add("vespa.eval.lazy_expressions", "true");
                EXPECT_TRUE(eval::LazyExpressions::check(p, true));
                EXPECT_TRUE(eval::LazyExpressions::check(p, false));
            }
            {
                Properties p;
                p.add("vespa.eval.lazy_expressions", "false");
                EXPECT_TRUE(!eval::LazyExpressions::check(p, true));
                EXPECT_TRUE(!eval::LazyExpressions::check(p, false));
            }
        }
        { // vespa.eval.use_fast_forest
            EXPECT_EQUAL(eval::UseFastForest::NAME, vespalib::string("vespa.eval.use_fast_forest"));
            EXPECT_EQUAL(eval::UseFastForest::DEFAULT_VALUE, false);
            Properties p;
            EXPECT_EQUAL(eval::UseFastForest::check(p), false);
            p.add("vespa.eval.use_fast_forest", "true");
            EXPECT_EQUAL(eval::UseFastForest::check(p), true);
        }
        { // vespa.rank.firstphase
            EXPECT_EQUAL(rank::FirstPhase::NAME, vespalib::string("vespa.rank.firstphase"));
            EXPECT_EQUAL(rank::FirstPhase::DEFAULT_VALUE, vespalib::string("nativeRank"));
            Properties p;
            EXPECT_EQUAL(rank::FirstPhase::lookup(p), vespalib::string("nativeRank"));
            p.add("vespa.rank.firstphase", "specialrank");
            EXPECT_EQUAL(rank::FirstPhase::lookup(p), vespalib::string("specialrank"));
        }
        { // vespa.rank.secondphase
            EXPECT_EQUAL(rank::SecondPhase::NAME, vespalib::string("vespa.rank.secondphase"));
            EXPECT_EQUAL(rank::SecondPhase::DEFAULT_VALUE, vespalib::string(""));
            Properties p;
            EXPECT_EQUAL(rank::SecondPhase::lookup(p), vespalib::string(""));
            p.add("vespa.rank.secondphase", "specialrank");
            EXPECT_EQUAL(rank::SecondPhase::lookup(p), vespalib::string("specialrank"));
        }
        { // vespa.dump.feature
            EXPECT_EQUAL(dump::Feature::NAME, vespalib::string("vespa.dump.feature"));
            EXPECT_EQUAL(dump::Feature::DEFAULT_VALUE.size(), 0u);
            Properties p;
            EXPECT_EQUAL(dump::Feature::lookup(p).size(), 0u);
            p.add("vespa.dump.feature", "foo");
            p.add("vespa.dump.feature", "bar");
            std::vector<vespalib::string> a = dump::Feature::lookup(p);
            ASSERT_TRUE(a.size() == 2);
            EXPECT_EQUAL(a[0], vespalib::string("foo"));
            EXPECT_EQUAL(a[1], vespalib::string("bar"));
        }
        { // vespa.dump.ignoredefaultfeatures
            EXPECT_EQUAL(dump::IgnoreDefaultFeatures::NAME, vespalib::string("vespa.dump.ignoredefaultfeatures"));
            EXPECT_EQUAL(dump::IgnoreDefaultFeatures::DEFAULT_VALUE, "false");
            Properties p;
            EXPECT_TRUE(!dump::IgnoreDefaultFeatures::check(p));
            p.add("vespa.dump.ignoredefaultfeatures", "true");
            EXPECT_TRUE(dump::IgnoreDefaultFeatures::check(p));
        }
        { // vespa.matching.termwise_limit
            EXPECT_EQUAL(matching::TermwiseLimit::NAME, vespalib::string("vespa.matching.termwise_limit"));
            EXPECT_EQUAL(matching::TermwiseLimit::DEFAULT_VALUE, 1.0);
            Properties p;
            EXPECT_EQUAL(matching::TermwiseLimit::lookup(p), 1.0);
            p.add("vespa.matching.termwise_limit", "0.05");
            EXPECT_EQUAL(matching::TermwiseLimit::lookup(p), 0.05);
        }
        { // vespa.matching.numthreads
            EXPECT_EQUAL(matching::NumThreadsPerSearch::NAME, vespalib::string("vespa.matching.numthreadspersearch"));
            EXPECT_EQUAL(matching::NumThreadsPerSearch::DEFAULT_VALUE, std::numeric_limits<uint32_t>::max());
            Properties p;
            EXPECT_EQUAL(matching::NumThreadsPerSearch::lookup(p), std::numeric_limits<uint32_t>::max());
            p.add("vespa.matching.numthreadspersearch", "50");
            EXPECT_EQUAL(matching::NumThreadsPerSearch::lookup(p), 50u);
        }

        { // vespa.matching.minhitsperthread
            EXPECT_EQUAL(matching::MinHitsPerThread::NAME, vespalib::string("vespa.matching.minhitsperthread"));
            EXPECT_EQUAL(matching::MinHitsPerThread::DEFAULT_VALUE, 0u);
            Properties p;
            EXPECT_EQUAL(matching::MinHitsPerThread::lookup(p), 0u);
            p.add("vespa.matching.minhitsperthread", "50");
            EXPECT_EQUAL(matching::MinHitsPerThread::lookup(p), 50u);
        }
        {
            EXPECT_EQUAL(matching::NumSearchPartitions::NAME, vespalib::string("vespa.matching.numsearchpartitions"));
            EXPECT_EQUAL(matching::NumSearchPartitions::DEFAULT_VALUE, 1u);
            Properties p;
            EXPECT_EQUAL(matching::NumSearchPartitions::lookup(p), 1u);
            p.add("vespa.matching.numsearchpartitions", "50");
            EXPECT_EQUAL(matching::NumSearchPartitions::lookup(p), 50u);
        }
        { // vespa.matchphase.degradation.attribute
            EXPECT_EQUAL(matchphase::DegradationAttribute::NAME, vespalib::string("vespa.matchphase.degradation.attribute"));
            EXPECT_EQUAL(matchphase::DegradationAttribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationAttribute::lookup(p), "");
            p.add("vespa.matchphase.degradation.attribute", "foobar");
            EXPECT_EQUAL(matchphase::DegradationAttribute::lookup(p), "foobar");
        }
        { // vespa.matchphase.degradation.ascending
            EXPECT_EQUAL(matchphase::DegradationAscendingOrder::NAME, vespalib::string("vespa.matchphase.degradation.ascendingorder"));
            EXPECT_EQUAL(matchphase::DegradationAscendingOrder::DEFAULT_VALUE, false);
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationAscendingOrder::lookup(p), false);
            p.add("vespa.matchphase.degradation.ascendingorder", "true");
            EXPECT_EQUAL(matchphase::DegradationAscendingOrder::lookup(p), true);
        }
        { // vespa.matchphase.degradation.maxhits
            EXPECT_EQUAL(matchphase::DegradationMaxHits::NAME, vespalib::string("vespa.matchphase.degradation.maxhits"));
            EXPECT_EQUAL(matchphase::DegradationMaxHits::DEFAULT_VALUE, 0u);
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationMaxHits::lookup(p), 0u);
            p.add("vespa.matchphase.degradation.maxhits", "123789");
            EXPECT_EQUAL(matchphase::DegradationMaxHits::lookup(p), 123789u);
        }
        { // vespa.matchphase.degradation.samplepercentage
            EXPECT_EQUAL(matchphase::DegradationSamplePercentage::NAME, vespalib::string("vespa.matchphase.degradation.samplepercentage"));
            EXPECT_EQUAL(matchphase::DegradationSamplePercentage::DEFAULT_VALUE, 0.2);
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationSamplePercentage::lookup(p), 0.2);
            p.add("vespa.matchphase.degradation.samplepercentage", "0.9");
            EXPECT_EQUAL(matchphase::DegradationSamplePercentage::lookup(p), 0.9);
        }
        { // vespa.matchphase.degradation.maxfiltercoverage
            EXPECT_EQUAL(matchphase::DegradationMaxFilterCoverage::NAME, vespalib::string("vespa.matchphase.degradation.maxfiltercoverage"));
            EXPECT_EQUAL(matchphase::DegradationMaxFilterCoverage::DEFAULT_VALUE, 0.2);
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationMaxFilterCoverage::lookup(p), 0.2);
            p.add("vespa.matchphase.degradation.maxfiltercoverage", "0.076");
            EXPECT_EQUAL(matchphase::DegradationMaxFilterCoverage::lookup(p), 0.076);
        }
        { // vespa.matchphase.degradation.postfiltermultiplier
            EXPECT_EQUAL(matchphase::DegradationPostFilterMultiplier::NAME, vespalib::string("vespa.matchphase.degradation.postfiltermultiplier"));
            EXPECT_EQUAL(matchphase::DegradationPostFilterMultiplier::DEFAULT_VALUE, 1.0);
            Properties p;
            EXPECT_EQUAL(matchphase::DegradationPostFilterMultiplier::lookup(p), 1.0);
            p.add("vespa.matchphase.degradation.postfiltermultiplier", "0.9");
            EXPECT_EQUAL(matchphase::DegradationPostFilterMultiplier::lookup(p), 0.9);
        }
        { // vespa.matchphase.diversity.attribute
            EXPECT_EQUAL(matchphase::DiversityAttribute::NAME, vespalib::string("vespa.matchphase.diversity.attribute"));
            EXPECT_EQUAL(matchphase::DiversityAttribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(matchphase::DiversityAttribute::lookup(p), "");
            p.add("vespa.matchphase.diversity.attribute", "foobar");
            EXPECT_EQUAL(matchphase::DiversityAttribute::lookup(p), "foobar");
        }
        { // vespa.matchphase.diversity.mingroups
            EXPECT_EQUAL(matchphase::DiversityMinGroups::NAME, vespalib::string("vespa.matchphase.diversity.mingroups"));
            EXPECT_EQUAL(matchphase::DiversityMinGroups::DEFAULT_VALUE, 1u);
            Properties p;
            EXPECT_EQUAL(matchphase::DiversityMinGroups::lookup(p), 1u);
            p.add("vespa.matchphase.diversity.mingroups", "5");
            EXPECT_EQUAL(matchphase::DiversityMinGroups::lookup(p), 5u);
        }
        { // vespa.hitcollector.heapsize
            EXPECT_EQUAL(hitcollector::HeapSize::NAME, vespalib::string("vespa.hitcollector.heapsize"));
            EXPECT_EQUAL(hitcollector::HeapSize::DEFAULT_VALUE, 100u);
            Properties p;
            EXPECT_EQUAL(hitcollector::HeapSize::lookup(p), 100u);
            p.add("vespa.hitcollector.heapsize", "50");
            EXPECT_EQUAL(hitcollector::HeapSize::lookup(p), 50u);
        }
        { // vespa.hitcollector.arraysize
            EXPECT_EQUAL(hitcollector::ArraySize::NAME, vespalib::string("vespa.hitcollector.arraysize"));
            EXPECT_EQUAL(hitcollector::ArraySize::DEFAULT_VALUE, 10000u);
            Properties p;
            EXPECT_EQUAL(hitcollector::ArraySize::lookup(p), 10000u);
            p.add("vespa.hitcollector.arraysize", "50");
            EXPECT_EQUAL(hitcollector::ArraySize::lookup(p), 50u);
        }
        { // vespa.hitcollector.estimatepoint
            EXPECT_EQUAL(hitcollector::EstimatePoint::NAME, vespalib::string("vespa.hitcollector.estimatepoint"));
            EXPECT_EQUAL(hitcollector::EstimatePoint::DEFAULT_VALUE, 0xffffffffu);
            Properties p;
            EXPECT_EQUAL(hitcollector::EstimatePoint::lookup(p), 0xffffffffu);
            p.add("vespa.hitcollector.estimatepoint", "50");
            EXPECT_EQUAL(hitcollector::EstimatePoint::lookup(p), 50u);
        }
        { // vespa.hitcollector.estimatelimit
            EXPECT_EQUAL(hitcollector::EstimateLimit::NAME, vespalib::string("vespa.hitcollector.estimatelimit"));
            EXPECT_EQUAL(hitcollector::EstimateLimit::DEFAULT_VALUE, 0xffffffffu);
            Properties p;
            EXPECT_EQUAL(hitcollector::EstimateLimit::lookup(p), 0xffffffffu);
            p.add("vespa.hitcollector.estimatelimit", "50");
            EXPECT_EQUAL(hitcollector::EstimateLimit::lookup(p), 50u);
        }
        { // vespa.hitcollector.rankscoredroplimit
            EXPECT_EQUAL(hitcollector::RankScoreDropLimit::NAME, vespalib::string("vespa.hitcollector.rankscoredroplimit"));
            search::feature_t got1 = hitcollector::RankScoreDropLimit::DEFAULT_VALUE;
            EXPECT_TRUE(got1 != got1);
            Properties p;
            search::feature_t got2= hitcollector::RankScoreDropLimit::lookup(p);
            EXPECT_TRUE(got2 != got2);
            p.add("vespa.hitcollector.rankscoredroplimit", "-123456789.12345");
            EXPECT_EQUAL(hitcollector::RankScoreDropLimit::lookup(p), -123456789.12345);
            p.clear().add("vespa.hitcollector.rankscoredroplimit", "123456789.12345");
            EXPECT_EQUAL(hitcollector::RankScoreDropLimit::lookup(p), 123456789.12345);
        }
        { // vespa.fieldweight.
            EXPECT_EQUAL(FieldWeight::BASE_NAME, vespalib::string("vespa.fieldweight."));
            EXPECT_EQUAL(FieldWeight::DEFAULT_VALUE, 100u);
            Properties p;
            EXPECT_EQUAL(FieldWeight::lookup(p, "foo"), 100u);
            p.add("vespa.fieldweight.foo", "200");
            EXPECT_EQUAL(FieldWeight::lookup(p, "foo"), 200u);
        }
        { // vespa.isfilterfield.
            EXPECT_EQUAL(IsFilterField::BASE_NAME, "vespa.isfilterfield.");
            EXPECT_EQUAL(IsFilterField::DEFAULT_VALUE, "false");
            Properties p;
            EXPECT_TRUE(!IsFilterField::check(p, "foo"));
            p.add("vespa.isfilterfield.foo", "true");
            EXPECT_TRUE(IsFilterField::check(p, "foo"));
            EXPECT_TRUE(!IsFilterField::check(p, "bar"));
            IsFilterField::set(p, "bar");
            EXPECT_TRUE(IsFilterField::check(p, "bar"));
        }
        {
            EXPECT_EQUAL(mutate::on_match::Attribute::NAME, vespalib::string("vespa.mutate.on_match.attribute"));
            EXPECT_EQUAL(mutate::on_match::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_match::Attribute::lookup(p), "");
            p.add("vespa.mutate.on_match.attribute", "foobar");
            EXPECT_EQUAL(mutate::on_match::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(mutate::on_match::Operation::NAME, vespalib::string("vespa.mutate.on_match.operation"));
            EXPECT_EQUAL(mutate::on_match::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_match::Operation::lookup(p), "");
            p.add("vespa.mutate.on_match.operation", "+=1");
            EXPECT_EQUAL(mutate::on_match::Operation::lookup(p), "+=1");
        }
        {
            EXPECT_EQUAL(mutate::on_first_phase::Attribute::NAME, vespalib::string("vespa.mutate.on_first_phase.attribute"));
            EXPECT_EQUAL(mutate::on_first_phase::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_first_phase::Attribute::lookup(p), "");
            p.add("vespa.mutate.on_first_phase.attribute", "foobar");
            EXPECT_EQUAL(mutate::on_first_phase::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(mutate::on_first_phase::Operation::NAME, vespalib::string("vespa.mutate.on_first_phase.operation"));
            EXPECT_EQUAL(mutate::on_first_phase::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_first_phase::Operation::lookup(p), "");
            p.add("vespa.mutate.on_first_phase.operation", "+=1");
            EXPECT_EQUAL(mutate::on_first_phase::Operation::lookup(p), "+=1");
        }
        {
            EXPECT_EQUAL(mutate::on_second_phase::Attribute::NAME, vespalib::string("vespa.mutate.on_second_phase.attribute"));
            EXPECT_EQUAL(mutate::on_second_phase::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_second_phase::Attribute::lookup(p), "");
            p.add("vespa.mutate.on_second_phase.attribute", "foobar");
            EXPECT_EQUAL(mutate::on_second_phase::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(mutate::on_second_phase::Operation::NAME, vespalib::string("vespa.mutate.on_second_phase.operation"));
            EXPECT_EQUAL(mutate::on_second_phase::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_second_phase::Operation::lookup(p), "");
            p.add("vespa.mutate.on_second_phase.operation", "+=1");
            EXPECT_EQUAL(mutate::on_second_phase::Operation::lookup(p), "+=1");
        }
        {
            EXPECT_EQUAL(mutate::on_summary::Attribute::NAME, vespalib::string("vespa.mutate.on_summary.attribute"));
            EXPECT_EQUAL(mutate::on_summary::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_summary::Attribute::lookup(p), "");
            p.add("vespa.mutate.on_summary.attribute", "foobar");
            EXPECT_EQUAL(mutate::on_summary::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(mutate::on_summary::Operation::NAME, vespalib::string("vespa.mutate.on_summary.operation"));
            EXPECT_EQUAL(mutate::on_summary::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(mutate::on_summary::Operation::lookup(p), "");
            p.add("vespa.mutate.on_summary.operation", "+=1");
            EXPECT_EQUAL(mutate::on_summary::Operation::lookup(p), "+=1");
        }
        {
            EXPECT_EQUAL(execute::onmatch::Attribute::NAME, vespalib::string("vespa.execute.onmatch.attribute"));
            EXPECT_EQUAL(execute::onmatch::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onmatch::Attribute::lookup(p), "");
            p.add("vespa.execute.onmatch.attribute", "foobar");
            EXPECT_EQUAL(execute::onmatch::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(execute::onmatch::Operation::NAME, vespalib::string("vespa.execute.onmatch.operation"));
            EXPECT_EQUAL(execute::onmatch::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onmatch::Operation::lookup(p), "");
            p.add("vespa.execute.onmatch.operation", "++");
            EXPECT_EQUAL(execute::onmatch::Operation::lookup(p), "++");
        }
        {
            EXPECT_EQUAL(execute::onrerank::Attribute::NAME, vespalib::string("vespa.execute.onrerank.attribute"));
            EXPECT_EQUAL(execute::onrerank::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onrerank::Attribute::lookup(p), "");
            p.add("vespa.execute.onrerank.attribute", "foobar");
            EXPECT_EQUAL(execute::onrerank::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(execute::onrerank::Operation::NAME, vespalib::string("vespa.execute.onrerank.operation"));
            EXPECT_EQUAL(execute::onrerank::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onrerank::Operation::lookup(p), "");
            p.add("vespa.execute.onrerank.operation", "++");
            EXPECT_EQUAL(execute::onrerank::Operation::lookup(p), "++");
        }
        {
            EXPECT_EQUAL(execute::onsummary::Attribute::NAME, vespalib::string("vespa.execute.onsummary.attribute"));
            EXPECT_EQUAL(execute::onsummary::Attribute::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onsummary::Attribute::lookup(p), "");
            p.add("vespa.execute.onsummary.attribute", "foobar");
            EXPECT_EQUAL(execute::onsummary::Attribute::lookup(p), "foobar");
        }
        {
            EXPECT_EQUAL(execute::onsummary::Operation::NAME, vespalib::string("vespa.execute.onsummary.operation"));
            EXPECT_EQUAL(execute::onsummary::Operation::DEFAULT_VALUE, "");
            Properties p;
            EXPECT_EQUAL(execute::onsummary::Operation::lookup(p), "");
            p.add("vespa.execute.onsummary.operation", "++");
            EXPECT_EQUAL(execute::onsummary::Operation::lookup(p), "++");
        }
        {
            EXPECT_EQUAL(softtimeout::Enabled::NAME, vespalib::string("vespa.softtimeout.enable"));
            EXPECT_TRUE(softtimeout::Enabled::DEFAULT_VALUE);
            Properties p;
            p.add(softtimeout::Enabled::NAME, "false");
            EXPECT_FALSE(softtimeout::Enabled::lookup(p));
        }
        {
            EXPECT_EQUAL(softtimeout::Factor::NAME, vespalib::string("vespa.softtimeout.factor"));
            EXPECT_EQUAL(0.5, softtimeout::Factor::DEFAULT_VALUE);
            Properties p;
            p.add(softtimeout::Factor::NAME, "0.33");
            EXPECT_EQUAL(0.33, softtimeout::Factor::lookup(p));
        }
        {
            EXPECT_EQUAL(softtimeout::TailCost::NAME, vespalib::string("vespa.softtimeout.tailcost"));
            EXPECT_EQUAL(0.1, softtimeout::TailCost::DEFAULT_VALUE);
            Properties p;
            p.add(softtimeout::TailCost::NAME, "0.17");
            EXPECT_EQUAL(0.17, softtimeout::TailCost::lookup(p));
        }
    }
}

TEST("test attribute type properties")
{
    Properties p;
    p.add("vespa.type.attribute.foo", "tensor(x[10])");
    EXPECT_EQUAL("tensor(x[10])", type::Attribute::lookup(p, "foo"));
    EXPECT_EQUAL("", type::Attribute::lookup(p, "bar"));
}

TEST("test query feature type properties")
{
    Properties p;
    p.add("vespa.type.query.foo", "tensor(x[10])");
    EXPECT_EQUAL("tensor(x[10])", type::QueryFeature::lookup(p, "foo"));
    EXPECT_EQUAL("", type::QueryFeature::lookup(p, "bar"));
}


TEST_MAIN() { TEST_RUN_ALL(); }
