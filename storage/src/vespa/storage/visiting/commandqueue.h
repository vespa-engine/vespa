// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class CommandQueue
 * @ingroup visiting
 *
 * @brief Keep an ordered queue of messages that can time out individually.
 * Messages are ordered by priority and arrival sequence.
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <atomic>
#include <vector>
#include <ostream>
#include <set>
#include <cassert>

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

    // wraps an iterator for std::set<CommandEntry *>, hiding the indirection
    template<typename I> struct wrap_set_iterator : public I {
        auto * operator-> () const { return I::operator*(); }
        auto & operator* () const { return *I::operator*(); }
    };
private:
    // container of CommandEntry instances, primarily indexed
    // on priority+sequenceId, with an extra index sorted
    // on deadline+sequenceId.
    struct CommandList {
        using MainSet = std::set<CommandEntry>;
        using EntryPtr = const CommandEntry *;
        struct DeadlineCmp {
            bool operator() (EntryPtr a, EntryPtr b) const {
                vespalib::steady_time dla = a->_deadline;
                vespalib::steady_time dlb = b->_deadline;
                if (dla != dlb) {
                    return dla < dlb;
                } else {
                    return (a->_sequenceId) < (b->_sequenceId);
                }
            }
        };
        using DeadlineSet = std::set<EntryPtr, DeadlineCmp>;
        MainSet byPriAndSeqSet;
        DeadlineSet byDeadlineSet;
        void insert(CommandEntry toadd) {
            auto [iter, added] = byPriAndSeqSet.emplace(std::move(toadd));
            assert(added);
            const CommandEntry &entry = *iter;
            byDeadlineSet.insert(&entry);
        }
        void erase(MainSet::iterator iter) {
            assert(iter != byPriAndSeqSet.end());
            const CommandEntry &entry = *iter;
            byDeadlineSet.erase(&entry);
            byPriAndSeqSet.erase(iter);
        }
        void remove(const CommandEntry &key) {
            auto iter = byPriAndSeqSet.find(key);
            erase(iter);
        }
        void clear() {
            for (auto it = byPriAndSeqSet.begin(); it != byPriAndSeqSet.end(); ) {
                const auto& entry = *it;
                byDeadlineSet.erase(&entry);
                it = byPriAndSeqSet.erase(it);
            }
        }
        ~CommandList();

        auto rbegin()       {  return byPriAndSeqSet.rbegin(); }
        auto rend()         {  return byPriAndSeqSet.rend(); }
        auto rbegin() const {  return byPriAndSeqSet.rbegin(); }
        auto rend()   const {  return byPriAndSeqSet.rend(); }
    };

    const framework::Clock& _clock;
    CommandList             _commands;
    uint64_t                _sequenceId;
    std::atomic<size_t>     _cached_size;

public:
    using iterator               = CommandList::MainSet::iterator;
    using const_iterator         = CommandList::MainSet::const_iterator;
    using reverse_iterator       = CommandList::MainSet::reverse_iterator;
    using const_reverse_iterator = CommandList::MainSet::const_reverse_iterator;
    using const_titerator        = wrap_set_iterator<typename CommandList::DeadlineSet::const_iterator>;

    explicit CommandQueue(const framework::Clock& clock)
        : _clock(clock),
          _sequenceId(0),
          _cached_size(0)
    {}

    iterator begin()             { return _commands.byPriAndSeqSet.begin(); }
    iterator end()               { return _commands.byPriAndSeqSet.end(); }
    const_iterator begin() const { return _commands.byPriAndSeqSet.begin(); }
    const_iterator end()   const { return _commands.byPriAndSeqSet.end(); }

    const_titerator tbegin() const {
        return wrap_set_iterator(_commands.byDeadlineSet.begin());
    }
    const_titerator tend() const {
        return wrap_set_iterator(_commands.byDeadlineSet.end());
    }

    bool empty() const { return _commands.byPriAndSeqSet.empty(); }
    size_t size() const { return _commands.byPriAndSeqSet.size(); }
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
        _cached_size.store(size(), std::memory_order_relaxed);
    }
};

template<class Command>
CommandQueue<Command>::CommandList::~CommandList() = default;

template<class Command>
std::pair<std::shared_ptr<Command>, vespalib::steady_time>
CommandQueue<Command>::releaseNextCommand()
{
    std::pair<std::shared_ptr<Command>, vespalib::steady_time> retVal;
    if (!empty()) {
        iterator first = begin();
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
    if (!empty()) {
        const_iterator first = begin();
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
    while (!empty()) {
        auto iter = tbegin();
        if (iter->_deadline > now)
            break;
        timed_out.emplace_back(*iter);
        _commands.remove(timed_out.back());
    }
    update_cached_size();
    return timed_out;
}

template <class Command>
std::pair<std::shared_ptr<Command>, vespalib::steady_time>
CommandQueue<Command>::releaseLowestPriorityCommand()
{
    if (!empty()) {
        // NOTE: works only for this special case:
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
    if (!empty()) {
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
