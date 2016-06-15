// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <cstring>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("");

#include <vespa/searchcore/util/log.h>
#include <vespa/searchcore/fdispatch/search/partitioned_array.h>
#include <vespa/searchcore/fdispatch/search/engine_base.h>
#include <vespa/searchcore/fdispatch/common/stdincl.h>

using namespace FastS_QueryDistribution;

#define PA PartitionedArray

struct PA::PartitionIDLessThan {
    bool operator()(MeasuredVec& partition,
                    FastS_EngineBase* engine) const
    {
        return (*partition.vec.begin())->GetPartID() <
          engine->GetPartID();
    }
};

namespace {
struct EngineOrdering {
    //less than definition
    bool operator()(FastS_EngineBase* l,
                    FastS_EngineBase* r) const {
        return std::strcmp(l->GetName(),
                           r->GetName()) < 0;
    }
};

} //end anonymous namespace

bool PA::EqualPartitionID(PartitionIterator partitionIterator, FastS_EngineBase* engine) {
    FastS_EngineBase* firstEngine = *partitionIterator->vec.begin();
    return firstEngine->GetPartID() == engine->GetPartID();
}

void PA::InsertNewEngine(PartitionIterator partitionIterator,
        FastS_EngineBase* engine) {
    EngineIterator engineIterator = std::lower_bound(
            partitionIterator->vec.begin(),
            partitionIterator->vec.end(),
            engine, EngineOrdering());

    partitionIterator->vec.insert(engineIterator, engine);

}

void PA::InsertNewPartition(PartitionIterator partitionIterator,
        FastS_EngineBase* engine) {
    if (partitionIterator == _partitions.begin()) {
        _minPartitionID = engine->GetPartID();
    }

    MeasuredVec partition;
    partition.vec.push_back(engine);
    _partitions.insert(partitionIterator, partition);
}

void PA::Add(FastS_EngineBase* engine) {

    if (engine->GetPartID() == FastS_NoID32()){
        AddEngineWithInvalidPartitionID(engine);
    } else {
        PartitionIterator partitionIter = std::lower_bound(
                _partitions.begin(),
                _partitions.end(),
                engine,
                PartitionIDLessThan());

        if( partitionIter == _partitions.end() ||
           !EqualPartitionID(partitionIter, engine) )
        {
            InsertNewPartition( partitionIter, engine);
        } else {
            InsertNewEngine( partitionIter, engine );
        }
    }
    ++_numEngines;
}

void PA::AddEngineWithInvalidPartitionID(FastS_EngineBase* engine) {
    _invalidPartitionEngines.push_back(engine);
}

FastS_EngineBase* PA::Extract() {
    if (_partitions.empty() ) {
        if (_invalidPartitionEngines.empty())
            return 0;
        else {
            FastS_EngineBase* res = _invalidPartitionEngines.back();
            _invalidPartitionEngines.pop_back();
            --_numEngines;
            return res;
        }
    }
    else {
        FastS_EngineBase* res = _partitions.back().vec.back();
        _partitions.back().vec.pop_back();
        if( _partitions.back().vec.empty() ) {
            _partitions.pop_back();
        }
        --_numEngines;
        return res;
    }

}

void PA::EnginePartitionIDChanged(FastS_EngineBase* engine, uint32_t oldID) {
    if (oldID == FastS_NoID32()) {
        RemoveFromInvalidList(engine);
    } else {
        RemoveFromPartitionedArray(engine,oldID);
    }
    Add(engine);
}

void PA::RemoveFromInvalidList(FastS_EngineBase* engine) {
    if ( _invalidPartitionEngines.end() !=
        std::remove(_invalidPartitionEngines.begin(), _invalidPartitionEngines.end(),
                    engine) )
    {
        --_numEngines;
        _invalidPartitionEngines.pop_back();
    } else {
        LOG(error, "RemoveFromInvalidList: Engine not found");
    }
}

void PA::RemoveFromPartitionedArray(FastS_EngineBase* engine, uint32_t oldID) {
    size_t index = oldID - _minPartitionID;
    if (_partitions[index].vec.end() !=
        std::remove(_partitions[index].vec.begin(), _partitions[index].vec.end(),
                    engine) )
    {
        --_numEngines;
        _partitions[index].vec.pop_back();
    } else {
        LOG(error, "RemoveFromPartitionedArray: Engine with oldID %d not found", oldID);
    }

    if (_partitions[index].vec.empty()) {
        _partitions.erase(_partitions.begin() + index);
    }
}

uint32_t PA::totalMeasure() const{
    uint32_t result = 0;

    for (const_PartitionIterator pi = _partitions.begin();
         pi != _partitions.end();
         ++pi) {
        result += pi->count;
    }
    return result;
}

uint32_t PA::Partition::ID() const {
    FastS_assert(!Empty());
    return (*this)[0]->GetPartID();
}
