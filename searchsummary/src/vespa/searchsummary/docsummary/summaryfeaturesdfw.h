// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsumfieldwriter.h"

namespace vespalib { class JSONStringer; }

namespace search::docsummary {

class IDocsumEnvironment;

class FeaturesDFW : public IDocsumFieldWriter
{
protected:
    void featureDump(vespalib::JSONStringer & json, const vespalib::stringref & name, double feature);
};

class SummaryFeaturesDFW : public FeaturesDFW
{
private:
    SummaryFeaturesDFW(const SummaryFeaturesDFW &);
    SummaryFeaturesDFW & operator=(const SummaryFeaturesDFW &);

    IDocsumEnvironment * _env;

public:
    SummaryFeaturesDFW();
    ~SummaryFeaturesDFW() override;
    void init(IDocsumEnvironment * env);
    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GeneralResult *gres, GetDocsumsState *state,
                     ResType type, vespalib::slime::Inserter &target) override;
};

}
