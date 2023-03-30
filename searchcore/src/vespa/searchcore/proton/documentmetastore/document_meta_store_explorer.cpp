// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_meta_store_explorer.h"
#include "documentmetastore.h"
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>

using search::StateExplorerUtils;
using search::attribute::Status;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

DocumentMetaStoreExplorer::DocumentMetaStoreExplorer(IDocumentMetaStoreContext::IReadGuard::UP metaStore)
    : _metaStore(std::move(metaStore))
{
}

void
DocumentMetaStoreExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    if (full) {
        auto dms = dynamic_cast<const DocumentMetaStore*>(&_metaStore->get());
        if (dms != nullptr) {
            const Status &status = dms->getStatus();
            StateExplorerUtils::status_to_slime(status, object.setObject("status"));
        }
        search::LidUsageStats stats = _metaStore->get().getLidUsageStats();
        object.setLong("usedLids", stats.getUsedLids());
        object.setLong("activeLids", _metaStore->get().getNumActiveLids());
        object.setLong("lidLimit", stats.getLidLimit());
        object.setLong("lowestFreeLid", stats.getLowestFreeLid());
        object.setLong("highestUsedLid", stats.getHighestUsedLid());
        object.setLong("lidBloat", stats.getLidBloat());
        object.setDouble("lidBloatFactor", stats.getLidBloatFactor());
    } else {
        object.setLong("usedLids", _metaStore->get().getNumUsedLids());
        object.setLong("activeLids", _metaStore->get().getNumActiveLids());
    }
}

} // namespace proton
