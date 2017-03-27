// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/aggregation/grouping.h>
#include <map>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchcore/grouping/sessionid.h>

namespace search {

namespace grouping {

/**
 * A grouping session represents the execution of a grouping expression with one
 * or more passes. Multiple passes are supported by keeping internal state, and
 * providing a way to copy parts of this state into a context object for each
 * pass.
 **/
class GroupingSession
{
private:
    typedef std::shared_ptr<search::aggregation::Grouping> GroupingPtr;
    typedef std::map<uint32_t, GroupingPtr>                    GroupingMap;
    typedef std::vector<GroupingPtr>                           GroupingList;

    SessionId       _sessionId;
    GroupingContext _mgrContext;
    GroupingManager _groupingManager;
    GroupingMap     _groupingMap;
    fastos::TimeStamp _timeOfDoom;

public:
    typedef std::unique_ptr<GroupingSession> UP;

    /**
     * Create a new grouping session
     *
     * @param sessionId The session id of this session.
     * @param groupingContext grouping context.
     * @param attrCtx attribute context.
     **/
    GroupingSession(const SessionId & sessionId,
                    GroupingContext & groupingContext,
                    const search::attribute::IAttributeContext &attrCtx);
    GroupingSession(const GroupingSession &) = delete;
    GroupingSession &operator=(const GroupingSession &) = delete;

    /**
     * Release resources
     **/
    ~GroupingSession();

    /**
     * Return our session identifier
     **/
    const SessionId & getSessionId() const { return _sessionId; }

    /**
     * Initialize the session with data from the current context.
     * @param groupingContext The current grouping context.
     * @param attrCtx attribute context.
     **/
    void init(GroupingContext & groupingContext,
              const search::attribute::IAttributeContext &attrCtx);

    /**
     * This function is called to prepare for creation of individual
     * contexts for separate threads.
     *
     * @param num_threads number of threads that will request contexts
     **/
    void prepareThreadContextCreation(size_t num_threads);

    /**
     * Create a grouping context to be used by a single thread when
     * performing multi-threaded grouping. Thread 0 will get a
     * grouping context that shares groups with this session while
     * other threads will get equivalent copies that can later be
     * merged into the master context after partial grouping is
     * performed in parallel. Note that this thread may be called by
     * multiple threads at the same time.
     *
     * @param thread_id thread id
     * @param attrCtx attribute context.
     **/
    GroupingContext::UP createThreadContext(size_t thread_id,
                                            const search::attribute::IAttributeContext &attrCtx);

    /**
     * Return the GroupingManager to use when performing grouping.
     **/
    GroupingManager & getGroupingManager() { return _groupingManager; }

    /**
     * Continue excuting a query given a context.
     *
     * @param context The grouping context which contains information about the
     *                current pass.
     **/
    void continueExecution(GroupingContext & context);

    /**
     * Checks whether or not the session is finished.
     **/
    bool finished() const { return _groupingMap.empty(); }

    /**
     * Get this sessions timeout.
     */
    fastos::TimeStamp getTimeOfDoom() const { return _timeOfDoom; }
};

} // namespace search::grouping
} // namespace search

