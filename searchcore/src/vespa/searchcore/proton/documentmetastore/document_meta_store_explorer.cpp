// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_meta_store_explorer.h"
#include <vespa/vespalib/data/slime/cursor.h>

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
