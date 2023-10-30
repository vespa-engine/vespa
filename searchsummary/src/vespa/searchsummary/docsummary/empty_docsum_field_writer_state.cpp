// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_docsum_field_writer_state.h"

using vespalib::slime::Inserter;

namespace search::docsummary {

EmptyDocsumFieldWriterState::EmptyDocsumFieldWriterState() = default;

EmptyDocsumFieldWriterState::~EmptyDocsumFieldWriterState() = default;

void
EmptyDocsumFieldWriterState::insertField(uint32_t, Inserter&)
{
}

}
