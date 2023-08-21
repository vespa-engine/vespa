// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "activecopy.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <cassert>
#include <ostream>

namespace std {

template<typename T>
std::ostream& operator<<(std::ostream& out, const std::vector<T>& v) {
    out << "[";
    for (uint32_t i=0; i<v.size(); ++i) {
        out << "\n  " << v[i];
    }
    if (!v.empty()) {
        out << "\n";
    }
    return out << "]";
}

}

namespace storage::distributor {

using IndexList = lib::Distribution::IndexList;

vespalib::string
ActiveCopy::getReason() const {
    if (_ready && (_doc_count > 0) && valid_ideal()) {
        vespalib::asciistream ost;
        ost << "copy is ready, has " << _doc_count
            << " docs and ideal state priority " << _ideal;
        return ost.str();
    } else if (_ready && (_doc_count > 0)) {
        vespalib::asciistream ost;
        ost << "copy is ready with " << _doc_count << " docs";
        return ost.str();
    } else if (_ready) {
        return "copy is ready";
    } else if ((_doc_count > 0) && valid_ideal()) {
        vespalib::asciistream ost;
        ost << "copy has " << _doc_count << " docs and ideal state priority " << _ideal;
        return ost.str();
    } else if (_doc_count > 0) {
        vespalib::asciistream ost;
        ost << "copy has " << _doc_count << " docs";
        return ost.str();
    } else if (_active) {
        return "copy is already active";
    } else if (valid_ideal()) {
        vespalib::asciistream ost;
        ost << "copy is ideal state priority " << _ideal;
        return ost.str();
    } else {
        return "first available copy";
    }
}

std::ostream&
operator<<(std::ostream& out, const ActiveCopy & e) {
    out << "Entry(Node " << e._nodeIndex;
    if (e._ready) {
        out << ", ready";
    }
    if (e._doc_count > 0) {
        out << ", doc_count " << e._doc_count;
    }
    if (e.valid_ideal()) {
        out << ", ideal pri " << e._ideal;
    }
    out << ")";
    return out;
}

namespace {

IndexList
buildValidNodeIndexList(const BucketDatabase::Entry& e) {
    IndexList result;
    result.reserve(e->getNodeCount());
    for (uint32_t i=0, n=e->getNodeCount(); i < n; ++i) {
        const BucketCopy& cp = e->getNodeRef(i);
        if (cp.valid()) {
            result.push_back(cp.getNode());
        }
    }
    return result;
}

using SmallActiveCopyList = vespalib::SmallVector<ActiveCopy, 2>;
static_assert(sizeof(SmallActiveCopyList) == 40);

SmallActiveCopyList
buildNodeList(const BucketDatabase::Entry& e,vespalib::ConstArrayRef<uint16_t> nodeIndexes, const IdealServiceLayerNodesBundle::Node2Index & idealState)
{
    SmallActiveCopyList result;
    result.reserve(nodeIndexes.size());
    for (uint16_t nodeIndex : nodeIndexes) {
        const BucketCopy *copy = e->getNode(nodeIndex);
        assert(copy);
        result.emplace_back(nodeIndex, *copy, idealState.lookup(nodeIndex));
    }
    return result;
}

}

struct ActiveStateOrder {
    bool operator()(const ActiveCopy & e1, const ActiveCopy & e2) noexcept {
        if (e1._ready != e2._ready) {
            return e1._ready;
        }
        if (e1._doc_count != e2._doc_count) {
            return e1._doc_count > e2._doc_count;
        }
        if (e1._ideal != e2._ideal) {
            return e1._ideal < e2._ideal;
        }
        if (e1._active != e2._active) {
            return e1._active;
        }
        return e1.nodeIndex() < e2.nodeIndex();
    }
};

ActiveList
ActiveCopy::calculate(const Node2Index & idealState, const lib::Distribution& distribution,
                      const BucketDatabase::Entry& e, uint32_t max_activation_inhibited_out_of_sync_groups)
{
    IndexList validNodesWithCopy = buildValidNodeIndexList(e);
    if (validNodesWithCopy.empty()) {
        return ActiveList();
    }
    std::vector<IndexList> groups;
    if (distribution.activePerGroup()) {
        groups = distribution.splitNodesIntoLeafGroups(validNodesWithCopy);
    } else {
        groups.push_back(std::move(validNodesWithCopy));
    }
    std::vector<ActiveCopy> result;
    result.reserve(groups.size());

    auto maybe_majority_info = ((max_activation_inhibited_out_of_sync_groups > 0)
                                ? e->majority_consistent_bucket_info()
                                : api::BucketInfo()); // Invalid by default
    uint32_t inhibited_groups = 0;
    for (const auto& group_nodes : groups) {
        SmallActiveCopyList entries = buildNodeList(e, group_nodes, idealState);
        auto best = std::min_element(entries.begin(), entries.end(), ActiveStateOrder());
        if ((groups.size() > 1) &&
            (inhibited_groups < max_activation_inhibited_out_of_sync_groups) &&
            maybe_majority_info.valid())
        {
            const auto* candidate = e->getNode(best->_nodeIndex);
            if (!candidate->getBucketInfo().equalDocumentInfo(maybe_majority_info) && !candidate->active()) {
                ++inhibited_groups;
                continue; // Do _not_ add candidate as activation target since it's out of sync with the majority
            }
        }
        result.emplace_back(*best);
    }
    return ActiveList(std::move(result));
}

void
ActiveList::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "[";
    if (verbose) {
        for (size_t i=0; i<_v.size(); ++i) {
            out << "\n" << indent << "  " << _v[i].nodeIndex() << " " << _v[i].getReason();
        }
        if (!_v.empty()) {
            out << "\n" << indent;
        }
    } else {
        if (!_v.empty()) {
            out << _v[0].nodeIndex();
        }
        for (size_t i=1; i<_v.size(); ++i) {
            out << " " << _v[i].nodeIndex();
        }
    }
    out << "]";
}

bool
ActiveList::contains(uint16_t node) const noexcept
{
    for (const auto& candidate : _v) {
        if (node == candidate.nodeIndex()) {
            return true;
        }
    }
    return false;
}

}
