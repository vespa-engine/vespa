// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fs4hit.h"
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/objectoperation.h>

namespace search::aggregation {

class CountFS4Hits : public vespalib::ObjectPredicate,
                     public vespalib::ObjectOperation
{
private:
    uint32_t _hitCnt;

public:
    CountFS4Hits() : _hitCnt(0) {}
    uint32_t getHitCount() const { return _hitCnt; }
    bool check(const vespalib::Identifiable &obj) const override {
        return (obj.getClass().id() == FS4Hit::classId);
    }
    void execute(vespalib::Identifiable &obj) override {
        (void) obj;
        ++_hitCnt;
    }
};

class FS4HitSetDistributionKey : public vespalib::ObjectPredicate,
                                  public vespalib::ObjectOperation
{
private:
    uint32_t _distributionKey;

public:
    FS4HitSetDistributionKey(uint32_t distributionKey) : _distributionKey(distributionKey) {}
    bool check(const vespalib::Identifiable &obj) const override {
        return (obj.getClass().id() == FS4Hit::classId);
    }
    void execute(vespalib::Identifiable &obj) override {
        static_cast<FS4Hit &>(obj).setDistributionKey(_distributionKey);
    }
};

}
