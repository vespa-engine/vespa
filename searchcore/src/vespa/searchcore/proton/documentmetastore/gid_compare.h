// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>

namespace proton {
namespace documentmetastore {

/**
 * Interface for comparing global document ids for ordering.
 */
class IGidCompare
{
public:
    typedef std::shared_ptr<IGidCompare> SP;

    virtual ~IGidCompare() {}

    virtual bool operator()(const document::GlobalId &lhs,
                            const document::GlobalId &rhs) const = 0;
};


/**
 * Default ordering using bucket id order.
 */
class DefaultGidCompare : public IGidCompare
{
private:
    document::GlobalId::BucketOrderCmp _comp;

public:
    DefaultGidCompare()
        : IGidCompare(),
          _comp()
    {
    }

    virtual bool operator()(const document::GlobalId &lhs,
                            const document::GlobalId &rhs) const override {
        return _comp(lhs, rhs);
    }
};


} // namespace documentmetastore
} // namespace proton

