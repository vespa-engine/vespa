// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clear_imported_attribute_search_cache_job.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>

using search::attribute::ImportedAttributeVector;

namespace proton {

ClearImportedAttributeSearchCacheJob::ClearImportedAttributeSearchCacheJob(std::shared_ptr<IAttributeManager> mgr,
                                                                           vespalib::duration visibilityDelay)
    : IMaintenanceJob("clear_imported_attribute_search_cache", visibilityDelay, visibilityDelay),
      _mgr(std::move(mgr))
{
}

bool
ClearImportedAttributeSearchCacheJob::run()
{
    auto* imported_attributes_repo = _mgr->getImportedAttributes();
    if (imported_attributes_repo != nullptr) {
        std::vector<std::shared_ptr<ImportedAttributeVector>> importedAttrs;
        imported_attributes_repo->getAll(importedAttrs);
        for (const auto &attr : importedAttrs) {
            attr->clearSearchCache();
        }
    }
    return true;
}

void
ClearImportedAttributeSearchCacheJob::onStop()
{
}

}
