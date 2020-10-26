// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergestatus.h"
#include <ostream>
#include <vespa/log/log.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <cassert>

LOG_SETUP(".mergestatus");

namespace storage {

namespace {

/*
 * Class for remapping bit masks from a partial set of nodes to a full
 * set of nodes.
 */
class MaskRemapper
{
    std::vector<uint16_t> _mask_remap;

public:
    MaskRemapper(const std::vector<api::MergeBucketCommand::Node> &all_nodes,
                 const std::vector<api::MergeBucketCommand::Node> &nodes);
    ~MaskRemapper();

    uint16_t operator()(uint16_t mask) const;
};

MaskRemapper::MaskRemapper(const std::vector<api::MergeBucketCommand::Node> &all_nodes,
                           const std::vector<api::MergeBucketCommand::Node> &nodes)
    : _mask_remap()
{
    if (nodes != all_nodes) {
        vespalib::hash_map<uint32_t, uint32_t> node_index_to_mask(all_nodes.size());
        uint16_t mask = 1u;
        for (const auto& node : all_nodes) {
            node_index_to_mask[node.index] = mask;
            mask <<= 1;
        }
        _mask_remap.reserve(nodes.size());
        for (const auto& node : nodes) {
            mask = node_index_to_mask[node.index];
            assert(mask != 0u);
            _mask_remap.push_back(mask);
        }
    }
}

MaskRemapper::~MaskRemapper() = default;

uint16_t
MaskRemapper::operator()(uint16_t mask) const
{
    if (!_mask_remap.empty()) {
        uint16_t new_mask = 0u;
        for (uint32_t i = 0u; i < _mask_remap.size(); ++i) {
            if ((mask & (1u << i)) != 0u) {
                new_mask |= _mask_remap[i];
            }
        }
        mask = new_mask;
    }
    return mask;
}

}

MergeStatus::MergeStatus(const framework::Clock& clock, const metrics::LoadType& lt,
                         api::StorageMessage::Priority priority,
                         uint32_t traceLevel)
    : reply(), nodeList(), maxTimestamp(0), diff(), pendingId(0),
      pendingGetDiff(), pendingApplyDiff(), timeout(0), startTime(clock),
      context(lt, priority, traceLevel)
{}

MergeStatus::~MergeStatus() = default;

bool
MergeStatus::removeFromDiff(
        const std::vector<api::ApplyBucketDiffCommand::Entry>& part,
        uint16_t hasMask,
        const std::vector<api::MergeBucketCommand::Node> &nodes)
{
    std::deque<api::GetBucketDiffCommand::Entry>::iterator it(diff.begin());
    std::vector<api::ApplyBucketDiffCommand::Entry>::const_iterator it2(
            part.begin());
    bool altered = false;
    MaskRemapper remap_mask(nodeList, nodes);
    // We expect part array to be sorted in the same order as in the diff,
    // and that all entries in the part should exist in the source list.
    while (it != diff.end() && it2 != part.end()) {
        if (it->_timestamp != it2->_entry._timestamp) {
            ++it;
        } else {
            break;
        }
    }

    // Iterate and match entries in diff.
    while (it != diff.end() && it2 != part.end()) {
        if (it->_timestamp != it2->_entry._timestamp) {
            ++it;
        } else {
            // It is legal for an apply bucket diff to not fill all entries, so
            // only remove it if it was actually transferred to all copies this
            // time around, or if no copies have that doc anymore. (Can happen
            // due to reverting or corruption)
            if (it2->_entry._hasMask == hasMask
                || it2->_entry._hasMask == 0)
            {
                if (it2->_entry._hasMask == 0) {
                    LOG(debug, "Merge entry %s no longer exists on any nodes",
                        it2->toString().c_str());
                }
                // Timestamp equal. Should really be the same entry. If not
                // though, there is nothing we can do but accept it.
                if (!(*it == it2->_entry)) {
                    LOG(warning, "Merge retrieved entry %s for entry %s but "
                                 "these do not match.",
                        it2->toString().c_str(), it->toString().c_str());
                }
                it = diff.erase(it);
                altered = true;
            } else {
                uint16_t mask = remap_mask(it2->_entry._hasMask);
                if (mask != it->_hasMask) {
                    // Hasmasks have changed, meaning bucket contents changed on
                    // one or more of the nodes during merging.
                    altered = true;
                    it->_hasMask = mask;
                }
            }
            ++it2;
        }
    }
    if (it2 != part.end()) {
        uint32_t counter = 0;
        while (it2 != part.end()) {
            ++it2;
            ++counter;
        }
        LOG(warning, "Apply bucket diff contained %u entries not existing in "
                     "the request.", counter);
    }

    return altered;
}

void
MergeStatus::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    if (reply.get()) {
        (void) verbose;
        out << "MergeStatus(" << "nodes";
        for (uint32_t i=0; i<nodeList.size(); ++i) {
            out << " " << nodeList[i];
        }
        out << ", maxtime " << maxTimestamp << ":";
        for (std::deque<api::GetBucketDiffCommand::Entry>::const_iterator it
                = diff.begin(); it != diff.end(); ++it)
        {
            out << "\n" << indent << it->toString(true);
        }
        out << ")";
    } else if (pendingGetDiff.get() != 0) {
        out << "MergeStatus(Middle node awaiting GetBucketDiffReply)\n";
    } else if (pendingApplyDiff.get() != 0) {
        out << "MergeStatus(Middle node awaiting ApplyBucketDiffReply)\n";
    }
}

};
