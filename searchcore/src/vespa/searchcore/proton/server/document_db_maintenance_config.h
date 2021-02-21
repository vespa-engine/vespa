// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_flush_config.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

namespace proton {

class DocumentDBPruneConfig
{
private:
    vespalib::duration _delay;
    vespalib::duration _interval;
    vespalib::duration _age;

public:
    DocumentDBPruneConfig() noexcept;
    DocumentDBPruneConfig(vespalib::duration interval, vespalib::duration age) noexcept;

    bool operator==(const DocumentDBPruneConfig &rhs) const noexcept;
    vespalib::duration getDelay() const noexcept { return _delay; }
    vespalib::duration getInterval() const noexcept { return _interval; }
    vespalib::duration getAge() const noexcept { return _age; }
};

typedef DocumentDBPruneConfig DocumentDBPruneRemovedDocumentsConfig;

class DocumentDBHeartBeatConfig
{
private:
    vespalib::duration _interval;

public:
    DocumentDBHeartBeatConfig() noexcept;
    DocumentDBHeartBeatConfig(vespalib::duration interval) noexcept;

    bool operator==(const DocumentDBHeartBeatConfig &rhs) const noexcept;
    vespalib::duration getInterval() const noexcept { return _interval; }
};

class DocumentDBLidSpaceCompactionConfig
{
private:
    vespalib::duration   _delay;
    vespalib::duration   _interval;
    uint32_t             _allowedLidBloat;
    double               _allowedLidBloatFactor;
    double               _remove_batch_block_rate;
    double               _remove_block_rate;
    bool                 _disabled;
    bool                 _useBucketExecutor;

public:
    DocumentDBLidSpaceCompactionConfig() noexcept;
    DocumentDBLidSpaceCompactionConfig(vespalib::duration interval,
                                       uint32_t allowedLidBloat,
                                       double allowwedLidBloatFactor,
                                       double remove_batch_block_rate,
                                       double remove_block_rate,
                                       bool disabled,
                                       bool useBucketExecutor) noexcept;

    static DocumentDBLidSpaceCompactionConfig createDisabled() noexcept;
    bool operator==(const DocumentDBLidSpaceCompactionConfig &rhs) const noexcept;
    vespalib::duration getDelay() const noexcept { return _delay; }
    vespalib::duration getInterval() const noexcept { return _interval; }
    uint32_t getAllowedLidBloat() const noexcept { return _allowedLidBloat; }
    double getAllowedLidBloatFactor() const noexcept { return _allowedLidBloatFactor; }
    double get_remove_batch_block_rate() const noexcept { return _remove_batch_block_rate; }
    double get_remove_block_rate() const noexcept { return _remove_block_rate; }
    bool isDisabled() const noexcept { return _disabled; }
    bool useBucketExecutor() const noexcept { return _useBucketExecutor; }
};

class BlockableMaintenanceJobConfig {
private:
    double   _resourceLimitFactor;
    uint32_t _maxOutstandingMoveOps;

public:
    BlockableMaintenanceJobConfig() noexcept;
    BlockableMaintenanceJobConfig(double resourceLimitFactor,
                                  uint32_t maxOutstandingMoveOps) noexcept;
    bool operator==(const BlockableMaintenanceJobConfig &rhs) const noexcept;
    double getResourceLimitFactor() const noexcept { return _resourceLimitFactor; }
    uint32_t getMaxOutstandingMoveOps() const noexcept { return _maxOutstandingMoveOps; }
};

class BucketMoveConfig {
public:
    BucketMoveConfig() noexcept;
    BucketMoveConfig(uint32_t  _maxDocsToMovePerBucket, bool useBucketExecutor) noexcept;
    bool operator==(const BucketMoveConfig &rhs) const noexcept;
    uint32_t getMaxDocsToMovePerBucket() const noexcept { return _maxDocsToMovePerBucket; }
    bool useBucketExecutor() const noexcept { return _useBucketExecutor; }
private:
    uint32_t  _maxDocsToMovePerBucket;
    bool      _useBucketExecutor;
};

class DocumentDBMaintenanceConfig
{
public:
    typedef std::shared_ptr<DocumentDBMaintenanceConfig> SP;

private:
    DocumentDBPruneRemovedDocumentsConfig _pruneRemovedDocuments;
    DocumentDBHeartBeatConfig             _heartBeat;
    vespalib::duration                    _sessionCachePruneInterval;
    vespalib::duration                    _visibilityDelay;
    DocumentDBLidSpaceCompactionConfig    _lidSpaceCompaction;
    AttributeUsageFilterConfig            _attributeUsageFilterConfig;
    vespalib::duration                    _attributeUsageSampleInterval;
    BlockableMaintenanceJobConfig         _blockableJobConfig;
    DocumentDBFlushConfig                 _flushConfig;
    BucketMoveConfig                      _bucketMoveConfig;

public:
    DocumentDBMaintenanceConfig() noexcept;
    DocumentDBMaintenanceConfig(const DocumentDBPruneRemovedDocumentsConfig &pruneRemovedDocuments,
                                const DocumentDBHeartBeatConfig &heartBeat,
                                vespalib::duration sessionCachePruneInterval,
                                vespalib::duration visibilityDelay,
                                const DocumentDBLidSpaceCompactionConfig &lidSpaceCompaction,
                                const AttributeUsageFilterConfig &attributeUsageFilterConfig,
                                vespalib::duration attributeUsageSampleInterval,
                                const BlockableMaintenanceJobConfig &blockableJobConfig,
                                const DocumentDBFlushConfig &flushConfig,
                                const BucketMoveConfig & bucketMoveconfig) noexcept;

    DocumentDBMaintenanceConfig(const DocumentDBMaintenanceConfig &) = delete;
    DocumentDBMaintenanceConfig & operator = (const DocumentDBMaintenanceConfig &) = delete;
    ~DocumentDBMaintenanceConfig();


    bool
    operator==(const DocumentDBMaintenanceConfig &rhs) const noexcept ;

    const DocumentDBPruneRemovedDocumentsConfig &getPruneRemovedDocumentsConfig() const noexcept {
        return _pruneRemovedDocuments;
    }
    const DocumentDBHeartBeatConfig &getHeartBeatConfig() const noexcept {
        return _heartBeat;
    }
    vespalib::duration getSessionCachePruneInterval() const noexcept {
        return _sessionCachePruneInterval;
    }
    vespalib::duration getVisibilityDelay() const noexcept { return _visibilityDelay; }
    const DocumentDBLidSpaceCompactionConfig &getLidSpaceCompactionConfig() const noexcept {
        return _lidSpaceCompaction;
    }
    const AttributeUsageFilterConfig &getAttributeUsageFilterConfig() const noexcept {
        return _attributeUsageFilterConfig;
    }
    vespalib::duration getAttributeUsageSampleInterval() const noexcept {
        return _attributeUsageSampleInterval;
    }
    const BlockableMaintenanceJobConfig &getBlockableJobConfig() const noexcept {
        return _blockableJobConfig;
    }
    const DocumentDBFlushConfig &getFlushConfig() const noexcept { return _flushConfig; }
    const BucketMoveConfig & getBucketMoveConfig() const noexcept  { return _bucketMoveConfig; }
};

} // namespace proton
