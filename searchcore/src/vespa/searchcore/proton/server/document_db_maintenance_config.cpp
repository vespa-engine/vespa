// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_maintenance_config.h"

namespace proton {

constexpr vespalib::duration MAX_DELAY_SEC = 300s;

DocumentDBPruneConfig::DocumentDBPruneConfig() noexcept
    : _delay(MAX_DELAY_SEC),
      _interval(21600s),
      _age(1209600s)
{
}

DocumentDBPruneConfig::
DocumentDBPruneConfig(vespalib::duration interval, vespalib::duration age) noexcept
    : _delay(std::min(MAX_DELAY_SEC, interval)),
      _interval(interval),
      _age(age)
{
}

bool
DocumentDBPruneConfig::operator==(const DocumentDBPruneConfig &rhs) const noexcept
{
    return _delay == rhs._delay &&
           _interval == rhs._interval &&
           _age == rhs._age;
}

DocumentDBHeartBeatConfig::DocumentDBHeartBeatConfig() noexcept
    : _interval(60s)
{
}

DocumentDBHeartBeatConfig::DocumentDBHeartBeatConfig(vespalib::duration interval) noexcept
    : _interval(interval)
{
}

bool
DocumentDBHeartBeatConfig::operator==(const DocumentDBHeartBeatConfig &rhs) const noexcept
{
    return _interval == rhs._interval;
}

DocumentDBLidSpaceCompactionConfig::DocumentDBLidSpaceCompactionConfig() noexcept
    : _delay(MAX_DELAY_SEC),
      _interval(3600s),
      _allowedLidBloat(1000000000),
      _allowedLidBloatFactor(1.0),
      _remove_batch_block_rate(0.5),
      _remove_block_rate(100),
      _disabled(false),
      _useBucketExecutor(false)
{
}

DocumentDBLidSpaceCompactionConfig::DocumentDBLidSpaceCompactionConfig(vespalib::duration interval,
                                                                       uint32_t allowedLidBloat,
                                                                       double allowedLidBloatFactor,
                                                                       double remove_batch_block_rate,
                                                                       double remove_block_rate,
                                                                       bool disabled,
                                                                       bool useBucketExecutor) noexcept
    : _delay(std::min(MAX_DELAY_SEC, interval)),
      _interval(interval),
      _allowedLidBloat(allowedLidBloat),
      _allowedLidBloatFactor(allowedLidBloatFactor),
      _remove_batch_block_rate(remove_batch_block_rate),
      _remove_block_rate(remove_block_rate),
      _disabled(disabled),
      _useBucketExecutor(useBucketExecutor)
{
}

DocumentDBLidSpaceCompactionConfig
DocumentDBLidSpaceCompactionConfig::createDisabled() noexcept
{
    DocumentDBLidSpaceCompactionConfig result;
    result._disabled = true;
    return result;
}

bool
DocumentDBLidSpaceCompactionConfig::operator==(const DocumentDBLidSpaceCompactionConfig &rhs) const noexcept
{
   return _delay == rhs._delay &&
           _interval == rhs._interval &&
           _allowedLidBloat == rhs._allowedLidBloat &&
           _allowedLidBloatFactor == rhs._allowedLidBloatFactor &&
           _disabled == rhs._disabled;
}


BlockableMaintenanceJobConfig::BlockableMaintenanceJobConfig() noexcept
    : _resourceLimitFactor(1.0),
      _maxOutstandingMoveOps(10)
{}

BlockableMaintenanceJobConfig::BlockableMaintenanceJobConfig(double resourceLimitFactor,
                                                             uint32_t maxOutstandingMoveOps) noexcept
    : _resourceLimitFactor(resourceLimitFactor),
      _maxOutstandingMoveOps(maxOutstandingMoveOps)
{}

bool
BlockableMaintenanceJobConfig::operator==(const BlockableMaintenanceJobConfig &rhs) const noexcept
{
    return _resourceLimitFactor == rhs._resourceLimitFactor &&
           _maxOutstandingMoveOps == rhs._maxOutstandingMoveOps;
}

BucketMoveConfig::BucketMoveConfig() noexcept
    : _maxDocsToMovePerBucket(1),
      _useBucketExecutor(false)
{}
BucketMoveConfig::BucketMoveConfig(uint32_t  maxDocsToMovePerBucket, bool useBucketExecutor_) noexcept
    : _maxDocsToMovePerBucket(maxDocsToMovePerBucket),
      _useBucketExecutor(useBucketExecutor_)
{}

bool
BucketMoveConfig::operator==(const BucketMoveConfig &rhs) const noexcept
{
    return _maxDocsToMovePerBucket == rhs._maxDocsToMovePerBucket &&
            _useBucketExecutor == rhs._useBucketExecutor;
}

DocumentDBMaintenanceConfig::DocumentDBMaintenanceConfig() noexcept
    : _pruneRemovedDocuments(),
      _heartBeat(),
      _sessionCachePruneInterval(900s),
      _visibilityDelay(vespalib::duration::zero()),
      _lidSpaceCompaction(),
      _attributeUsageFilterConfig(),
      _attributeUsageSampleInterval(60s),
      _blockableJobConfig(),
      _flushConfig(),
      _bucketMoveConfig()
{ }

DocumentDBMaintenanceConfig::~DocumentDBMaintenanceConfig() = default;

DocumentDBMaintenanceConfig::
DocumentDBMaintenanceConfig(const DocumentDBPruneRemovedDocumentsConfig &pruneRemovedDocuments,
                            const DocumentDBHeartBeatConfig &heartBeat,
                            vespalib::duration groupingSessionPruneInterval,
                            vespalib::duration visibilityDelay,
                            const DocumentDBLidSpaceCompactionConfig &lidSpaceCompaction,
                            const AttributeUsageFilterConfig &attributeUsageFilterConfig,
                            vespalib::duration attributeUsageSampleInterval,
                            const BlockableMaintenanceJobConfig &blockableJobConfig,
                            const DocumentDBFlushConfig &flushConfig,
                            const BucketMoveConfig & bucketMoveconfig) noexcept
    : _pruneRemovedDocuments(pruneRemovedDocuments),
      _heartBeat(heartBeat),
      _sessionCachePruneInterval(groupingSessionPruneInterval),
      _visibilityDelay(visibilityDelay),
      _lidSpaceCompaction(lidSpaceCompaction),
      _attributeUsageFilterConfig(attributeUsageFilterConfig),
      _attributeUsageSampleInterval(attributeUsageSampleInterval),
      _blockableJobConfig(blockableJobConfig),
      _flushConfig(flushConfig),
      _bucketMoveConfig(bucketMoveconfig)
{ }

bool
DocumentDBMaintenanceConfig::
operator==(const DocumentDBMaintenanceConfig &rhs) const noexcept
{
    return
        _pruneRemovedDocuments == rhs._pruneRemovedDocuments &&
        _heartBeat == rhs._heartBeat &&
        _sessionCachePruneInterval == rhs._sessionCachePruneInterval &&
        _visibilityDelay == rhs._visibilityDelay &&
        _lidSpaceCompaction == rhs._lidSpaceCompaction &&
        _attributeUsageFilterConfig == rhs._attributeUsageFilterConfig &&
        _attributeUsageSampleInterval == rhs._attributeUsageSampleInterval &&
        _blockableJobConfig == rhs._blockableJobConfig &&
        _flushConfig == rhs._flushConfig &&
        _bucketMoveConfig == rhs._bucketMoveConfig;
}

} // namespace proton
