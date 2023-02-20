// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class CommandQueue
 * @ingroup visiting
 *
 * @brief Keep an ordered queue of messages that can time out individually.
 * Messages are ordered by priority and arrival sequence.
 */

#pragma once

#include <boost/multi_index_container.hpp>
#include <boost/multi_index/identity.hpp>
#include <boost/multi_index/member.hpp>
#include <boost/multi_index/mem_fun.hpp>
#include <boost/multi_index/ordered_index.hpp>
#include <boost/multi_index/sequenced_index.hpp>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <atomic>
#include <vector>
#include <ostream>

namespace storage {

template<class Command>
class CommandQueue : public vespalib::Printable
{
public:
    struct CommandEntry {
        using PriorityType = typename Command::Priority;

        std::shared_ptr<Command> _command;
        vespalib::steady_time    _deadline;
        uint64_t                 _sequenceId;
        PriorityType             _priority;

        CommandEntry(const std::shared_ptr<Command>& cmd,
                     vespalib::steady_time deadline,
                     uint64_t sequenceId,
                     PriorityType priority)
            : _command(cmd),
              _deadline(deadline),
              _sequenceId(sequenceId),
              _priority(priority)
        {}

        // Sort on both priority and sequence ID
        bool operator<(const CommandEntry& entry) const {
            if (_priority != entry._priority) {
                return (_priority < entry._priority);
            }
            return (_sequenceId < entry._sequenceId);
        }
    };

private:
    using CommandList = boost::multi_index::multi_index_container<
                CommandEntry,
                boost::multi_index::indexed_by<
                    boost::multi_index::ordered_unique<
                        boost::multi_index::identity<CommandEntry>
                    >,
                    boost::multi_index::ordered_non_unique<
                        boost::multi_index::member<CommandEntry, vespalib::steady_time, &CommandEntry::_deadline>
                    >
                >
            >;
    using timelist = typename boost::multi_index::nth_index<CommandList, 1>::type;

    const framework::Clock& _clock;
    mutable CommandList     _commands;
    uint64_t                _sequenceId;
    std::atomic<size_t>     _cached_size;

public:
    using iterator = typename CommandList::iterator;
    using reverse_iterator = typename CommandList::reverse_iterator;
    using const_iterator = typename CommandList::const_iterator;
    using const_reverse_iterator = typename CommandList::const_reverse_iterator;
    using const_titerator = typename timelist::const_iterator;

    explicit CommandQueue(const framework::Clock& clock)
        : _clock(clock),
          _sequenceId(0),
          _cached_size(0)
    {}

    iterator begin() { return _commands.begin(); }
    iterator end() { return _commands.end(); }

    const_iterator begin() const { return _commands.begin(); }
    const_iterator end() const { return _commands.end(); }

    const_titerator tbegin() const {
        auto& tl = boost::multi_index::get<1>(_commands);
        return tl.begin();
    }
    const_titerator tend() const {
        auto& tl = boost::multi_index::get<1>(_commands);
        return tl.end();
    }

    bool empty() const { return _commands.empty(); }
    size_t size() const { return _commands.size(); }
    size_t relaxed_atomic_size() const noexcept { return _cached_size.load(std::memory_order_relaxed); }
    std::pair<std::shared_ptr<Command>, vespalib::steady_time> releaseNextCommand();
    std::shared_ptr<Command> peekNextCommand() const;
    void add(const std::shared_ptr<Command>& msg);
    void erase(iterator it) {
        _commands.erase(it);
        update_cached_size();
    }
    std::vector<CommandEntry> releaseTimedOut();
    std::pair<std::shared_ptr<Command>, vespalib::steady_time> releaseLowestPriorityCommand();

    std::shared_ptr<Command> peekLowestPriorityCommand() const;
    void clear() { return _commands.clear(); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

private:
    void update_cached_size() noexcept {
        _cached_size.store(_commands.size(), std::memory_order_relaxed);
    }
};


template<class Command>
std::pair<std::shared_ptr<Command>, vespalib::steady_time>
CommandQueue<Command>::releaseNextCommand()
{
    std::pair<std::shared_ptr<Command>, vespalib::steady_time> retVal;
    if (!_commands.empty()) {
        iterator first = _commands.begin();
        retVal.first = first->_command;
        retVal.second = first->_deadline;
        _commands.erase(first);
        update_cached_size();
    }
    return retVal;
}

template<class Command>
std::shared_ptr<Command>
CommandQueue<Command>::peekNextCommand() const
{
    if (!_commands.empty()) {
        const_iterator first = _commands.begin();
        return first->_command;
    } else {
        return std::shared_ptr<Command>();
    }
}

template<class Command>
void
CommandQueue<Command>::add(const std::shared_ptr<Command>& cmd)
{
    auto deadline = _clock.getMonotonicTime() + cmd->getQueueTimeout();
    _commands.insert(CommandEntry(cmd, deadline, ++_sequenceId, cmd->getPriority()));
    update_cached_size();
}

template<class Command>
std::vector<typename CommandQueue<Command>::CommandEntry>
CommandQueue<Command>::releaseTimedOut()
{
    std::vector<CommandEntry> timed_out;
    auto now = _clock.getMonotonicTime();
    while (!empty() && (tbegin()->_deadline <= now)) {
        timed_out.emplace_back(*tbegin());
        timelist& tl = boost::multi_index::get<1>(_commands);
        tl.erase(tbegin());
    }
    update_cached_size();
    return timed_out;
}

template <class Command>
std::pair<std::shared_ptr<Command>, vespalib::steady_time>
CommandQueue<Command>::releaseLowestPriorityCommand()
{
    if (!_commands.empty()) {
        iterator last = (++_commands.rbegin()).base();
        auto deadline = last->_deadline;
        std::shared_ptr<Command> cmd(last->_command);
        _commands.erase(last);
        update_cached_size();
        return {cmd, deadline};
    } else {
        return {};
    }
}

template <class Command>
std::shared_ptr<Command>
CommandQueue<Command>::peekLowestPriorityCommand() const
{
    if (!_commands.empty()) {
        const_reverse_iterator last = _commands.rbegin();
        return last->_command;
    } else {
        return std::shared_ptr<Command>();
    }
}

template<class Command>
void
CommandQueue<Command>::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose;
    out << "Insert order:\n";
    for (const_iterator it = begin(); it != end(); ++it) {
        out << indent << *it->_command << ", priority " << it->_priority
            << ", time " << vespalib::count_ms(it->_deadline.time_since_epoch()) << "\n";
    }
    out << indent << "Time order:";
    for (const_titerator it = tbegin(); it != tend(); ++it) {
        out << "\n" << indent << *it->_command << ", priority " << it->_priority
            << ", time " << vespalib::count_ms(it->_deadline.time_since_epoch());
    }
}

} // storage
