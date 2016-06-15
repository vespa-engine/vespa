// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


#include <vector>

class FastS_EngineBase;

namespace FastS_QueryDistribution {

//assumes partitions are numbered sequentially
class PartitionedArray {
private:
    typedef std::vector<FastS_EngineBase*> Vec;

    struct MeasuredVec {
        Vec vec;
        //counts number of deterministically distributed queries
        mutable uint32_t count;
        MeasuredVec()
            :count(0)
        {}
    };

    typedef std::vector<MeasuredVec> Vec2d;

    typedef Vec2d::iterator PartitionIterator;
    typedef Vec2d::const_iterator const_PartitionIterator;

    typedef Vec::iterator EngineIterator;
    typedef Vec::const_iterator const_EngineIterator;

    Vec2d _partitions;
    size_t _minPartitionID;

    Vec _invalidPartitionEngines;
    size_t _numEngines;

public:
    class Partition {
        const MeasuredVec& _partition;
    public:
        Partition(const PartitionedArray& pc, size_t index)
            : _partition( pc._partitions[index] )
        {}

        FastS_EngineBase* operator[](size_t index) const {
            return _partition.vec[index];
        }

        size_t Size() const{
            return _partition.vec.size();
        }

        uint32_t QueryCount() const {
            return _partition.count;
        }

        void IncQueryCount() const {
            ++_partition.count;
        }

        bool Empty() const {
            return _partition.vec.empty();
        }

        uint32_t ID() const;

    };

private:
    bool EqualPartitionID(PartitionIterator partitionIterator, FastS_EngineBase* engine);
    void InsertNewEngine(PartitionIterator partitionIterator, FastS_EngineBase* engine);
    void InsertNewPartition(PartitionIterator partitionIterator, FastS_EngineBase* engine);
    void AddEngineWithInvalidPartitionID(FastS_EngineBase* engine);
    void RemoveFromInvalidList(FastS_EngineBase* engine);
    void RemoveFromPartitionedArray(FastS_EngineBase* engine, uint32_t oldID);
    struct PartitionIDLessThan;


public:
    //should only be used after building modus is finished
    inline Partition operator[](size_t partitionIndex) const {
        return Partition(*this, partitionIndex);
    }

    inline Partition operator()(size_t partitionID) const {
        return Partition(*this, partitionID - _minPartitionID);
    }

    FastS_EngineBase* Extract();
    void Add(FastS_EngineBase* engine);
    void EnginePartitionIDChanged(FastS_EngineBase* engine, uint32_t oldID);

    template <class FUN>
    FUN ForEachPartition(FUN fun) const {
        for (size_t i=0; i<_partitions.size(); i++) {
            fun(Partition(*this, i));
        }
        return fun;
    }

    template <class FUN>
    FUN ForEach(FUN fun) const {
        for(const_EngineIterator ei = _invalidPartitionEngines.begin();
            ei != _invalidPartitionEngines.end();
            ++ei)
        {
            fun(*ei);
        }

        for(const_PartitionIterator pi=_partitions.begin();
            pi != _partitions.end();
            ++pi)
        {
            for(const_EngineIterator ei = pi->vec.begin();
                ei != pi->vec.end();
                ++ei)
            {
                fun(*ei);
            }
        }
        return fun;
    }

    uint32_t totalMeasure() const;

    PartitionedArray() :
        _minPartitionID(0)
    {}

    size_t NumPartitions() {
        return _partitions.size();
    }

};

} //namespace FastS_QueryDistribution
