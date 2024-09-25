// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include <cstdint>
#include <iosfwd>
#include <string>

namespace documentapi {

class TestAndSetCondition {
private:
    // Ordinarily a client will only specify _either_ a selection or a required persistence timestamp,
    // but for backwards compatibility it's possible for both to be specified at the same time. The
    // semantics then is that nodes which understand the timestamp predicate _ignore_ the selection,
    // where other nodes will fall back to the selection. The responsibility falls on the distributor
    // to ensure that fanned out operations are handled in a consistent way based on what the underlying
    // content nodes report supporting.
    std::string _selection;
    uint64_t    _required_persistence_timestamp;

public:
    constexpr TestAndSetCondition() noexcept
        : _selection(),
          _required_persistence_timestamp(0)
    {}
    
    explicit TestAndSetCondition(std::string_view selection)
        : _selection(selection),
          _required_persistence_timestamp(0)
    {}

    explicit TestAndSetCondition(uint64_t required_persistence_timestamp) noexcept
        : _selection(),
          _required_persistence_timestamp(required_persistence_timestamp)
    {}

    TestAndSetCondition(std::string_view selection,
                        uint64_t required_persistence_timestamp)
        : _selection(selection),
          _required_persistence_timestamp(required_persistence_timestamp)
    {}

    ~TestAndSetCondition();

    TestAndSetCondition(const TestAndSetCondition&);
    TestAndSetCondition& operator=(const TestAndSetCondition&);

    TestAndSetCondition(TestAndSetCondition&&) noexcept = default;
    TestAndSetCondition& operator=(TestAndSetCondition&&) noexcept = default;

    [[nodiscard]] const std::string& getSelection() const noexcept { return _selection; }
    // A return value of 0 implies no timestamp predicate is set.
    [[nodiscard]] uint64_t required_persistence_timestamp() const noexcept {
        return _required_persistence_timestamp;
    }
    [[nodiscard]] bool has_selection() const noexcept { return !_selection.empty(); }
    [[nodiscard]] bool has_required_persistence_timestamp() const noexcept {
        return (_required_persistence_timestamp != 0);
    }
    [[nodiscard]] bool isPresent() const noexcept {
        return (has_selection() || has_required_persistence_timestamp());
    }

    bool operator==(const TestAndSetCondition& rhs) const noexcept {
        return ((_selection == rhs._selection) &&
                (_required_persistence_timestamp == rhs._required_persistence_timestamp));
    }
};

std::ostream& operator<<(std::ostream&, const TestAndSetCondition&);

}
