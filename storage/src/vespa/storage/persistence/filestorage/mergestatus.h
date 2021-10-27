// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/context.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageframework/generic/clock/timer.h>

#include <vector>
#include <deque>
#include <future>
#include <memory>
#include <optional>

namespace storage {

class MergeStatus : public document::Printable {
public:
    std::shared_ptr<api::StorageReply> reply;
    std::vector<api::MergeBucketCommand::Node> full_node_list;
    std::vector<api::MergeBucketCommand::Node> nodeList;
    framework::MicroSecTime maxTimestamp;
    std::deque<api::GetBucketDiffCommand::Entry> diff;
    api::StorageMessage::Id pendingId;
    std::shared_ptr<api::GetBucketDiffReply> pendingGetDiff;
    std::shared_ptr<api::ApplyBucketDiffReply> pendingApplyDiff;
    vespalib::duration timeout;
    framework::MilliSecTimer startTime;
    std::optional<std::future<vespalib::string>> delayed_error;
    spi::Context context;
 	
    MergeStatus(const framework::Clock&, api::StorageMessage::Priority, uint32_t traceLevel);
    ~MergeStatus() override;

    /**
     * Note: hasMask parameter and _entry._hasMask in part vector are per-reply masks,
     *       based on the nodes returned in ApplyBucketDiffReply.
     * @return true if any entries were removed from the internal diff
     *   or the two diffs had entries with mismatching hasmasks, which
     *   indicates that bucket contents have changed during the merge.
     */
    bool removeFromDiff(const std::vector<api::ApplyBucketDiffCommand::Entry>& part, uint16_t hasMask, const std::vector<api::MergeBucketCommand::Node> &nodes);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool isFirstNode() const { return static_cast<bool>(reply); }
    void set_delayed_error(std::future<vespalib::string>&& delayed_error_in);
    void check_delayed_error(api::ReturnCode &return_code);
};

} // storage

