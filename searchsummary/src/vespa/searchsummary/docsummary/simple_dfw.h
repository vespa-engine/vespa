// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"

namespace search::docsummary {

/*
 * Abstract class for writing document summaries that don't need
 * access to a document retrieved from IDocsumStore.
 */
class SimpleDFW : public DocsumFieldWriter
{
public:
    virtual void insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const = 0;
    void insertField(uint32_t docid, const IDocsumStoreDocument*, GetDocsumsState& state, vespalib::slime::Inserter &target) const override;
};

}
