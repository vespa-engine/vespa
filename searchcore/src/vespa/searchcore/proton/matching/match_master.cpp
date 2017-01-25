// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "match_master.h"
#include "docid_range_scheduler.h"
#include "match_loop_communicator.h"
#include "match_thread.h"
#include <vespa/searchlib/common/featureset.h>
#include <vespa/vespalib/util/thread_bundle.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.match_master");

namespace proton {
namespace matching {

using namespace search::fef;
using search::queryeval::SearchIterator;
using search::FeatureSet;

namespace {

struct TimedMatchLoopCommunicator : IMatchLoopCommunicator {
    IMatchLoopCommunicator &communicator;
    fastos::StopWatch rerank_time;
    TimedMatchLoopCommunicator(IMatchLoopCommunicator &com) : communicator(com) {}
    virtual double estimate_match_frequency(const Matches &matches) {
        return communicator.estimate_match_frequency(matches);
    }
    virtual size_t selectBest(const std::vector<feature_t> &sortedScores) {
        size_t result = communicator.selectBest(sortedScores);
        rerank_time.start();
        return result;
    }
    virtual RangePair rangeCover(const RangePair &ranges) {
        RangePair result = communicator.rangeCover(ranges);
        rerank_time.stop();
        return result;
    }
};

DocidRangeScheduler::UP
createScheduler(uint32_t numThreads, uint32_t numSearchPartitions, uint32_t numDocs)
{
    if (numSearchPartitions == 0) {
        return std::make_unique<AdaptiveDocidRangeScheduler>(numThreads, 1, numDocs);
    }
    if (numSearchPartitions <= numThreads) {
        return std::make_unique<PartitionDocidRangeScheduler>(numThreads, numDocs);
    }
    return std::make_unique<TaskDocidRangeScheduler>(numThreads, numSearchPartitions, numDocs);
}

} // namespace proton::matching::<unnamed>

ResultProcessor::Result::UP
MatchMaster::match(const MatchParams &params,
                   vespalib::ThreadBundle &threadBundle,
                   const MatchToolsFactory &matchToolsFactory,
                   ResultProcessor &resultProcessor,
                   uint32_t distributionKey,
                   uint32_t numSearchPartitions)
{
    fastos::StopWatch query_latency_time;
    query_latency_time.start();
    vespalib::DualMergeDirector mergeDirector(threadBundle.size());
    MatchLoopCommunicator communicator(threadBundle.size(), params.heapSize);
    TimedMatchLoopCommunicator timedCommunicator(communicator);
    DocidRangeScheduler::UP scheduler = createScheduler(threadBundle.size(), numSearchPartitions, params.numDocs);

    std::vector<MatchThread::UP> threadState;
    std::vector<vespalib::Runnable*> targets;
    for (size_t i = 0; i < threadBundle.size(); ++i) {
        IMatchLoopCommunicator &com =
            (i == 0)?
            static_cast<IMatchLoopCommunicator&>(timedCommunicator) :
            static_cast<IMatchLoopCommunicator&>(communicator);
        threadState.emplace_back(std::make_unique<MatchThread>(i, threadBundle.size(),
                        params, matchToolsFactory, com, *scheduler,
                        resultProcessor, mergeDirector, distributionKey));
        targets.push_back(threadState.back().get());
    }
    resultProcessor.prepareThreadContextCreation(threadBundle.size());
    threadBundle.run(targets);
    ResultProcessor::Result::UP reply = resultProcessor.makeReply();
    query_latency_time.stop();
    double query_time_s = query_latency_time.elapsed().sec();
    double rerank_time_s = timedCommunicator.rerank_time.elapsed().sec();
    double match_time_s = 0.0;
    for (size_t i = 0; i < threadState.size(); ++i) {
        match_time_s = std::max(match_time_s, threadState[i]->get_match_time());
        _stats.merge_partition(threadState[i]->get_thread_stats(), i);
    }
    _stats.queryLatency(query_time_s);
    _stats.matchTime(match_time_s - rerank_time_s);
    _stats.rerankTime(rerank_time_s);
    _stats.groupingTime(query_time_s - match_time_s);
    _stats.queries(1);
    if (matchToolsFactory.match_limiter().was_limited()) {
        _stats.limited_queries(1);        
    }
    return reply;
}

FeatureSet::SP
MatchMaster::getFeatureSet(const MatchToolsFactory &matchToolsFactory,
                           const std::vector<uint32_t> &docs, bool summaryFeatures)
{
    MatchTools::UP matchTools = matchToolsFactory.createMatchTools();
    RankProgram::UP rankProgram = summaryFeatures ? matchTools->summary_program() :
            matchTools->dump_program();

    std::vector<vespalib::string> featureNames;
    FeatureResolver resolver(rankProgram->get_seeds());
    featureNames.reserve(resolver.num_features());
    for (size_t i = 0; i < resolver.num_features(); ++i) {
        featureNames.emplace_back(resolver.name_of(i));
    }
    FeatureSet::SP retval(new FeatureSet(featureNames, docs.size()));
    if (docs.empty()) {
        return retval;
    }
    FeatureSet &fs = *retval.get();

    SearchIterator::UP search = matchTools->createSearch(rankProgram->match_data());
    search->initRange(docs.front(), docs.back()+1);
    for (uint32_t i = 0; i < docs.size(); ++i) {
        if (search->seek(docs[i])) {
            uint32_t docId = search->getDocId();
            search->unpack(docId);
            rankProgram->run(docId);
            search::feature_t * f = fs.getFeaturesByIndex(
                    fs.addDocId(docId));
            for (uint32_t j = 0; j < featureNames.size(); ++j) {
                f[j] = *resolver.resolve_number(j);
            }
        } else {
            LOG(debug, "getFeatureSet: Did not find hit for docid '%u'. "
                "Skipping hit", docs[i]);
        }
    }
    return retval;
}

} // namespace proton::matching
} // namespace proton
