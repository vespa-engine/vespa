// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/vdslib/container/documentsummary.h>

namespace storage {
namespace api {

/**
 * @class DocumentSummaryCommand
 * @ingroup message
 *
 * @brief The result of a searchvisitor.
 */
class DocumentSummaryCommand : public StorageCommand,
                               public vdslib::DocumentSummary
{
public:
    explicit DocumentSummaryCommand();
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(DocumentSummaryCommand, onDocumentSummary)
};

/**
 * @class DocumentSummaryReply
 * @ingroup message
 *
 * @brief Response to a document summary command.
 */
class DocumentSummaryReply : public StorageReply {
public:
    explicit DocumentSummaryReply(const DocumentSummaryCommand& command);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(DocumentSummaryReply, onDocumentSummaryReply)
};

} // api
} // storage
