// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/docsummary/isummarymanager.h>

namespace proton {

namespace test {

struct DummySummaryManager : public ISummaryManager
{
    virtual ISummarySetup::SP
    createSummarySetup(const vespa::config::search::SummaryConfig &,
                       const vespa::config::search::SummarymapConfig &,
                       const vespa::config::search::summary::JuniperrcConfig &,
                       const std::shared_ptr<const document::DocumentTypeRepo> &,
                       const std::shared_ptr<search::IAttributeManager> &) override {
        return ISummarySetup::SP();
    }
};

} // namespace test

} // namespace proton

