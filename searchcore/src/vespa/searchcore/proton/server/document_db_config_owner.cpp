// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_config_owner.h"
#include "document_db_directory_holder.h"

namespace proton {

DocumentDBConfigOwner::DocumentDBConfigOwner()
    : _holder(std::make_shared<DocumentDBDirectoryHolder>())
{
}

DocumentDBConfigOwner::~DocumentDBConfigOwner() = default;

std::shared_ptr<DocumentDBDirectoryHolder>
DocumentDBConfigOwner::getDocumentDBDirectoryHolder()
{
    return _holder;
};

} // namespace proton
