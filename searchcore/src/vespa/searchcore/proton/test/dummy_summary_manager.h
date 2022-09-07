// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/docsummary/isummarymanager.h>

namespace proton::test {

struct DummySummaryManager : public ISummaryManager
{
    ISummarySetup::SP
    createSummarySetup(const SummaryConfig &,
                       const JuniperrcConfig &,
                       const std::shared_ptr<const document::DocumentTypeRepo> &,
                       const std::shared_ptr<search::IAttributeManager> &) override {
        return {};
    }
};

}
