// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer_state.h"

namespace search::docsummary {

/*
 * Class used as fallback when no suitable field writer state could be
 * instantiated. insertField() is a noop.
 */
class EmptyDocsumFieldWriterState : public DocsumFieldWriterState
{
public:
    EmptyDocsumFieldWriterState();
    ~EmptyDocsumFieldWriterState() override;
    void insertField(uint32_t, vespalib::slime::Inserter&) override;
};

}
