// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>
namespace proton::documentmetastore {

/**
 * Interface used to delay reuse of lids until references to the lids have
 * been purged from the data structures in memory index and attribute vectors.
 */
class ILidReuseDelayer
{
public:
    virtual ~ILidReuseDelayer() { }
    /**
     * Delay reuse of a single lid.
     *
     * @param lid          The lid for which to delay reuse
     *
     * @return bool        True if caller must handle lid reuse explicitly
     */
    virtual bool delayReuse(uint32_t lid) = 0;
    /**
     * Delay reuse of multiple lids.
     *
     * @param lids         The lids for which to delay reuse
     *
     * @return bool        True if caller must handle lid reuse explicitly
     */
    virtual bool delayReuse(const std::vector<uint32_t> &lids) = 0;
    virtual void setImmediateCommit(bool immediateCommit) = 0;
    virtual bool getImmediateCommit() const = 0;
    virtual void setHasIndexedOrAttributeFields(bool hasIndexedFields) = 0;
    virtual std::vector<uint32_t> getReuseLids() = 0;
};

}
