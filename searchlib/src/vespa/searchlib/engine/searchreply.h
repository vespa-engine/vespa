// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/vespalib/util/array.h>
#include <memory>
#include <vespa/searchlib/engine/searchrequest.h>

namespace search {
namespace engine {

class SearchReply
{
public:
    using UP = std::unique_ptr<SearchReply>;

    class Hit
    {
    public:
        Hit() : gid(), metric(0), path(0), _distributionKey(0) {}
        void setDistributionKey(uint32_t key) { _distributionKey = key; }
        uint32_t getDistributionKey() const { return _distributionKey; }
        document::GlobalId gid;
        search::HitRank	metric;
        uint32_t	path;      // wide
    private:
        int32_t	        _distributionKey;  // wide
    };

    class Coverage {
    public:
        Coverage() : _covered(0), _active(0) {}
        Coverage(uint64_t active) : _covered(active), _active(active) {}
        Coverage(uint64_t active, uint64_t covered) : _covered(covered), _active(active) {}
        uint64_t getCovered() const { return _covered; }
        uint64_t getActive() const { return _active; }
        Coverage & setCovered(uint64_t v) { _covered = v; return *this; }
        Coverage & setActive(uint64_t v) { _active = v; return *this; }
    private:
        uint64_t _covered;
        uint64_t _active;
    };

    // set to false to indicate 'talk to the hand' behavior
    bool                  valid;

    // normal results
    uint32_t              offset;
private:
    uint32_t _distributionKey;
public:
    uint64_t              totalHitCount;
    search::HitRank       maxRank;
    std::vector<uint32_t> sortIndex;
    std::vector<char>     sortData;
    vespalib::Array<char> groupResult;
    Coverage              coverage;
    bool                  useWideHits;
    std::vector<Hit>      hits;
    PropertiesMap         propertiesMap;

    // in case of error
    uint32_t              errorCode;
    vespalib::string      errorMessage;

    SearchRequest::UP     request;

    SearchReply();
    SearchReply(const SearchReply &rhs); // for test only
    
    void setDistributionKey(uint32_t key) { _distributionKey = key; }
    uint32_t getDistributionKey() const { return _distributionKey; }
};

} // namespace engine
} // namespace search

