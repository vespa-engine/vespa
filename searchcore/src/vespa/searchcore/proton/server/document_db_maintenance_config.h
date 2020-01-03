// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_db_flush_config.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>

namespace proton {

class DocumentDBPruneConfig
{
private:
    vespalib::duration _delay;
    vespalib::duration _interval;
    vespalib::duration _age;

public:
    DocumentDBPruneConfig();
    DocumentDBPruneConfig(vespalib::duration interval, vespalib::duration age);

    bool operator==(const DocumentDBPruneConfig &rhs) const;
    vespalib::duration getDelay() const { return _delay; }
    vespalib::duration getInterval() const { return _interval; }
    vespalib::duration getAge() const { return _age; }
};

typedef DocumentDBPruneConfig DocumentDBPruneRemovedDocumentsConfig;

class DocumentDBHeartBeatConfig
{
private:
    vespalib::duration _interval;

public:
    DocumentDBHeartBeatConfig();
    DocumentDBHeartBeatConfig(vespalib::duration interval);

    bool operator==(const DocumentDBHeartBeatConfig &rhs) const;
    vespalib::duration getInterval() const { return _interval; }
};

class DocumentDBLidSpaceCompactionConfig
{
private:
    vespalib::duration   _delay;
    vespalib::duration   _interval;
    uint32_t             _allowedLidBloat;
    double               _allowedLidBloatFactor;
    vespalib::duration   _remove_batch_block_delay;
    bool                 _disabled;
    uint32_t             _maxDocsToScan;

public:
    DocumentDBLidSpaceCompactionConfig();
    DocumentDBLidSpaceCompactionConfig(vespalib::duration interval,
                                       uint32_t allowedLidBloat,
                                       double allowwedLidBloatFactor,
                                       vespalib::duration remove_batch_block_delay,
                                       bool disabled,
                                       uint32_t maxDocsToScan = 10000);

    static DocumentDBLidSpaceCompactionConfig createDisabled();
    bool operator==(const DocumentDBLidSpaceCompactionConfig &rhs) const;
    vespalib::duration getDelay() const { return _delay; }
    vespalib::duration getInterval() const { return _interval; }
    uint32_t getAllowedLidBloat() const { return _allowedLidBloat; }
    double getAllowedLidBloatFactor() const { return _allowedLidBloatFactor; }
    vespalib::duration get_remove_batch_block_delay() const { return _remove_batch_block_delay; }
    bool isDisabled() const { return _disabled; }
    uint32_t getMaxDocsToScan() const { return _maxDocsToScan; }
};

class BlockableMaintenanceJobConfig {
private:
    double _resourceLimitFactor;
    uint32_t _maxOutstandingMoveOps;

public:
    BlockableMaintenanceJobConfig();
    BlockableMaintenanceJobConfig(double resourceLimitFactor,
                                  uint32_t maxOutstandingMoveOps);
    bool operator==(const BlockableMaintenanceJobConfig &rhs) const;
    double getResourceLimitFactor() const { return _resourceLimitFactor; }
    uint32_t getMaxOutstandingMoveOps() const { return _maxOutstandingMoveOps; }
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

public:
    DocumentDBMaintenanceConfig();
    DocumentDBMaintenanceConfig(const DocumentDBPruneRemovedDocumentsConfig &pruneRemovedDocuments,
                                const DocumentDBHeartBeatConfig &heartBeat,
                                vespalib::duration sessionCachePruneInterval,
                                vespalib::duration visibilityDelay,
                                const DocumentDBLidSpaceCompactionConfig &lidSpaceCompaction,
                                const AttributeUsageFilterConfig &attributeUsageFilterConfig,
                                vespalib::duration attributeUsageSampleInterval,
                                const BlockableMaintenanceJobConfig &blockableJobConfig,
                                const DocumentDBFlushConfig &flushConfig);

    DocumentDBMaintenanceConfig(const DocumentDBMaintenanceConfig &) = delete;
    DocumentDBMaintenanceConfig & operator = (const DocumentDBMaintenanceConfig &) = delete;
    ~DocumentDBMaintenanceConfig();


    bool
    operator==(const DocumentDBMaintenanceConfig &rhs) const;

    const DocumentDBPruneRemovedDocumentsConfig &getPruneRemovedDocumentsConfig() const {
        return _pruneRemovedDocuments;
    }
    const DocumentDBHeartBeatConfig &getHeartBeatConfig() const {
        return _heartBeat;
    }
    vespalib::duration getSessionCachePruneInterval() const {
        return _sessionCachePruneInterval;
    }
    vespalib::duration getVisibilityDelay() const { return _visibilityDelay; }
    bool hasVisibilityDelay() const { return _visibilityDelay > vespalib::duration::zero(); }
    const DocumentDBLidSpaceCompactionConfig &getLidSpaceCompactionConfig() const {
        return _lidSpaceCompaction;
    }
    const AttributeUsageFilterConfig &getAttributeUsageFilterConfig() const {
        return _attributeUsageFilterConfig;
    }
    vespalib::duration getAttributeUsageSampleInterval() const {
        return _attributeUsageSampleInterval;
    }
    const BlockableMaintenanceJobConfig &getBlockableJobConfig() const {
        return _blockableJobConfig;
    }
    const DocumentDBFlushConfig &getFlushConfig() const { return _flushConfig; }
};

} // namespace proton

