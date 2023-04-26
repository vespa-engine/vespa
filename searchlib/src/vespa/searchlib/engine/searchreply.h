// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchrequest.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/searchlib/common/unique_issues.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/featureset.h>
#include <vector>

namespace search::engine {

class SearchReply
{
public:
    using FeatureValues = vespalib::FeatureValues;
    using UP = std::unique_ptr<SearchReply>;

    class Hit
    {
    public:
        Hit() noexcept : gid(), metric(0) {}
        document::GlobalId gid;
        search::HitRank    metric;
    };

    class Coverage {
    public:
        Coverage() noexcept : Coverage(0) { }
        explicit Coverage(uint64_t active) noexcept : Coverage(active, active) { }
        Coverage(uint64_t active, uint64_t covered) noexcept
            : _covered(covered), _active(active), _targetActive(active),
              _degradeReason(0)
        { }
        uint64_t getCovered() const { return _covered; }
        uint64_t getActive() const { return _active; }
        uint64_t getTargetActive() const { return _targetActive; }

        bool wasDegradedByMatchPhase() const { return ((_degradeReason & MATCH_PHASE) != 0); }
        bool wasDegradedByTimeout() const { return ((_degradeReason & TIMEOUT) != 0); }

        Coverage & setCovered(uint64_t v) { _covered = v; return *this; }
        Coverage & setActive(uint64_t v) { _active = v; return *this; }
        Coverage & setTargetActive(uint64_t v) { _targetActive = v; return *this; }

        Coverage & degradeMatchPhase() { _degradeReason |= MATCH_PHASE; return *this; }
        Coverage & degradeTimeout() { _degradeReason |= TIMEOUT; return *this; }
        enum DegradeReason {MATCH_PHASE=0x01, TIMEOUT=0x02};
    private:
        uint64_t _covered;
        uint64_t _active;
        uint64_t _targetActive;
        uint32_t _degradeReason;
    };

private:
    uint32_t              _distributionKey;
public:
    uint64_t              totalHitCount;
    std::vector<uint32_t> sortIndex;
    std::vector<char>     sortData;
    vespalib::Array<char> groupResult;
    Coverage              coverage;
    std::vector<Hit>      hits;
    FeatureValues         match_features;
    PropertiesMap         propertiesMap;

    SearchRequest::UP     request;
    UniqueIssues::UP      my_issues;

    SearchReply();
    ~SearchReply();
    SearchReply(const SearchReply &rhs); // for test only
    
    void setDistributionKey(uint32_t key) { _distributionKey = key; }
    uint32_t getDistributionKey() const { return _distributionKey; }
};

}
