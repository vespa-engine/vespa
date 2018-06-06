// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::slime { class Inserter; }

namespace search::docsummary {

/*
 * A subclass of this class can be instantiated by a document field writer to
 * track extra state during handling of a document summary request and
 * insert the field value using that state.
 */
class DocsumFieldWriterState
{
public:
    virtual void insertField(uint32_t docId, vespalib::slime::Inserter &target) = 0;
    virtual ~DocsumFieldWriterState() = default;
};

}
