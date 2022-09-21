// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_dfw.h"

namespace search::docsummary {

class RankFeaturesDFW : public SimpleDFW
{
public:
    RankFeaturesDFW();
    RankFeaturesDFW(const RankFeaturesDFW &) = delete;
    RankFeaturesDFW & operator=(const RankFeaturesDFW &) = delete;
    ~RankFeaturesDFW() override;
    bool isGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const override;
};

}
