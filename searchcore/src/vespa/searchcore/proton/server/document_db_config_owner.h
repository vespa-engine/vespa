// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_db_config_owner.h"

namespace proton {

class DocumentDBDirectoryHolder;

/*
 * Abstract class meant to be a base class for DocumentDB where a
 * directory holder exists until the document db instance is
 * destroyed.
 */
class DocumentDBConfigOwner : public IDocumentDBConfigOwner
{
    std::shared_ptr<DocumentDBDirectoryHolder> _holder;
public:
    DocumentDBConfigOwner();
    ~DocumentDBConfigOwner() override;
    std::shared_ptr<DocumentDBDirectoryHolder> getDocumentDBDirectoryHolder();
};

} // namespace proton
