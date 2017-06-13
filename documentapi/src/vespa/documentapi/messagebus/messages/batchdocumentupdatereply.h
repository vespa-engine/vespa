// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "writedocumentreply.h"

namespace documentapi {

class BatchDocumentUpdateReply : public WriteDocumentReply
{
    /**
     * If all documents to update are found, this vector will be empty. If
     * one or more documents are not found, this vector will have the size of
     * the initial number of updates, with entries set to true where the
     * corresponding update was not found.
     */
    std::vector<bool> _documentsNotFound;
public:
    typedef std::unique_ptr<BatchDocumentUpdateReply> UP;
    typedef std::shared_ptr<BatchDocumentUpdateReply> SP;

    BatchDocumentUpdateReply();
    ~BatchDocumentUpdateReply();

    const std::vector<bool>& getDocumentsNotFound() const { return _documentsNotFound; }
    std::vector<bool>& getDocumentsNotFound() { return _documentsNotFound; }

    string toString() const override { return "batchdocumentupdatereply"; }
};

}
