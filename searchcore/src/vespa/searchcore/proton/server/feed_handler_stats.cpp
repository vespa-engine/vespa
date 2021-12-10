// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feed_handler_stats.h"
#include <cassert>
#include <vespa/log/log.h>

LOG_SETUP(".proton.server.feed_handler_stats");

namespace proton {

namespace {

template <typename T>
void update_min_max(T value, std::optional<T>& min, std::optional<T>& max)
{
    if (!min.has_value() || value < min.value()) {
        min = value;
    }
    if (!max.has_value() || value > max.value()) {
        max = value;
    }
}

}

FeedHandlerStats::FeedHandlerStats(uint64_t commits, uint64_t operations, double total_latency) noexcept
    : _commits(commits),
      _operations(operations),
      _total_latency(total_latency),
      _min_operations(),
      _max_operations(),
      _min_latency(),
      _max_latency()
{
}

FeedHandlerStats::FeedHandlerStats() noexcept
    : FeedHandlerStats(0, 0, 0.0)
{
}

FeedHandlerStats::~FeedHandlerStats() = default;


FeedHandlerStats&
FeedHandlerStats::operator-=(const FeedHandlerStats& rhs) noexcept
{
    _commits -= rhs._commits;
    _operations -= rhs._operations;
    _total_latency -= rhs._total_latency;
    return *this;
}

void
FeedHandlerStats::add_commit(uint32_t operations, double latency) noexcept
{
    ++_commits;
    _operations += operations;
    _total_latency += latency;
    update_min_max(operations, _min_operations, _max_operations);
    update_min_max(latency, _min_latency, _max_latency);
}

void
FeedHandlerStats::reset_min_max() noexcept
{
    _min_operations.reset();
    _max_operations.reset();
    _min_latency.reset();
    _max_latency.reset();
}

void
FeedOperationCounter::commitCompleted(size_t numOperations) {
    assert(_commitsStarted > _commitsCompleted);
    assert(_operationsStarted >= _operationsCompleted + numOperations);
    _operationsCompleted += numOperations;
    _commitsCompleted++;
    LOG(spam, "%zu: onCommitDone(%zu) total=%zu left=%zu",
        _commitsCompleted, numOperations, _operationsCompleted, operationsInFlight());
}

}
