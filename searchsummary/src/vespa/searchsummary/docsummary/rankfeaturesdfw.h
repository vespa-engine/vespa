// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "summaryfeaturesdfw.h"

namespace search::docsummary {

class RankFeaturesDFW : public FeaturesDFW
{
private:
    RankFeaturesDFW(const RankFeaturesDFW &);
    RankFeaturesDFW & operator=(const RankFeaturesDFW &);

    IDocsumEnvironment * _env;

public:
    RankFeaturesDFW();
    ~RankFeaturesDFW();
    void init(IDocsumEnvironment * env);
    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) override;
};

}
