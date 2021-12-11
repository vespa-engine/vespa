// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <optional>

namespace proton {

/*
 * Stats for feed handler.
 */
class FeedHandlerStats
{
    uint64_t                _commits;
    uint64_t                _operations;
    double                  _total_latency;
    std::optional<uint32_t> _min_operations;
    std::optional<uint32_t> _max_operations;
    std::optional<double>   _min_latency;
    std::optional<double>   _max_latency;

public:
    FeedHandlerStats(uint64_t commits, uint64_t operations, double total_latency) noexcept;
    FeedHandlerStats() noexcept;
    ~FeedHandlerStats();
    FeedHandlerStats& operator-=(const FeedHandlerStats& rhs) noexcept;
    void add_commit(uint32_t operations, double latency) noexcept;
    void reset_min_max() noexcept;
    uint64_t get_commits() noexcept { return _commits; }
    uint64_t get_operations() noexcept { return _operations; }
    double get_total_latency() noexcept { return _total_latency; }
    const std::optional<uint32_t>& get_min_operations() noexcept { return _min_operations; }
    const std::optional<uint32_t>& get_max_operations() noexcept { return _max_operations; }
    const std::optional<double>& get_min_latency() noexcept { return _min_latency; }
    const std::optional<double>& get_max_latency() noexcept { return _max_latency; }
};

/**
 * Keeps track of feed operations started, completed and being committed.
 * Also tracks started and completed commit operations.
 */
class FeedOperationCounter {
public:
    FeedOperationCounter()
        : _operationsStarted(0),
          _operationsCompleted(0),
          _operationsStartedAtLastCommitStart(0),
          _commitsStarted(0),
          _commitsCompleted(0)
    {}
    void startOperation() { ++_operationsStarted; }
    void startCommit() {
        _commitsStarted++;
        _operationsStartedAtLastCommitStart = _operationsStarted;
    }

    void commitCompleted(size_t numOperations);

    size_t operationsSinceLastCommitStart() const {
        return _operationsStarted - _operationsStartedAtLastCommitStart;
    }
    size_t operationsInFlight() const { return _operationsStarted - _operationsCompleted; }
    size_t commitsInFlight() const { return _commitsStarted - _commitsCompleted; }
    bool shouldScheduleCommit() const {
        return (operationsInFlight() > 0) && (commitsInFlight() == 0);
    }
private:
    size_t  _operationsStarted;
    size_t  _operationsCompleted;
    size_t  _operationsStartedAtLastCommitStart;
    size_t  _commitsStarted;
    size_t  _commitsCompleted;
};

}
