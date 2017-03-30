// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "document_db_maintenance_config.h"

namespace proton {

DocumentDBPruneConfig::
DocumentDBPruneConfig(void)
    : _interval(21600.0),
      _age(1209600.0)
{
}

DocumentDBPruneConfig::
DocumentDBPruneConfig(double interval,
                      double age)
    : _interval(interval),
      _age(age)
{
}

bool
DocumentDBPruneConfig::
operator==(const DocumentDBPruneConfig &rhs) const
{
    return _interval == rhs._interval &&
                _age == rhs._age;
}

DocumentDBHeartBeatConfig::DocumentDBHeartBeatConfig(void)
    : _interval(60.0)
{
}

DocumentDBHeartBeatConfig::DocumentDBHeartBeatConfig(double interval)
    : _interval(interval)
{
}

bool
DocumentDBHeartBeatConfig::
operator==(const DocumentDBHeartBeatConfig &rhs) const
{
    return _interval == rhs._interval;
}

DocumentDBLidSpaceCompactionConfig::DocumentDBLidSpaceCompactionConfig()
    : _interval(3600),
      _allowedLidBloat(1000000000),
      _allowedLidBloatFactor(1.0),
      _disabled(false),
      _maxDocsToScan(10000)
{
}

DocumentDBLidSpaceCompactionConfig::DocumentDBLidSpaceCompactionConfig(double interval,
                                                                       uint32_t allowedLidBloat,
                                                                       double allowedLidBloatFactor,
                                                                       bool disabled,
                                                                       uint32_t maxDocsToScan)
    : _interval(interval),
      _allowedLidBloat(allowedLidBloat),
      _allowedLidBloatFactor(allowedLidBloatFactor),
      _disabled(disabled),
      _maxDocsToScan(maxDocsToScan)
{
}

DocumentDBLidSpaceCompactionConfig
DocumentDBLidSpaceCompactionConfig::createDisabled()
{
    DocumentDBLidSpaceCompactionConfig result;
    result._disabled = true;
    return result;
}

bool
DocumentDBLidSpaceCompactionConfig::operator==(const DocumentDBLidSpaceCompactionConfig &rhs) const
{
   return _interval == rhs._interval &&
           _allowedLidBloat == rhs._allowedLidBloat &&
           _allowedLidBloatFactor == rhs._allowedLidBloatFactor &&
           _disabled == rhs._disabled;
}

DocumentDBMaintenanceConfig::DocumentDBMaintenanceConfig(void)
    : _pruneRemovedDocuments(),
      _heartBeat(),
      _sessionCachePruneInterval(900.0),
      _visibilityDelay(0),
      _lidSpaceCompaction(),
      _attributeUsageFilterConfig(),
      _attributeUsageSampleInterval(60.0),
      _resourceLimitFactor(1.0)
{
}

DocumentDBMaintenanceConfig::
DocumentDBMaintenanceConfig(const DocumentDBPruneRemovedDocumentsConfig &
                            pruneRemovedDocuments,
                            const DocumentDBHeartBeatConfig &heartBeat,
                            const DocumentDBWipeOldRemovedFieldsConfig &
                            wipeOldRemovedFields,
                            double groupingSessionPruneInterval,
                            fastos::TimeStamp visibilityDelay,
                            const DocumentDBLidSpaceCompactionConfig &lidSpaceCompaction,
                            const AttributeUsageFilterConfig &attributeUsageFilterConfig,
                            double attributeUsageSampleInterval,
                            double resourceLimitFactor)
    : _pruneRemovedDocuments(pruneRemovedDocuments),
      _heartBeat(heartBeat),
      _wipeOldRemovedFields(wipeOldRemovedFields),
      _sessionCachePruneInterval(groupingSessionPruneInterval),
      _visibilityDelay(visibilityDelay),
      _lidSpaceCompaction(lidSpaceCompaction),
      _attributeUsageFilterConfig(attributeUsageFilterConfig),
      _attributeUsageSampleInterval(attributeUsageSampleInterval),
      _resourceLimitFactor(resourceLimitFactor)
{
}

bool
DocumentDBMaintenanceConfig::
operator==(const DocumentDBMaintenanceConfig &rhs) const
{
    return _pruneRemovedDocuments == rhs._pruneRemovedDocuments &&
        _heartBeat == rhs._heartBeat &&
        _wipeOldRemovedFields == rhs._wipeOldRemovedFields &&
        _sessionCachePruneInterval == rhs._sessionCachePruneInterval &&
        _visibilityDelay == rhs._visibilityDelay &&
        _lidSpaceCompaction == rhs._lidSpaceCompaction &&
        _attributeUsageFilterConfig == rhs._attributeUsageFilterConfig &&
        _attributeUsageSampleInterval == rhs._attributeUsageSampleInterval &&
        _resourceLimitFactor == rhs._resourceLimitFactor;
}

} // namespace proton
