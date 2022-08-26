// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_dfw.h"

namespace search::docsummary {

class IDocsumEnvironment;

class RankFeaturesDFW : public SimpleDFW
{
private:
    IDocsumEnvironment * _env;

public:
    RankFeaturesDFW(IDocsumEnvironment * env);
    RankFeaturesDFW(const RankFeaturesDFW &) = delete;
    RankFeaturesDFW & operator=(const RankFeaturesDFW &) = delete;
    ~RankFeaturesDFW() override;
    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) const override;
};

}
