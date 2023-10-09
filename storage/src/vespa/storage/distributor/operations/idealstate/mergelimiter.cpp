// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergelimiter.h"
#include <cassert>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.operations.merge.limiter");

namespace storage::distributor {

MergeLimiter::MergeLimiter(uint16_t maxNodes)
    : _maxNodes(maxNodes)
{
    assert(maxNodes > 1);
    LOG(spam, "Limiter initialized with %u nodes.", uint32_t(maxNodes));
}

// TODO replace this overly complicated set of heuristics with something simpler.
// Suggestion:
// 1. Find non-source only replica with highest meta entry count. Emit it and remove from set.
//    This tries to maintain a "seed" replica that can hopefully let the remaining replicas
//    converge to the complete document entry set as quickly as possible.
// 2. Create mapping from checksum -> replica set.
// 3. Circularly loop through mapping and emit+remove the first replica in each mapping's set.
//    Distributing the merge across replica checksum groups is a heuristic to fetch as many
//    distinct document entries in one merge operation as possible, as these are all known to
//    be pairwise divergent from each other.
// 3.1 Once merge limit is reached, break
// 4. Do a stable sort on the emitted list such that source only replicas are last in the sequence.
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

        bool hasTrusted() const noexcept { return (_trustedCopies > 0); }
        uint32_t trustedCount() const noexcept { return _trustedCopies; }
        uint32_t size() const noexcept { return static_cast<uint32_t>(_copies.size()); }
        bool operator==(const MergeMetaData& mmd) const noexcept {
            return (_checksum == mmd.checksum());
        }
        void add(const MergeMetaData& mmd) {
            if (_copies.empty()) {
                _checksum = mmd.checksum();
            }
            // Don't treat source only replicas as trusted from the perspective of
            // picking replica groups. "Trusted" in the context of the merge limiter
            // logic _in practice_ means "may be output as the sole non-source only node
            // in the resulting node set", which obviously doesn't work if it is in fact
            // source only to begin with...
            if (mmd.trusted() && !mmd.source_only()) {
                ++_trustedCopies;
            }
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
            for (uint32_t i = 0, n = static_cast<uint32_t>(a.size()); i < n; ++i) {
                add(a[i]);
                if (a[i].trusted() && !a[i].source_only()) {
                    ++_trustedCopies;
                }
            }
        }

        bool hasTrusted() const noexcept { return (_trustedCopies > 0); }

        Statistics extractGroupsWithTrustedCopies() {
            std::vector<EqualCopies> remaining;
            Statistics trusted;
            remaining.reserve(_groups.size());
            trusted._groups.reserve(_groups.size());
            for (uint32_t i = 0, n = static_cast<uint32_t>(_groups.size()); i < n; ++i) {
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
            for (uint32_t i = 0, n = static_cast<uint32_t>(_groups.size()); i < n; ++i) {
                if (i != groupIndex) {
                    remaining.push_back(_groups[i]);
                }
            }
            remaining.swap(_groups);
        }

    private:
        void add(const MergeMetaData& mmd) {
            // Treat source only replicas as their own distinct "groups" with regards
            // to picking replicas for being part of the merge. This way, we avoid
            // accidentally picking a trusted source only replica as our one trusted
            // replica that will be part of the merge.
            if (!mmd.source_only()) {
                for (uint32_t i = 0; i < _groups.size(); ++i) {
                    if (_groups[i] == mmd) {
                        _groups[i].add(mmd);
                        return;
                    }
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
        // FIXME redesign! `last` will unsigned over/underflow in extractNext, which
        // is not a very pretty solution, to say the least.
        uint32_t last = -1;
        for (uint32_t i = 0; i < max; ++i) {
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

// FIXME the only reason why this code doesn't end up accidentally picking
// just source-only replicas as the output node set today is due to an implicit
// guarantee that the input to this function always has source-only replicas
// listed _last_ in the sequence.
void
MergeLimiter::limitMergeToMaxNodes(NodeArray& nodes)
{
    // If not above max anyhow, we need not do anything
    if (nodes.size() <= _maxNodes) {
        return;
    }
    // Gather some statistics to base decision on what we are going to do on
    Statistics stats(nodes);
    NodeArray result;
    // If we have trusted copies, these are likely to be complete. Pick one of them
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

} // storage::distributor
