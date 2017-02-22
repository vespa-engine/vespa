// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <string>
#include <vespa/searchsummary/docsummary/summaryfeaturesdfw.h>

namespace search {
namespace docsummary {

class RankFeaturesDFW : public FeaturesDFW
{
private:
    RankFeaturesDFW(const RankFeaturesDFW &);
    RankFeaturesDFW & operator=(const RankFeaturesDFW &);

    IDocsumEnvironment * _env;

public:
    RankFeaturesDFW();
    virtual ~RankFeaturesDFW();
    void init(IDocsumEnvironment * env);
    virtual bool IsGenerated() const { return true; }
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target);
};

}
}

