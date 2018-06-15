// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/distancetopathfeature.h>
#include <vespa/searchlib/features/termdistancefeature.h>
#include <vespa/searchlib/fef/test/ftlib.h>

class Test : public FtTestApp
{
public:
    Test();
    ~Test();
    int Main() override;
    void testFramework();
    void testFtLib();
    void testAge();
    void testAttribute();
    void testAttributeMatch();
    void testCloseness();
    void testMatchCount();
    void testDistance();
    void testDistanceToPath();
    void testDotProduct();
    void testFieldLength();
    void testFieldMatch();
    void testFieldTermMatch();
    void testFirstPhase();
    void testForeach();
    void testFreshness();
    void testMatch();
    void testMatches();
    void testNow();
    void testQuery();
    void testQueryTermCount();
    void testRandom();
    void testRandomNormal();
    void testRandomNormalStable();
    void testRankingExpression();
    void testTerm();
    void testTermDistance();
    void testUtils();

private:
    void testFieldMatchBluePrint();
    void testFieldMatchExecutor();
    void testFieldMatchExecutorOutOfOrder();
    void testFieldMatchExecutorSegments();
    void testFieldMatchExecutorGaps();
    void testFieldMatchExecutorHead();
    void testFieldMatchExecutorTail();
    void testFieldMatchExecutorLongestSequence();
    void testFieldMatchExecutorMatches();
    void testFieldMatchExecutorCompleteness();
    void testFieldMatchExecutorOrderness();
    void testFieldMatchExecutorRelatedness();
    void testFieldMatchExecutorLongestSequenceRatio();
    void testFieldMatchExecutorEarliness();
    void testFieldMatchExecutorWeight();
    void testFieldMatchExecutorSignificance();
    void testFieldMatchExecutorImportance();
    void testFieldMatchExecutorOccurrence();
    void testFieldMatchExecutorAbsoluteOccurrence();
    void testFieldMatchExecutorWeightedOccurrence();
    void testFieldMatchExecutorWeightedAbsoluteOccurrence();
    void testFieldMatchExecutorSignificantOccurrence();
    void testFieldMatchExecutorUnweightedProximity();
    void testFieldMatchExecutorReverseProximity();
    void testFieldMatchExecutorAbsoluteProximity();
    void testFieldMatchExecutorMultiSegmentProximity();
    void testFieldMatchExecutorSegmentDistance();
    void testFieldMatchExecutorSegmentProximity();
    void testFieldMatchExecutorSegmentStarts();
    void testFieldMatchExecutorMoreThanASegmentLengthOfUnmatchedQuery();
    void testFieldMatchExecutorQueryRepeats();
    void testFieldMatchExecutorZeroCases();
    void testFieldMatchExecutorExceedingIterationLimit();
    void testFieldMatchExecutorRemaining();

    void assertAge(feature_t expAge, const vespalib::string & attr, uint64_t now, uint64_t docTime);
    void setupForAgeTest(FtFeatureTest & ft, uint64_t docTime);
    void setupForAttributeTest(FtFeatureTest &ft, bool setup_env = true);
    void assertCloseness(feature_t exp, const vespalib::string & attr, double distance, double maxDistance = 0, double halfResponse = 0);
    void setupForDistanceTest(FtFeatureTest & ft, const vespalib::string & attrName,
                              const std::vector<std::pair<int32_t, int32_t> > & positions, bool zcurve);
    void assert2DZDistance(feature_t exp, const vespalib::string & positions,
                           int32_t xquery, int32_t yquery, uint32_t xAspect = 0);
    void assertDistanceToPath(const std::vector<std::pair<int32_t, int32_t> > pos, const vespalib::string &path,
                              feature_t distance = search::features::DistanceToPathExecutor::DEFAULT_DISTANCE,
                              feature_t traveled = 1, feature_t product = 0);
    void setupForDocumentTest(FtFeatureTest &ft, const vespalib::string & attrName, const vespalib::string & docType);
    void assertDotProduct(feature_t exp, const vespalib::string & vector, uint32_t docId = 1,
                          const vespalib::string & attribute = "wsstr", const vespalib::string & attributeOverride="");
    void setupForDotProductTest(FtFeatureTest & ft);
    void assertFieldMatch(const vespalib::string & spec, const vespalib::string & query, const vespalib::string & field,
                          const search::features::fieldmatch::Params * params = NULL, uint32_t totalTermWeight = 0, feature_t totalSignificance = 0.0f);
    void assertFieldMatch(const vespalib::string & spec, const vespalib::string & query, const vespalib::string & field,
                          uint32_t totalTermWeight);
    void assertFieldMatchTS(const vespalib::string & spec, const vespalib::string & query, const vespalib::string & field,
                            feature_t totalSignificance);
    vespalib::string getExpression(const vespalib::string &parameter) const;
    void assertForeachOperation(feature_t exp, const vespalib::string & cond, const vespalib::string & op);
    void assertFreshness(feature_t expFreshness, const vespalib::string & attr, uint32_t age, uint32_t maxAge = 0, double halfResponse = 0, bool logScale = false);
    bool assertTermDistance(const search::features::TermDistanceCalculator::Result & exp, const vespalib::string & query,
                            const vespalib::string & field, uint32_t docId = 1);
    bool assertMatches(uint32_t output, const vespalib::string & query, const vespalib::string & field,
                       const vespalib::string & feature = "matches(foo)", uint32_t docId = 1);

private:
    search::fef::BlueprintFactory _factory;
};

