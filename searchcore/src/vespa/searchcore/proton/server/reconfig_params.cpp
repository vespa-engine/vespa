// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.reconfig_params");
#include "reconfig_params.h"

namespace proton {

ReconfigParams::
ReconfigParams(const DocumentDBConfig::ComparisonResult &res)
    : _res(res)
{
}

bool
ReconfigParams::configHasChanged() const
{
    return _res.rankProfilesChanged ||
            _res.rankingConstantsChanged ||
            _res.indexschemaChanged ||
            _res.attributesChanged ||
            _res.summaryChanged ||
            _res.summarymapChanged ||
            _res.juniperrcChanged ||
            _res.documenttypesChanged ||
            _res.documentTypeRepoChanged ||
            _res.importedFieldsChanged ||
            _res.tuneFileDocumentDBChanged ||
            _res.schemaChanged ||
            _res.maintenanceChanged;
}

bool
ReconfigParams::shouldSchemaChange() const
{
    return _res.schemaChanged;
}

bool
ReconfigParams::shouldMatchersChange() const
{
    return _res.rankProfilesChanged || _res.rankingConstantsChanged || shouldSchemaChange();
}

bool
ReconfigParams::shouldIndexManagerChange() const
{
    return _res.indexschemaChanged;
}

bool
ReconfigParams::shouldAttributeManagerChange() const
{
    return _res.attributesChanged || _res.importedFieldsChanged;
}

bool
ReconfigParams::shouldSummaryManagerChange() const
{
    return _res.summaryChanged || _res.summarymapChanged || _res.juniperrcChanged;
}

bool
ReconfigParams::shouldSubDbsChange() const
{
    return shouldMatchersChange()
           || shouldAttributeManagerChange()
           || shouldSummaryManagerChange();
}

bool
ReconfigParams::shouldMaintenanceControllerChange() const
{
    return configHasChanged();
}

} // namespace proton
