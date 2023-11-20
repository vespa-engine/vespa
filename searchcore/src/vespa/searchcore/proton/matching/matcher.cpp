// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matcher.h"
#include "isearchcontext.h"
#include "match_master.h"
#include "match_context.h"
#include "match_tools.h"
#include "match_params.h"
#include "sessionmanager.h"
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchlib/common/allocatedbitvector.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.matcher");

using search::fef::Properties;
using namespace search::fef::indexproperties::matching;
using namespace search::fef::indexproperties;
using namespace search::engine;
using namespace search::grouping;
using search::DocumentMetaData;
using search::LidUsageStats;
using search::MatchingElementsFields;
using search::MatchingElements;
using search::attribute::IAttributeContext;
using search::fef::MatchDataLayout;
using search::fef::MatchData;
using search::fef::RankSetup;
using search::fef::indexproperties::hitcollector::HeapSize;
using search::fef::indexproperties::hitcollector::ArraySize;
using search::fef::indexproperties::hitcollector::RankScoreDropLimit;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using vespalib::Doom;
using vespalib::FeatureSet;
using vespalib::make_string_short::fmt;

namespace proton::matching {

namespace {

constexpr vespalib::duration TIME_BEFORE_ALLOWING_SOFT_TIMEOUT_FACTOR_ADJUSTMENT = 60s;

// used to give out empty whitelist blueprints
struct StupidMetaStore : search::IDocumentMetaStore {
    static const search::AllocatedBitVector _dummy;
    const search::BitVector & getValidLids() const override { return _dummy; }
    bool getGid(DocId, GlobalId &) const override { return false; }
    bool getGidEvenIfMoved(DocId, GlobalId &) const override { return false; }
    bool getLid(const GlobalId &, DocId &) const override { return false; }
    DocumentMetaData getMetaData(const GlobalId &) const override { return {}; }
    void getMetaData(const BucketId &, DocumentMetaData::Vector &) const override { }
    DocId getCommittedDocIdLimit() const override { return 1; }
    DocId getNumUsedLids() const override { return 0; }
    DocId getNumActiveLids() const override { return 0; }
    uint64_t getCurrentGeneration() const override { return 0; }
    LidUsageStats getLidUsageStats() const override { return {}; }
    Blueprint::UP createWhiteListBlueprint() const override { return {}; }
    void foreach(const search::IGidToLidMapperVisitor &) const override { }
};

const search::AllocatedBitVector StupidMetaStore::_dummy(1);

size_t
numThreads(size_t hits, size_t minHits) {
    return static_cast<size_t>(std::ceil(double(hits) / double(minHits)));
}

class LimitedThreadBundleWrapper final : public vespalib::ThreadBundle
{
public:
    LimitedThreadBundleWrapper(vespalib::ThreadBundle &threadBundle, uint32_t maxThreads)
        : _threadBundle(threadBundle),
          _maxThreads(std::min(maxThreads, static_cast<uint32_t>(threadBundle.size())))
    { }
    size_t size() const override { return _maxThreads; }
    void run(vespalib::Runnable* const* targets, size_t cnt) override {
        _threadBundle.run(targets, cnt);
    }
private:
    vespalib::ThreadBundle &_threadBundle;
    const uint32_t          _maxThreads;
};

bool
willNeedRanking(const SearchRequest & request, const GroupingContext & groupingContext,
                search::feature_t rank_score_drop_limit)
{
    return (groupingContext.needRanking() || (request.maxhits != 0))
           && (request.sortSpec.empty() ||
               (request.sortSpec.find("[rank]") != vespalib::string::npos) ||
               !std::isnan(rank_score_drop_limit));
}

SearchReply::UP
handleGroupingSession(SessionManager &sessionMgr, GroupingContext & groupingContext, GroupingSession::UP groupingSession)
{
    auto reply = std::make_unique<SearchReply>();
    groupingSession->continueExecution(groupingContext);
    groupingContext.getResult().swap(reply->groupResult);
    if (!groupingSession->finished()) {
        sessionMgr.insert(std::move(groupingSession));
    }
    return reply;
}

}  // namespace proton::matching::<unnamed>

Matcher::Matcher(const search::index::Schema &schema, Properties props, const vespalib::Clock &clock,
                 QueryLimiter &queryLimiter, const search::fef::IRankingAssetsRepo &rankingAssetsRepo, uint32_t distributionKey)
  : _indexEnv(distributionKey, schema, std::move(props), rankingAssetsRepo),
    _blueprintFactory(),
    _rankSetup(),
    _viewResolver(ViewResolver::createFromSchema(schema)),
    _statsLock(),
    _stats(softtimeout::Factor::lookup(_indexEnv.getProperties())),
    _startTime(my_clock::now()),
    _clock(clock),
    _queryLimiter(queryLimiter),
    _distributionKey(distributionKey)
{
    search::features::setup_search_features(_blueprintFactory);
    search::fef::test::setup_fef_test_plugin(_blueprintFactory);
    _rankSetup = std::make_shared<search::fef::RankSetup>(_blueprintFactory, _indexEnv);
    _rankSetup->configure(); // reads config values from the property map
    if (!_rankSetup->compile()) {
        throw vespalib::IllegalArgumentException(fmt("failed to compile rank setup :\n%s",
                                                     _rankSetup->getJoinedWarnings().c_str()), VESPA_STRLOC);
    }
}

Matcher::~Matcher() = default;

MatchingStats
Matcher::getStats()
{
    std::lock_guard<std::mutex> guard(_statsLock);
    MatchingStats stats = std::move(_stats);
    _stats = MatchingStats(stats.softDoomFactor());
    return stats;
}

std::unique_ptr<MatchToolsFactory>
Matcher::create_match_tools_factory(const search::engine::Request &request, ISearchContext &searchContext,
                                    IAttributeContext &attrContext, const search::IDocumentMetaStore &metaStore,
                                    const Properties &feature_overrides, vespalib::ThreadBundle &thread_bundle,
                                    const IDocumentMetaStoreContext::IReadGuard::SP * metaStoreReadGuard,
                                    uint32_t maxHits, bool is_search) const
{
    const Properties & rankProperties = request.propertiesMap.rankProperties();
    bool softTimeoutEnabled = softtimeout::Enabled::lookup(rankProperties, _rankSetup->getSoftTimeoutEnabled());
    bool hasFactorOverride = softtimeout::Factor::isPresent(rankProperties);
    double factor = softTimeoutEnabled
                    ? ( hasFactorOverride
                        ? softtimeout::Factor::lookup(rankProperties, _stats.softDoomFactor())
                        : _stats.softDoomFactor())
                    : 0.95;
    vespalib::duration safeLeft = std::chrono::duration_cast<vespalib::duration>(request.getTimeLeft() * factor);
    vespalib::steady_time safeDoom(_clock.getTimeNS() + safeLeft);
    if (softTimeoutEnabled) {
        LOG(debug, "Soft-timeout computed factor=%1.3f, used factor=%1.3f, userSupplied=%d, softTimeout=%" PRId64,
                   _stats.softDoomFactor(), factor, hasFactorOverride, vespalib::count_ns(safeLeft));
    }
    vespalib::Doom doom(_clock, safeDoom, request.getTimeOfDoom(), hasFactorOverride);
    return std::make_unique<MatchToolsFactory>(_queryLimiter, doom, searchContext, attrContext,
                                               request.trace(), request.getStackRef(), request.location,
                                               _viewResolver, metaStore, _indexEnv, *_rankSetup,
                                               rankProperties, feature_overrides, thread_bundle,
                                               metaStoreReadGuard, maxHits, is_search);
}

size_t
Matcher::computeNumThreadsPerSearch(Blueprint::HitEstimate hits, const Properties & rankProperties) const {
    size_t threads = NumThreadsPerSearch::lookup(rankProperties, _rankSetup->getNumThreadsPerSearch());
    uint32_t minHitsPerThread = MinHitsPerThread::lookup(rankProperties, _rankSetup->getMinHitsPerThread());
    if ((threads > 1) && (minHitsPerThread > 0)) {
        threads = (hits.empty) ? 1 : std::min(threads, numThreads(hits.estHits, minHitsPerThread));
    }
    return threads;
}

namespace {

void
traceQuery(uint32_t traceLevel, Trace & trace, const Query & query) {
    if (traceLevel <= trace.getLevel()) {
        if (query.peekRoot()) {
            vespalib::slime::ObjectInserter inserter(trace.createCursor("query_execution_plan"), "optimized");
            query.peekRoot()->asSlime(inserter);
        }
    }
}

void
updateCoverage(Coverage & coverage, const MaybeMatchPhaseLimiter & limiter, const MatchingStats & my_stats,
               const search::IDocumentMetaStore &metaStore, const bucketdb::BucketDBOwner & bucketdb)
{
    size_t spaceEstimate = (my_stats.softDoomed())
                           ? my_stats.docidSpaceCovered()
                           : limiter.getDocIdSpaceEstimate();
    // note: this is actually totalSpace+1, since 0 is reserved
    uint32_t totalSpace = metaStore.getCommittedDocIdLimit();
    if (spaceEstimate >= totalSpace) {
        // estimate is too high, clamp it
        spaceEstimate = totalSpace;
    } else {
        // account for docid 0 reserved
        spaceEstimate += 1;
    }
    coverage.setActive(metaStore.getNumActiveLids());
    coverage.setTargetActive(bucketdb.getNumActiveDocs());
    coverage.setCovered((spaceEstimate *  coverage.getActive()) / totalSpace);
    if (limiter.was_limited()) {
        coverage.degradeMatchPhase();
        LOG(debug, "was limited, degraded from match phase");
    }
    if (my_stats.softDoomed()) {
        coverage.degradeTimeout();
        LOG(debug, "soft doomed, degraded from timeout covered = %" PRIu64, coverage.getCovered());
    }
    LOG(debug, "docid limit = %d", totalSpace);
    LOG(debug, "num active lids = %" PRIu64, coverage.getActive());
    LOG(debug, "space Estimate = %zd", spaceEstimate);
    LOG(debug, "covered = %" PRIu64, coverage.getCovered());
}

}

SearchReply::UP
Matcher::match(const SearchRequest &request, vespalib::ThreadBundle &threadBundle,
               ISearchContext &searchContext, IAttributeContext &attrContext, SessionManager &sessionMgr,
               const search::IDocumentMetaStore &metaStore, const bucketdb::BucketDBOwner & bucketdb,
               SearchSession::OwnershipBundle &&owned_objects)
{
    vespalib::Timer total_matching_time;
    MatchingStats my_stats;
    SearchReply::UP reply = std::make_unique<SearchReply>();
    bool isDoomExplicit = false;
    { // we want to measure full set-up and tear-down time as part of
      // collateral time
        GroupingContext groupingContext(metaStore.getValidLids(), _clock, request.getTimeOfDoom(),
                                        request.groupSpec.data(), request.groupSpec.size());
        SessionId sessionId(request.sessionId.data(), request.sessionId.size());
        bool shouldCacheSearchSession = false;
        bool shouldCacheGroupingSession = false;
        if (!sessionId.empty()) {
            const Properties &cache_props = request.propertiesMap.cacheProperties();
            shouldCacheGroupingSession = cache_props.lookup("grouping").found();
            shouldCacheSearchSession = cache_props.lookup("query").found();
            if (shouldCacheGroupingSession) {
                GroupingSession::UP session(sessionMgr.pickGrouping(sessionId));
                if (session) {
                    return handleGroupingSession(sessionMgr, groupingContext, std::move(session));
                }
            }
        }
        const Properties *feature_overrides = &request.propertiesMap.featureOverrides();
        if (shouldCacheSearchSession) {
            // These should have been moved instead.
            owned_objects.feature_overrides = std::make_unique<Properties>(*feature_overrides);
            feature_overrides = owned_objects.feature_overrides.get();
        }

        MatchToolsFactory::UP mtf = create_match_tools_factory(request, searchContext, attrContext, metaStore,
                                                               *feature_overrides, threadBundle, &owned_objects.readGuard,
                                                               searchContext.getDocIdLimit(), true);
        isDoomExplicit = mtf->get_request_context().getDoom().isExplicitSoftDoom();
        traceQuery(6, request.trace(), mtf->query());
        if (!mtf->valid()) {
            return reply;
        }
        if (mtf->get_request_context().getDoom().soft_doom()) {
            vespalib::Issue::report("Search request soft doomed during query setup and initialization.");
            return reply;
        }

        const Properties & rankProperties = request.propertiesMap.rankProperties();
        uint32_t heapSize = HeapSize::lookup(rankProperties, _rankSetup->getHeapSize());
        uint32_t arraySize = ArraySize::lookup(rankProperties, _rankSetup->getArraySize());
        search::feature_t rank_score_drop_limit = RankScoreDropLimit::lookup(rankProperties, _rankSetup->getRankScoreDropLimit());

        MatchParams params(searchContext.getDocIdLimit(), heapSize, arraySize, rank_score_drop_limit,
                           request.offset, request.maxhits, !_rankSetup->getSecondPhaseRank().empty(),
                           willNeedRanking(request, groupingContext, rank_score_drop_limit));

        ResultProcessor rp(attrContext, metaStore, sessionMgr, groupingContext, sessionId,
                           request.sortSpec, params.offset, params.hits);

        size_t numThreadsPerSearch = computeNumThreadsPerSearch(mtf->estimate(), rankProperties);
        LimitedThreadBundleWrapper limitedThreadBundle(threadBundle, numThreadsPerSearch);
        MatchMaster master;
        uint32_t numParts = NumSearchPartitions::lookup(rankProperties, _rankSetup->getNumSearchPartitions());
        if (limitedThreadBundle.size() > 1) {
            attrContext.enableMultiThreadSafe();
        }
        ResultProcessor::Result::UP result = master.match(request.trace(), params, limitedThreadBundle, *mtf, rp,
                                                          _distributionKey, numParts);
        my_stats = MatchMaster::getStats(std::move(master));
        reply = std::move(result->_reply);
        Coverage & coverage = reply->coverage;
        updateCoverage(coverage, mtf->match_limiter(), my_stats, metaStore, bucketdb);

        LOG(debug, "numThreadsPerSearch = %zu. Configured = %d, estimated hits=%d, totalHits=%" PRIu64 ", rankprofile=%s",
            numThreadsPerSearch, _rankSetup->getNumThreadsPerSearch(), mtf->estimate().estHits, reply->totalHitCount,
            request.ranking.c_str());

        if (shouldCacheSearchSession && ((result->_numFs4Hits != 0) || shouldCacheGroupingSession)) {
            auto session = std::make_shared<SearchSession>(sessionId, request.getStartTime(), request.getTimeOfDoom(),
                                                           std::move(mtf), std::move(owned_objects));
            session->releaseEnumGuards();
            sessionMgr.insert(std::move(session));
        }
    }
    double querySetupTime = vespalib::to_s(total_matching_time.elapsed()) - my_stats.queryLatencyAvg();
    my_stats.querySetupTime(querySetupTime);
    updateStats(my_stats, request, reply->coverage, isDoomExplicit);
    return reply;
}

void
Matcher::updateStats(const MatchingStats & my_stats, const search::engine::Request & request,
                     const Coverage & coverage, bool isDoomExplicit) {
    vespalib::duration duration = request.getTimeUsed();
    std::lock_guard<std::mutex> guard(_statsLock);
    _stats.add(my_stats);
    if (my_stats.softDoomed()) {
        double old = _stats.softDoomFactor();
        vespalib::duration overtimeLimit = std::chrono::duration_cast<vespalib::duration>((1.0 - _rankSetup->getSoftTimeoutTailCost()) * request.getTimeout());
        vespalib::duration adjustedDuration = duration - my_stats.doomOvertime();
        if (adjustedDuration < vespalib::duration::zero()) {
            adjustedDuration = vespalib::duration::zero();
        }
        bool allowedSoftTimeoutFactorAdjustment = ((my_clock::now() - _startTime) > TIME_BEFORE_ALLOWING_SOFT_TIMEOUT_FACTOR_ADJUSTMENT)
                                                  && ! isDoomExplicit;
        if (allowedSoftTimeoutFactorAdjustment) {
            _stats.updatesoftDoomFactor(request.getTimeout(), overtimeLimit, adjustedDuration);
        }
        if ((_stats.softDoomed() < 10) || (_stats.softDoomed()%100 == 0)) {
            LOG(info,
                "Triggered softtimeout %s count: %zu. Coverage = %" PRIu64 " of %" PRIu64 " documents. request=%1.3f,"
                " doomOvertime=%1.3f, overtime_limit=%1.3f and duration=%1.3f, rankprofile=%s"
                ", factor %s adjusted from %1.3f to %1.3f",
                isDoomExplicit ? "with query override" : "factor adjustment",
                _stats.softDoomed(), coverage.getCovered(), coverage.getActive(),
                vespalib::to_s(request.getTimeout()), vespalib::to_s(my_stats.doomOvertime()),
                vespalib::to_s(overtimeLimit), vespalib::to_s(duration),
                request.ranking.c_str(), (allowedSoftTimeoutFactorAdjustment ? "" : "NOT "), old,
                _stats.softDoomFactor());
        }
    }
}

FeatureSet::SP
Matcher::getSummaryFeatures(const DocsumRequest & req, ISearchContext & searchCtx,
                            IAttributeContext & attrCtx, SessionManager &sessionMgr) const
{
    auto docsum_matcher = create_docsum_matcher(req, searchCtx, attrCtx, sessionMgr);
    return docsum_matcher->get_summary_features();
}

FeatureSet::SP
Matcher::getRankFeatures(const DocsumRequest & req, ISearchContext & searchCtx,
                         IAttributeContext & attrCtx, SessionManager &sessionMgr) const
{
    auto docsum_matcher = create_docsum_matcher(req, searchCtx, attrCtx, sessionMgr);
    return docsum_matcher->get_rank_features();
}

MatchingElements::UP
Matcher::get_matching_elements(const DocsumRequest &req, ISearchContext &search_ctx,
                               IAttributeContext &attr_ctx, SessionManager &session_manager,
                               const MatchingElementsFields &fields) const
{
    auto docsum_matcher = create_docsum_matcher(req, search_ctx, attr_ctx, session_manager);
    return docsum_matcher->get_matching_elements(fields);
}

DocsumMatcher::UP
Matcher::create_docsum_matcher(const DocsumRequest &req, ISearchContext &search_ctx,
                               IAttributeContext &attr_ctx, SessionManager &session_manager) const
{
    std::vector<uint32_t> docs;
    docs.reserve(req.hits.size());
    for (const auto &hit : req.hits) {
        if (hit.docid != search::endDocId) {
            docs.push_back(hit.docid);
        }
    }
    std::sort(docs.begin(), docs.end());
    SessionId sessionId(req.sessionId.data(), req.sessionId.size());
    bool expectedSessionCached(false);
    if (!sessionId.empty()) {
        const Properties &cache_props = req.propertiesMap.cacheProperties();
        expectedSessionCached = cache_props.lookup("query").found();
        if (expectedSessionCached) {
            SearchSession::SP session(session_manager.pickSearch(sessionId));
            if (session) {
                return std::make_unique<DocsumMatcher>(std::move(session), std::move(docs));
            }
        }
    }
    StupidMetaStore meta;
    MatchToolsFactory::UP mtf = create_match_tools_factory(req, search_ctx, attr_ctx, meta,
                                                           req.propertiesMap.featureOverrides(),
                                                           vespalib::ThreadBundle::trivial(), nullptr, docs.size(), false);
    if (!mtf->valid()) {
        LOG(warning, "could not initialize docsum matching: %s",
            (expectedSessionCached) ? "session has expired" : "invalid query");
        return std::make_unique<DocsumMatcher>();
    }
    return std::make_unique<DocsumMatcher>(std::move(mtf), std::move(docs));
}

bool
Matcher::canProduceSummaryFeatures() const {
    return ! _rankSetup->getSummaryFeatures().empty();
}

}
