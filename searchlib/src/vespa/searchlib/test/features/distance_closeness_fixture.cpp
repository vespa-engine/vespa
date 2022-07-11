// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_closeness_fixture.h"

namespace search::features::test {

FeatureDumpFixture::~FeatureDumpFixture() = default;

DistanceClosenessFixture::DistanceClosenessFixture(size_t fooCnt, size_t barCnt, const Labels &labels, const vespalib::string &featureName)
    : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
      mdl(), match_data(), rankProgram(), fooHandles(), barHandles()
{
    for (size_t i = 0; i < fooCnt; ++i) {
        uint32_t fieldId = indexEnv.getFieldByName("foo")->id();
        fooHandles.push_back(mdl.allocTermField(fieldId));
        SimpleTermData term;
        term.setUniqueId(i + 1);
        term.addField(fieldId).setHandle(fooHandles.back());
        queryEnv.getTerms().push_back(term);
    }
    for (size_t i = 0; i < barCnt; ++i) {
        uint32_t fieldId = indexEnv.getFieldByName("bar")->id();
        barHandles.push_back(mdl.allocTermField(fieldId));
        SimpleTermData term;
        term.setUniqueId(fooCnt + i + 1);
        term.addField(fieldId).setHandle(barHandles.back());
        queryEnv.getTerms().push_back(term);
    }
    labels.inject(queryEnv.getProperties());
    rankSetup.setFirstPhaseRank(featureName);
    rankSetup.setIgnoreDefaultRankFeatures(true);
    ASSERT_TRUE(rankSetup.compile());
    match_data = mdl.createMatchData();
    rankProgram = rankSetup.create_first_phase_program();
    rankProgram->setup(*match_data, queryEnv);
}

}

