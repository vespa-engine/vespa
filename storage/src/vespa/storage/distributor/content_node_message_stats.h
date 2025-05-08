// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <cstdint>
#include <iosfwd>

namespace storage::distributor {

/**
 * Encapsulation of a set of monotonic counters for observed send/receive events
 * for requests sent to--and responses received from-- a particular content node.
 *
 * Not thread safe.
 */
struct ContentNodeMessageStats {
    uint64_t sent;
    // invariant: sum(recv_*) + cancelled <= _sent
    uint64_t recv_ok;
    uint64_t recv_network_error;
    uint64_t recv_clock_skew_error;
    uint64_t recv_other_error;
    uint64_t cancelled;
    // TODO also count timeouts explicitly?

    constexpr ContentNodeMessageStats() noexcept
        : sent(0), recv_ok(0), recv_network_error(0), recv_clock_skew_error(0),
          recv_other_error(0), cancelled(0)
    {}

    constexpr ContentNodeMessageStats(uint64_t sent_, uint64_t recv_ok_, uint64_t recv_network_error_,
                                      uint64_t recv_clock_skew_error_,  uint64_t recv_other_error_,
                                      uint64_t cancelled_) noexcept
        : sent(sent_), recv_ok(recv_ok_), recv_network_error(recv_network_error_),
          recv_clock_skew_error(recv_clock_skew_error_), recv_other_error(recv_other_error_),
          cancelled(cancelled_)
    {}

    void merge(const ContentNodeMessageStats& other) noexcept;
    // Returns a stats instance with all fields of `this` subtracted by those of `rhs`.
    // Precondition: all fields of `this` are monotonically greater than those of `rhs`.
    [[nodiscard]] ContentNodeMessageStats subtracted(const ContentNodeMessageStats& rhs) const noexcept;
    // Returns true iff all contained fields are zero.
    [[nodiscard]] bool all_zero() const noexcept;
    // Sum of all `*_error` fields. Note: cancellation is not considered an error.
    [[nodiscard]] uint64_t sum_errors() const noexcept {
        return recv_network_error + recv_clock_skew_error + recv_other_error;
    }
    // Sum of all errors + OK received. Does not include cancellation.
    // I.e. sum_errors() > 0 ==> sum_received() > 0
    [[nodiscard]] uint64_t sum_received() const noexcept {
        return sum_errors() + recv_ok;
    }

    bool operator==(const ContentNodeMessageStats&) const noexcept = default;

    void observe_outgoing_request() noexcept {
        ++sent;
    }
    void observe_cancelled() noexcept {
        ++cancelled;
    }

    // Message type is included since certain messages may have transitive errors set,
    // which cannot be directly attributed to a particular node.
    void observe_incoming_response_result(api::MessageType::Id msg_type_id, api::ReturnCode::Result result) noexcept;
};

std::ostream& operator<<(std::ostream& os, const ContentNodeMessageStats& s);

}
