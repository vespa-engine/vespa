// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/operations/idealstate/mergelimiter.h>

#include <vespa/log/log.h>

LOG_SETUP(".distributor.operations.merge.limiter");

namespace storage {
namespace distributor {

MergeLimiter::MergeLimiter(uint16_t maxNodes)
    : _maxNodes(maxNodes)
{
    LOG(spam, "Limiter initialized with %u nodes.", uint32_t(maxNodes));
}

namespace {
    class EqualCopies {
        uint32_t _checksum;
        std::vector<MergeMetaData> _copies;
        uint32_t _trustedCopies;

    public:
        EqualCopies()
            : _checksum(0),
              _trustedCopies(0)
        {
        }

        bool hasTrusted() const { return (_trustedCopies > 0); }
        uint32_t trustedCount() const { return _trustedCopies; }
        uint32_t size() const { return _copies.size(); }
        bool operator==(const MergeMetaData& mmd) const {
            return (_checksum == mmd.checksum());
        }
        void add(const MergeMetaData& mmd) {
            if (_copies.empty()) _checksum = mmd.checksum();
            if (mmd.trusted()) ++_trustedCopies;
            _copies.push_back(mmd);
        }
        MergeMetaData extractNext() {
            MergeMetaData data = _copies.back();
            _copies.pop_back();
            return data;
        }
    };

    class Statistics {
        std::vector<EqualCopies> _groups;
        uint32_t _trustedCopies;

    public:
        Statistics() : _trustedCopies(0) {}
        Statistics(const MergeLimiter::NodeArray& a)
            : _trustedCopies(0)
        {
            _groups.reserve(a.size());
            for (uint32_t i=0, n=a.size(); i<n; ++i) {
                add(a[i]);
                if (a[i].trusted()) {
                    ++_trustedCopies;
                }
            }
        }

        EqualCopies& getMajority() {
            EqualCopies* candidate = 0;
            uint32_t size = 0;
            for (uint32_t i=0, n=_groups.size(); i<n; ++i) {
                if (_groups[i].size() > size) {
                    candidate = &_groups[i];
                    size = candidate->size();
                }
            }
            assert(candidate != 0);
            return *candidate;
        }

        bool hasTrusted() const { return (_trustedCopies > 0); }
        uint32_t trustedCount() const { return _trustedCopies; }

        Statistics extractGroupsWithTrustedCopies() {
            std::vector<EqualCopies> remaining;
            Statistics trusted;
            remaining.reserve(_groups.size());
            trusted._groups.reserve(_groups.size());
            for (uint32_t i=0, n=_groups.size(); i<n; ++i) {
                if (_groups[i].hasTrusted()) {
                    trusted._groups.push_back(_groups[i]);
                    trusted._trustedCopies += _groups[i].trustedCount();
                } else {
                    remaining.push_back(_groups[i]);
                    _trustedCopies -= _groups[i].trustedCount();
                }
            }
            swap(remaining, _groups);
            return trusted;
        }
        bool extractNext(MergeMetaData& data, uint32_t& last) {
            if (_groups.empty()) return false;
            if (++last >= _groups.size()) { last = 0; }
            data = _groups[last].extractNext();
            if (_groups[last].size() == 0) {
                removeGroup(last);
                --last;
            }
            return true;
        }
        void removeGroup(uint32_t groupIndex) {
            std::vector<EqualCopies> remaining;
            remaining.reserve(_groups.size()-1);
            for (uint32_t i=0, n=_groups.size(); i<n; ++i) {
                if (i != groupIndex) {
                    remaining.push_back(_groups[i]);
                }
            }
            remaining.swap(_groups);
        }

    private:
        void add(const MergeMetaData& mmd) {
            for (uint32_t i=0; i<_groups.size(); ++i) {
                if (_groups[i] == mmd) {
                    _groups[i].add(mmd);
                    return;
                }
            }
            _groups.push_back(EqualCopies());
            _groups.back().add(mmd);
        }
    };

        // Add up to max nodes, where different variants exist, prefer having
        // some of each.
    void addNodes(uint32_t max, Statistics& stats,
                  MergeLimiter::NodeArray& result)
    {
        uint32_t last = -1;
        for (uint32_t i=0; i<max; ++i) {
            MergeMetaData data;
            if (!stats.extractNext(data, last)) return;
            result.push_back(data);
        }
    }

    struct SourceOnlyOrder {
        bool operator()(const MergeMetaData& m1, const MergeMetaData& m2) {
            if (m1._sourceOnly == m2._sourceOnly) return false;
            return m2._sourceOnly;
        }
    };
}

void
MergeLimiter::limitMergeToMaxNodes(NodeArray& nodes)
{
        // If not above max anyhow, we need not do anything
    if (nodes.size() <= _maxNodes) return;
        // Gather some statistics to base decision on what we are going to do on
    Statistics stats(nodes);
    NodeArray result;
        // If we have trusted copies, these should be complete. Pick one of them
        // and merge with as many untrusted copies as possible
    if (stats.hasTrusted()) {
        Statistics trusted(stats.extractGroupsWithTrustedCopies());
        addNodes(_maxNodes - 1, stats, result);
        addNodes(_maxNodes - result.size(), trusted, result);
    } else {
        addNodes(_maxNodes, stats, result);
    }
    std::stable_sort(result.begin(), result.end(), SourceOnlyOrder());
    result.swap(nodes);
}

} // distributor
} // storage
