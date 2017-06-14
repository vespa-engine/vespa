// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/fastos/timestamp.h>

namespace proton {

class DocumentDBPruneConfig
{
private:
    double _delay;
    double _interval;
    double _age;

public:
    DocumentDBPruneConfig();
    DocumentDBPruneConfig(double interval, double age);

    bool operator==(const DocumentDBPruneConfig &rhs) const;
    double getDelay() const { return _delay; }
    double getInterval() const { return _interval; }
    double getAge() const { return _age; }
};

typedef DocumentDBPruneConfig DocumentDBPruneRemovedDocumentsConfig;

class DocumentDBHeartBeatConfig
{
private:
    double _interval;

public:
    DocumentDBHeartBeatConfig();
    DocumentDBHeartBeatConfig(double interval);

    bool operator==(const DocumentDBHeartBeatConfig &rhs) const;
    double getInterval() const { return _interval; }
};

class DocumentDBLidSpaceCompactionConfig
{
private:
    double   _delay;
    double   _interval;
    uint32_t _allowedLidBloat;
    double   _allowedLidBloatFactor;
    bool     _disabled;
    uint32_t _maxDocsToScan;

public:
    DocumentDBLidSpaceCompactionConfig();
    DocumentDBLidSpaceCompactionConfig(double interval,
                                       uint32_t allowedLidBloat,
                                       double allowwedLidBloatFactor,
                                       bool disabled = false,
                                       uint32_t maxDocsToScan = 10000);

    static DocumentDBLidSpaceCompactionConfig createDisabled();
    bool operator==(const DocumentDBLidSpaceCompactionConfig &rhs) const;
    double getDelay() const { return _delay; }
    double getInterval() const { return _interval; }
    uint32_t getAllowedLidBloat() const { return _allowedLidBloat; }
    double getAllowedLidBloatFactor() const { return _allowedLidBloatFactor; }
    bool isDisabled() const { return _disabled; }
    uint32_t getMaxDocsToScan() const { return _maxDocsToScan; }
};

class DocumentDBMaintenanceConfig
{
public:
    typedef std::shared_ptr<DocumentDBMaintenanceConfig> SP;

private:
    DocumentDBPruneRemovedDocumentsConfig _pruneRemovedDocuments;
    DocumentDBHeartBeatConfig             _heartBeat;
    double                                _sessionCachePruneInterval;
    fastos::TimeStamp                     _visibilityDelay;
    DocumentDBLidSpaceCompactionConfig    _lidSpaceCompaction;
    AttributeUsageFilterConfig            _attributeUsageFilterConfig;
    double                                _attributeUsageSampleInterval;
    double                                _resourceLimitFactor;

public:
    DocumentDBMaintenanceConfig();

    DocumentDBMaintenanceConfig(const DocumentDBPruneRemovedDocumentsConfig &pruneRemovedDocuments,
                                const DocumentDBHeartBeatConfig &heartBeat,
                                double sessionCachePruneInterval,
                                fastos::TimeStamp visibilityDelay,
                                const DocumentDBLidSpaceCompactionConfig &lidSpaceCompaction,
                                const AttributeUsageFilterConfig &attributeUsageFilterConfig,
                                double attributeUsageSampleInterval,
                                double resourceLimitFactor);

    bool
    operator==(const DocumentDBMaintenanceConfig &rhs) const;

    const DocumentDBPruneRemovedDocumentsConfig &
    getPruneRemovedDocumentsConfig() const
    {
        return _pruneRemovedDocuments;
    }

    const DocumentDBHeartBeatConfig &
    getHeartBeatConfig() const
    {
        return _heartBeat;
    }

    double
    getSessionCachePruneInterval() const
    {
        return _sessionCachePruneInterval;
    }

    fastos::TimeStamp getVisibilityDelay() const { return _visibilityDelay; }

    const DocumentDBLidSpaceCompactionConfig &getLidSpaceCompactionConfig() const {
        return _lidSpaceCompaction;
    }

    const AttributeUsageFilterConfig &getAttributeUsageFilterConfig() const {
        return _attributeUsageFilterConfig;
    }

    double getAttributeUsageSampleInterval() const {
        return _attributeUsageSampleInterval;
    }
    double getResourceLimitFactor() const { return _resourceLimitFactor; }
};

} // namespace proton

