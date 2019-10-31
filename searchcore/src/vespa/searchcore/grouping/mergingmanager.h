// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vector>

namespace search::grouping {

/**
 * Wrapper class used to handle merging of grouping results. All input
 * data is assumed to be kept alive by the user.
 **/
class MergingManager
{
public:
    /**
     * Simple wrapper for all the grouping results from a single
     * search node.
     **/
    struct Entry {
        uint32_t    partId;
        uint32_t    rowId;
        const char *data;
        size_t      length;

        Entry(uint32_t part, uint32_t row, const char *pt, size_t len)
            : partId(part), rowId(row), data(pt), length(len) {}
    };

private:
    MergingManager(const MergingManager &);
    MergingManager &operator=(const MergingManager &);
    void fullMerge();
    bool needMerge() const;

    uint32_t           _partBits;
    uint32_t           _rowBits;
    std::vector<Entry> _input;
    char              *_result;
    size_t             _resultLen;

public:
    /**
     * Create a new merging manager.
     *
     * @param partBits how many bits to be used to encode partId into path
     * @param rowBits how many bits to be used to encode rowId into path
     **/
    MergingManager(uint32_t partBits, uint32_t rowBits);

    /**
     * Release resources
     **/
    ~MergingManager();

    /**
     * Register an additional grouping result that should be part of
     * the upcoming merge operation.
     *
     * @param partId which partition these results came from
     * @param rowId which row these results came from
     * @param groupSpec group spec
     * @param groupSpecLen length of the group spec
     **/
    void addResult(uint32_t partId, uint32_t rowId,
                   const char *groupResult, size_t groupResultLen);

    /**
     * Perform actual merging of all the registered grouping results.
     **/
    void merge();

    /**
     * Obtain the size of the grouping result
     *
     * @return grouping result size
     **/
    size_t getGroupResultLen() const;

    /**
     * Obtain the grouping result.
     *
     * @return grouping result
     **/
    const char *getGroupResult() const;

    /**
     * Steal the grouping result. Invoking this method will take
     * overship of the grouping result blob returned by this
     * method. Use 'free' to release the memory when you are done with
     * it.
     *
     * @return grouping result that have just been stolen
     **/
    char *stealGroupResult();
};

}
