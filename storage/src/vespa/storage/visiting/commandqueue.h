// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <list>
#include <ostream>

namespace storage {

template<class Command>
class CommandQueue : public vespalib::Printable
{
public:
    struct CommandEntry {
        typedef typename Command::Priority PriorityType;
        std::shared_ptr<Command> _command;
        uint64_t _time;
        uint64_t _sequenceId;
        PriorityType _priority;

        CommandEntry(const std::shared_ptr<Command>& cmd,
                     uint64_t time,
                     uint64_t sequenceId,
                     PriorityType priority)
            : _command(cmd), _time(time), _sequenceId(sequenceId), _priority(priority)
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
    typedef boost::multi_index::multi_index_container<
                CommandEntry,
                boost::multi_index::indexed_by<
                    boost::multi_index::ordered_unique<
                        boost::multi_index::identity<CommandEntry>
                    >,
                    boost::multi_index::ordered_non_unique<
                        boost::multi_index::member<CommandEntry, uint64_t, &CommandEntry::_time>
                    >
                >
            > CommandList;
    typedef typename boost::multi_index
                        ::nth_index<CommandList, 1>::type timelist;

    const framework::Clock& _clock;
    mutable CommandList _commands;
    uint64_t _sequenceId;

public:
    typedef typename CommandList::iterator iterator;
    typedef typename CommandList::reverse_iterator reverse_iterator;
    typedef typename CommandList::const_iterator const_iterator;
    typedef typename CommandList::const_reverse_iterator const_reverse_iterator;
    typedef typename timelist::const_iterator const_titerator;

    CommandQueue(const framework::Clock& clock)
        : _clock(clock),
          _sequenceId(0) {}

    const framework::Clock& getTimer() const { return _clock; }

    iterator begin() { return _commands.begin(); }
    iterator end() { return _commands.end(); }

    const_iterator begin() const { return _commands.begin(); }
    const_iterator end() const { return _commands.end(); }

    const_titerator tbegin() const {
        timelist& tl = boost::multi_index::get<1>(_commands);
        return tl.begin();
    }
    const_titerator tend() const {
        timelist& tl = boost::multi_index::get<1>(_commands);
        return tl.end();
    }

    bool empty() const { return _commands.empty(); }
    uint32_t size() const { return _commands.size(); }
    std::pair<std::shared_ptr<Command>, time_t> releaseNextCommand();
    std::shared_ptr<Command> peekNextCommand() const;
    void add(const std::shared_ptr<Command>& msg);
    void erase(iterator it) { _commands.erase(it); }
    std::list<CommandEntry> releaseTimedOut();
    std::pair<std::shared_ptr<Command>, time_t> releaseLowestPriorityCommand();

    std::shared_ptr<Command> peekLowestPriorityCommand() const;
    void clear() { return _commands.clear(); }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


template<class Command>
std::pair<std::shared_ptr<Command>, time_t>
CommandQueue<Command>::releaseNextCommand()
{
    std::pair<std::shared_ptr<Command>, time_t> retVal(
                std::shared_ptr<Command>(), 0);
    if (!_commands.empty()) {
        iterator first = _commands.begin();
        retVal.first = first->_command;
        retVal.second = first->_time;
        _commands.erase(first);
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
    framework::MicroSecTime time(_clock.getTimeInMicros()
            + framework::MicroSecTime(vespalib::count_us(cmd->getQueueTimeout())));
    _commands.insert(CommandEntry(cmd, time.getTime(), ++_sequenceId, cmd->getPriority()));
}

template<class Command>
std::list<typename CommandQueue<Command>::CommandEntry>
CommandQueue<Command>::releaseTimedOut()
{
    std::list<CommandEntry> mylist;
    framework::MicroSecTime time(_clock.getTimeInMicros());
    while (!empty() && tbegin()->_time <= time.getTime()) {
        mylist.push_back(*tbegin());
        timelist& tl = boost::multi_index::get<1>(_commands);
        tl.erase(tbegin());
    }
    return mylist;
}

template <class Command>
std::pair<std::shared_ptr<Command>, time_t>
CommandQueue<Command>::releaseLowestPriorityCommand()
{
    if (!_commands.empty()) {
        iterator last = (++_commands.rbegin()).base();
        time_t time = last->_time;
        std::shared_ptr<Command> cmd(last->_command);
        _commands.erase(last);
        return std::pair<std::shared_ptr<Command>, time_t>(cmd, time);
    } else {
        return std::pair<std::shared_ptr<Command>, time_t>(
                std::shared_ptr<Command>(), 0);
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
            << ", time " << it->_time << "\n";
    }
    out << indent << "Time order:";
    for (const_titerator it = tbegin(); it != tend(); ++it) {
        out << "\n" << indent << *it->_command << ", priority " << it->_priority
            << ", time " << it->_time;
    }
}

} // storage
