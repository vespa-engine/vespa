// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_dfw.h"

namespace search::docsummary {

/*
 * Class for writing empty document summaries.
 */
class EmptyDFW : public SimpleDFW
{
public:
    EmptyDFW();
    ~EmptyDFW() override;

    bool IsGenerated() const override { return true; }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) override;
};

}
