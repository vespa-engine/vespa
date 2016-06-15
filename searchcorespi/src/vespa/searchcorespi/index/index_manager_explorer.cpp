// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.index_manager_explorer");
#include "index_manager_explorer.h"

#include <vespa/vespalib/data/slime/cursor.h>

using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace searchcorespi {

IndexManagerExplorer::IndexManagerExplorer(IIndexManager::SP mgr)
    : _mgr(std::move(mgr))
{
}

void
IndexManagerExplorer::get_state(const Inserter &inserter, bool full) const
{
    (void) full;
    Cursor &object = inserter.insertObject();
    object.setLong("lastSerialNum", _mgr->getCurrentSerialNum());
}

} // namespace searchcorespi
