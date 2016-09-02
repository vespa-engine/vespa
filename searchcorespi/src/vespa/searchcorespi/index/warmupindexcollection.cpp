// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcorespi/index/warmupindexcollection.h>
#include <vespa/searchcorespi/index/idiskindex.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchcorespi.index.warmupindexcollection");

namespace searchcorespi {

using search::query::StringBase;
using search::queryeval::Blueprint;
using search::fef::MatchDataLayout;
using search::queryeval::SearchIterator;
using search::queryeval::ISourceSelector;
using vespalib::makeTask;
using vespalib::makeClosure;
using index::IDiskIndex;
using fastos::TimeStamp;
using fastos::ClockSystem;

WarmupIndexCollection::WarmupIndexCollection(const WarmupConfig & warmupConfig,
                                             ISearchableIndexCollection::SP prev,
                                             ISearchableIndexCollection::SP next,
                                             IndexSearchable & warmup,
                                             vespalib::ThreadExecutor & executor,
                                             IWarmupDone & warmupDone) :
    _warmupConfig(warmupConfig),
    _prev(prev),
    _next(next),
    _warmup(warmup),
    _executor(executor),
    _warmupDone(warmupDone),
    _warmupEndTime(ClockSystem::now() + TimeStamp::Seconds(warmupConfig.getDuration())),
    _handledTerms()
{
    if (next->valid()) {
        setCurrentIndex(next->getCurrentIndex());
    } else {
        LOG(warning, "Next index is not valid, Dangerous !! : %s", next->toString().c_str());
    }
    LOG(debug, "For %g seconds I will warm up %s.", warmupConfig.getDuration(), typeid(_warmup).name());
    LOG(debug, "%s", toString().c_str());
}

void
WarmupIndexCollection::setSource(uint32_t docId)
{
    assert(_prev->valid());
    assert(_next->valid());
    _prev->setSource(docId);
    _next->setSource(docId);
}

vespalib::string
WarmupIndexCollection::toString() const
{
    vespalib::asciistream os;
    os << "warmup : ";
    if (dynamic_cast<const IDiskIndex *>(&_warmup) != NULL) {
        os << static_cast<const IDiskIndex &>(_warmup).getIndexDir();
    } else {
        os << typeid(_warmup).name();
    }
    os << "\n";
    os << "next   : " << _next->toString() << "\n";
    os << "prev   : " << _prev->toString() << "\n";
    return os.str();
}

WarmupIndexCollection::~WarmupIndexCollection()
{
    if (_warmupEndTime != 0) {
        LOG(info, "Warmup aborted due to new state change or application shutdown");
    }
   _executor.sync();
}

const ISourceSelector &
WarmupIndexCollection::getSourceSelector() const
{
    return _next->getSourceSelector();
}

size_t
WarmupIndexCollection::getSourceCount() const
{
    return _next->getSourceCount();
}

IndexSearchable &
WarmupIndexCollection::getSearchable(uint32_t i) const
{
    return _next->getSearchable(i);
}

uint32_t
WarmupIndexCollection::getSourceId(uint32_t i) const
{
    return _next->getSourceId(i);
}

void
WarmupIndexCollection::fireWarmup(Task::UP task)
{
    fastos::TimeStamp now(fastos::ClockSystem::now());
    if (now < _warmupEndTime) {
        _executor.execute(std::move(task));
    } else {
        vespalib::LockGuard guard(_lock);
        if (_warmupEndTime != 0) {
            _warmupEndTime = 0;
            guard.unlock();
            LOG(info, "Done warming up. Posting WarmupDoneTask");
            _warmupDone.warmupDone(shared_from_this());
        }
    }
}

bool
WarmupIndexCollection::handledBefore(uint32_t fieldId, const Node &term)
{
    const StringBase * sb(dynamic_cast<const StringBase *>(&term));
    if (sb != NULL) {
        const vespalib::string & s = sb->getTerm();
        vespalib::LockGuard guard(_lock);
        TermMap::insert_result found = _handledTerms[fieldId].insert(s);
        return ! found.second;
    }
    return true;
}
Blueprint::UP
WarmupIndexCollection::createBlueprint(const IRequestContext & requestContext,
                                       const FieldSpec &field,
                                       const Node &term,
                                       const IAttributeContext &attrCtx)
{
    if ( _warmupEndTime == 0) {
        // warmup done
        return _next->createBlueprint(requestContext, field, term, attrCtx);
    }
    if ( ! handledBefore(field.getFieldId(), term) ) {
        MatchDataLayout mdl;
        FieldSpec fs(field.getName(), field.getFieldId(), mdl.allocTermField(field.getFieldId()), field.isFilter());
        Task::UP task(new WarmupTask(mdl.createMatchData(), *this));
        static_cast<WarmupTask &>(*task).createBlueprint(fs, term, attrCtx);
        fireWarmup(std::move(task));
    }
    return _prev->createBlueprint(requestContext, field, term, attrCtx);
}

Blueprint::UP
WarmupIndexCollection::createBlueprint(const IRequestContext & requestContext,
                                       const FieldSpecList &fields,
                                       const Node &term,
                                       const IAttributeContext &attrCtx)
{
    if ( _warmupEndTime == 0) {
        // warmup done
        return _next->createBlueprint(requestContext, fields, term, attrCtx);
    }
    MatchDataLayout mdl;
    FieldSpecList fsl;
    bool needWarmUp(false);
    for(size_t i(0); i < fields.size(); i++) {
        const FieldSpec & f(fields[i]);
        FieldSpec fs(f.getName(), f.getFieldId(), mdl.allocTermField(f.getFieldId()), f.isFilter());
        fsl.add(fs);
        needWarmUp = needWarmUp || ! handledBefore(fs.getFieldId(), term);
    }
    if (needWarmUp) {
        Task::UP task(new WarmupTask(mdl.createMatchData(), *this));
        static_cast<WarmupTask &>(*task).createBlueprint(fsl, term, attrCtx);
        fireWarmup(std::move(task));
    }
    return _prev->createBlueprint(requestContext, fields, term, attrCtx);
}

search::SearchableStats
WarmupIndexCollection::getSearchableStats() const
{
    return _prev->getSearchableStats();
}

void
WarmupIndexCollection::append(uint32_t id, const IndexSearchable::SP &source)
{
    _next->append(id, source);
}

void
WarmupIndexCollection::replace(uint32_t id, const IndexSearchable::SP &source)
{
    _next->replace(id, source);
}

IndexSearchable::SP
WarmupIndexCollection::getSearchableSP(uint32_t i) const
{
    return _next->getSearchableSP(i);
}

void
WarmupIndexCollection::WarmupTask::run()
{
    if (_warmup._warmupEndTime != 0) {
        LOG(debug, "Warming up %s", _bluePrint->asString().c_str());
        _bluePrint->fetchPostings(true);
        SearchIterator::UP it(_bluePrint->createSearch(*_matchData, true));
        it->initFullRange();
        for (uint32_t docId = it->seekFirst(1); !it->isAtEnd(); docId = it->seekNext(docId+1)) {
            if (_warmup.doUnpack()) {
                it->unpack(docId);
            }
        }
    } else {
        LOG(debug, "Warmup has finished, ignoring task.");
    }
}

}
