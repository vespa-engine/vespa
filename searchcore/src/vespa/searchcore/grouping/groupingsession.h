// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sessionid.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/vespalib/util/time.h>
#include <vector>
#include <map>

namespace document { class DocumentType; }
namespace search::aggregation { class Grouping; }
namespace search::grouping {

class GroupingContext;
class GroupingManager;

/**
 * A grouping session represents the execution of a grouping
 * expression with one or more passes. Multiple passes are supported
 * by keeping internal state, and providing a way to copy parts of
 * this state into a context object for each pass.
 **/
class GroupingSession
{
/*
 * The grouping flow currently goes like this:
 * Matcher::match creates a GroupingContext from the request,
 * and looks for an existing session.
 * Assuming first time, the next step is in ResultProcessor
 * constructor, where a new GroupingSession is created.
 *
 * The new GroupingSession makes its own copy _mgrContext of the
 * GroupingContext with shared pointers to all the target Grouping
 * objects.  For groupings that can be fully processed the first time
 * through, the shared pointer will be to the object in the original
 * request, so the results end up there directly (skipping some of the
 * steps below). But most groupings require multiple passes, and the
 * target will instead be a Grouping copy which is shared into
 * _groupingMap, where the result will be cached for later passes.
 *
 * Then MatchMaster::match calls (via ResultProcessor)
 * prepareThreadContextCreation, and each MatchThread calls
 * (again via ResultProcessor) createThreadContext.
 *
 * Thread 0 gets a GroupingContext with shared pointers to the
 * Grouping objects owned by the GroupingSession, while other threads
 * get their own standalone Grouping objects.
 *
 * After matching, MatchThread::processResult will aggregate into the
 * Grouping objects via groupInRelevanceOrder or groupUnordered, and
 * fill in distribution key and global ID in the grouping results.
 *
 * The per-thread results are merged via GroupingSource::merge and
 * GroupingManager::merge, where the final merge target will be in
 * Thread 0 (meaning it ends up in _mgrContext here, shared with
 * either _groupingMap or ResultProcessor::_groupingContext).
 *
 * The next step happens in ResultProcessor::makeReply. If multiple
 * threads were used above, GroupingManager::prune() is called to
 * perform post-merge steps; often pruning group lists down to
 * maxGroups/precision target.  Note that the groups pruned here may
 * not be completely cleaned, since there's special code in
 * Group::Value::postMerge where actual destruct is delayed and
 * Group::Value::prune considers children beyond getChildrenSize()
 * which means that some groups may be "resurrected" after being
 * hidden.
 *
 * At this point the grouping objects in our _mgrContext holds the
 * "full" result from this content partition; to generate the actual
 * (first-pass) result requested from the QRS continueExecution() is
 * called.
 * This will copy as needed from the full result (found in
 * _groupingMap) into the original request (_groupingContext owned by
 * ResultProcessor) using mergePartial(), and serialize the results.
 * The serialized grouping result is swapped into the actual SearchReply,
 * and the GroupingSession is saved in the SessionManager (assuming
 * more than one pass is needed).
 *
 * The QRS GroupingExecutor will gather results from multiple content
 * nodes and do its own merging and pruning, and send a request for
 * next-pass results (again assuming multiple passes).
 * This will find the GroupingSession in SessionManager and
 * instead of performing search and aggregation, only
 * handleGroupingSession is called.
 * This will again call continueExecution, but now we will actually
 * prune from the full results anything that QRS wasn't interested in,
 * copy the partial result QRS wants to the request GroupingContext
 * where it's serialized, then handleGroupingSession swaps the
 * serialized result into a SearchReply and returns just that.
 * This repeats with new requests from QRS until all passes are
 * finished for all groupings.
 */
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
                    const attribute::IAttributeContext &attrCtx,
                    const document::DocumentType * documentType);
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
    void init(GroupingContext & groupingContext, const attribute::IAttributeContext &attrCtx, const document::DocumentType * documentType);

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
    std::unique_ptr<GroupingContext> createThreadContext(size_t thread_id, const attribute::IAttributeContext &attrCtx,
                                                         const document::DocumentType * documentType);

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
