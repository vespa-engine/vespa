// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

class SimpleDFW : public DocsumFieldWriter
{
public:
    virtual void insertField(uint32_t docid, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) = 0;
    void insertField(uint32_t docid, GeneralResult *, GetDocsumsState *state, ResType type, vespalib::slime::Inserter &target) override;
};

}
