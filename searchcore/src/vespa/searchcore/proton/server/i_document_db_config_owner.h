// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/config/config-proton.h>

namespace proton {

class DocumentDBConfig;

/*
 * Interface class defining reconfigure method for a document db.
 */
class IDocumentDBConfigOwner
{
public:
    using ProtonConfig = vespa::config::search::core::ProtonConfig;
    virtual ~IDocumentDBConfigOwner() { }
    virtual void reconfigure(const std::shared_ptr<ProtonConfig> & protonConfig,
                             const std::shared_ptr<DocumentDBConfig> & config) = 0;
};

} // namespace proton
