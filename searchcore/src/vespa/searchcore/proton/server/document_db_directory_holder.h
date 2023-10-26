// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_db_config_owner.h"

namespace proton {

/*
 * class holding onto a document db directory.
 */
class DocumentDBDirectoryHolder
{
public:
    DocumentDBDirectoryHolder();
    ~DocumentDBDirectoryHolder();
    static void waitUntilDestroyed(const std::weak_ptr<DocumentDBDirectoryHolder> &holder);
};

} // namespace proton
