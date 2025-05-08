// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_node_message_stats_tracker.h"
#include <ostream>

namespace storage::distributor {

std::ostream& operator<<(std::ostream& os, const ContentNodeMessageStats& s) {
    os << "Snapshot("
       << "sent="                   << s.sent
       << ", recv_ok="              << s.recv_ok
       << ", recv_rpc_error="       << s.recv_network_error
       << ", recv_time_sync_error=" << s.recv_clock_skew_error
       << ", recv_other_error="     << s.recv_other_error
       << ", cancelled="            << s.cancelled
       << ")";
    return os;
}

void ContentNodeMessageStats::merge(const ContentNodeMessageStats& other) noexcept {
    sent                  += other.sent;
    recv_ok               += other.recv_ok;
    recv_network_error    += other.recv_network_error;
    recv_clock_skew_error += other.recv_clock_skew_error;
    recv_other_error      += other.recv_other_error;
    cancelled             += other.cancelled;
}

ContentNodeMessageStats
ContentNodeMessageStats::subtracted(const ContentNodeMessageStats& rhs) const noexcept {
    ContentNodeMessageStats s;
    s.sent                  = sent - rhs.sent;
    s.recv_ok               = recv_ok - rhs.recv_ok;
    s.recv_network_error    = recv_network_error - rhs.recv_network_error;
    s.recv_clock_skew_error = recv_clock_skew_error - rhs.recv_clock_skew_error;
    s.recv_other_error      = recv_other_error - rhs.recv_other_error;
    s.cancelled             = cancelled - rhs.cancelled;
    return s;
}

bool ContentNodeMessageStats::all_zero() const noexcept {
    return (*this == ContentNodeMessageStats{});
}

namespace {

constexpr bool is_rpc_related_error_code(api::ReturnCode::Result res) noexcept {
    // MessageBus-style "polymorphic" error codes mean that we have to consider values across
    // distinct enum types. Casting away enum-ness is error-prone, but unfortunately necessary here.
    const auto res_ignore_enum = static_cast<uint32_t>(res);
    switch (res_ignore_enum) {
    // See `StorageApiRpcService` for FRT RPC -> mbus/storage API error mapping.
    // Whatever's assigned there for RPC-level errors should also be included here.
    case mbus::ErrorCode::CONNECTION_ERROR:
    case mbus::ErrorCode::NETWORK_ERROR:
    case mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE:
    case api::ReturnCode::TIMEOUT:
    case api::ReturnCode::NOT_CONNECTED:
        return true;
    default:
        return false;
    }
}

constexpr bool is_time_sync_related_error_code(api::ReturnCode::Result res) noexcept {
    return (res == api::ReturnCode::STALE_TIMESTAMP);
}

// Returns true iff the message type is for a response type whose error code may be caused
// by issues that are not directly related to the node the original request was sent to.
// This includes visitors (sends to clients) and merge related messages (sends across nodes).
constexpr bool response_type_may_have_transitive_error(api::MessageType::Id type_id) noexcept {
    switch (type_id) {
    case api::MessageType::VISITOR_CREATE_REPLY_ID:  // Could be from clients
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID: // Could be from other content nodes
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:   // Ditto
    case api::MessageType::MERGEBUCKET_REPLY_ID:     // Ditto
        return true;
    default:
        return false;
    }
}

constexpr bool is_non_failure_error_code(api::ReturnCode::Result res) noexcept {
    switch (res) {
    // TaS is technically an error, but should not be treated as a node-level error
    // since it's an expected operation precondition failure.
    case api::ReturnCode::TEST_AND_SET_CONDITION_FAILED:
    // Aborts can happen due several reasons, such as bucket ownership handoffs
    case api::ReturnCode::ABORTED:
    // Busy shall generally be considered transient due to full queues etc.
    case api::ReturnCode::BUSY:
    // Bucket deleted/not found implies operations raced with concurrent changes
    // to the bucket tree and should be retried transparently.
    case api::ReturnCode::BUCKET_NOT_FOUND:
    case api::ReturnCode::BUCKET_DELETED:
        return true;
    default:
        return false;
    }
}

} // anon ns

void
ContentNodeMessageStats::observe_incoming_response_result(api::MessageType::Id msg_type_id,
                                                          api::ReturnCode::Result result) noexcept
{
    if ((result == api::ReturnCode::Result::OK) || is_non_failure_error_code(result)) [[likely]] {
        ++recv_ok;
        return;
    }
    // We only attribute RPC/time sync errors to a node if the underlying message
    // can't be transitively set by errors on _other_ nodes than the one sent to.
    if (!response_type_may_have_transitive_error(msg_type_id)) {
        if (is_rpc_related_error_code(result)) {
            ++recv_network_error;
        } else if (is_time_sync_related_error_code(result)) {
            ++recv_clock_skew_error;
        } else {
            ++recv_other_error;
        }
    } else {
        ++recv_other_error;
    }
}

}
