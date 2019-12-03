// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/context.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageframework/generic/clock/timer.h>

#include <vector>
#include <deque>
#include <memory>

namespace storage {

class MergeStatus : public document::Printable {
public:
    using SP = std::shared_ptr<MergeStatus>;

    std::shared_ptr<api::StorageReply> reply;
    std::vector<api::MergeBucketCommand::Node> nodeList;
    framework::MicroSecTime maxTimestamp;
    std::deque<api::GetBucketDiffCommand::Entry> diff;
    api::StorageMessage::Id pendingId;
    std::shared_ptr<api::GetBucketDiffReply> pendingGetDiff;
    std::shared_ptr<api::ApplyBucketDiffReply> pendingApplyDiff;
    vespalib::duration timeout;
    framework::MilliSecTimer startTime;
    spi::Context context;
 	
    MergeStatus(framework::Clock&, const metrics::LoadType&, api::StorageMessage::Priority, uint32_t traceLevel);
    ~MergeStatus();

    /**
     * @return true if any entries were removed from the internal diff
     *   or the two diffs had entries with mismatching hasmasks, which
     *   indicates that bucket contents have changed during the merge.
     */
    bool removeFromDiff(const std::vector<api::ApplyBucketDiffCommand::Entry>& part, uint16_t hasMask);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool isFirstNode() const { return (reply.get() != 0); }
};

} // storage

