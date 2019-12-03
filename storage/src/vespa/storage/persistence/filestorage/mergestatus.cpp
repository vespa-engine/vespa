// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergestatus.h"
#include <ostream>
#include <vespa/log/log.h>

LOG_SETUP(".mergestatus");

namespace storage {

MergeStatus::MergeStatus(framework::Clock& clock, const metrics::LoadType& lt,
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
        uint16_t hasMask)
{
    std::deque<api::GetBucketDiffCommand::Entry>::iterator it(diff.begin());
    std::vector<api::ApplyBucketDiffCommand::Entry>::const_iterator it2(
            part.begin());
    bool altered = false;
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
            } else if (it2->_entry._hasMask != it->_hasMask) {
                // Hasmasks have changed, meaning bucket contents changed on
                // one or more of the nodes during merging.
                altered = true;
                it->_hasMask = it2->_entry._hasMask;
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
