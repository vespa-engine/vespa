// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstdint>

namespace proton {

class IDocumentDBReferenceRegistry;
class MaintenanceJobTokenSource;

namespace matching { class SessionManager; }

class IDocumentDBOwner
{
public:
    using SessionManager = matching::SessionManager;
    virtual ~IDocumentDBOwner();

    virtual bool isInitializing() const = 0;
    virtual uint32_t getDistributionKey() const = 0;
    virtual uint32_t getNumThreadsPerSearch() const = 0;
    virtual SessionManager & session_manager() = 0;
    virtual std::shared_ptr<MaintenanceJobTokenSource> get_lid_space_compaction_job_token_source() = 0;
    virtual std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const = 0;
};

} // namespace proton
