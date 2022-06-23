// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "summaryfeaturesdfw.h"

namespace search::docsummary {

class RankFeaturesDFW : public ISimpleDFW
{
private:
    IDocsumEnvironment * _env;

public:
    RankFeaturesDFW(IDocsumEnvironment * env);
    RankFeaturesDFW(const RankFeaturesDFW &) = delete;
    RankFeaturesDFW & operator=(const RankFeaturesDFW &) = delete;
    ~RankFeaturesDFW() override;
    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) override;
};

}
