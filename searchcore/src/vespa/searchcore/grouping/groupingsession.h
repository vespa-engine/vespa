// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sessionid.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/vespalib/util/time.h>
#include <vector>
#include <map>

namespace search::aggregation { class Grouping; }
namespace search::grouping {

class GroupingContext;
class GroupingManager;

/**
 * A grouping session represents the execution of a grouping expression with one
 * or more passes. Multiple passes are supported by keeping internal state, and
 * providing a way to copy parts of this state into a context object for each
 * pass.
 **/
class GroupingSession
{
private:
    using GroupingPtr = std::shared_ptr<aggregation::Grouping>;
    using GroupingMap = std::map<uint32_t, GroupingPtr>;
    using GroupingList = std::vector<GroupingPtr>;

    SessionId                        _sessionId;
    std::unique_ptr<GroupingContext> _mgrContext;
    std::unique_ptr<GroupingManager> _groupingManager;
    GroupingMap                      _groupingMap;
    vespalib::steady_time            _timeOfDoom;

public:
    using UP = std::unique_ptr<GroupingSession>;

    /**
     * Create a new grouping session
     *
     * @param sessionId The session id of this session.
     * @param groupingContext grouping context.
     * @param attrCtx attribute context.
     **/
    GroupingSession(const SessionId & sessionId,
                    GroupingContext & groupingContext,
                    const attribute::IAttributeContext &attrCtx);
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
    void init(GroupingContext & groupingContext, const attribute::IAttributeContext &attrCtx);

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
    std::unique_ptr<GroupingContext> createThreadContext(size_t thread_id, const attribute::IAttributeContext &attrCtx);

    /**
     * Return the GroupingManager to use when performing grouping.
     **/
    GroupingManager & getGroupingManager() { return *_groupingManager; }

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
    vespalib::steady_time getTimeOfDoom() const { return _timeOfDoom; }
};

}
