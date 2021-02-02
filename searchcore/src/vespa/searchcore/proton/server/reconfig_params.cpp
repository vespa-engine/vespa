// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
            _res.onnxModelsChanged ||
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
            _res.maintenanceChanged ||
            _res.storeChanged ||
            _res.alloc_config_changed;
}

bool
ReconfigParams::shouldSchemaChange() const
{
    return _res.schemaChanged;
}

bool
ReconfigParams::shouldMatchersChange() const
{
    return _res.rankProfilesChanged || _res.rankingConstantsChanged || _res.onnxModelsChanged || shouldSchemaChange();
}

bool
ReconfigParams::shouldIndexManagerChange() const
{
    return _res.indexschemaChanged;
}

bool
ReconfigParams::shouldAttributeManagerChange() const
{
    return _res.attributesChanged || _res.importedFieldsChanged || _res.visibilityDelayChanged || _res.alloc_config_changed;
}

bool
ReconfigParams::shouldSummaryManagerChange() const
{
    return  _res.summaryChanged || _res.summarymapChanged || _res.juniperrcChanged
            || _res.documentTypeRepoChanged || _res.documenttypesChanged || _res.storeChanged;
}

bool
ReconfigParams::shouldSubDbsChange() const
{
    return shouldMatchersChange() || shouldAttributeManagerChange() || shouldSummaryManagerChange()
           || _res.documentTypeRepoChanged || _res.documenttypesChanged || _res.storeChanged || _res.flushChanged;
}

bool
ReconfigParams::shouldMaintenanceControllerChange() const
{
    return configHasChanged();
}

bool
ReconfigParams::shouldAttributeWriterChange() const
{
    return shouldAttributeManagerChange() || _res.documentTypeRepoChanged;
}

} // namespace proton
