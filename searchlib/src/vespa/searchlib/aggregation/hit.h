// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rawrank.h"
#include <vespa/searchlib/common/identifiable.h>
#include <vespa/searchlib/common/hitrank.h>

namespace search::aggregation {

class Hit : public vespalib::Identifiable
{
private:
    RawRank _rank;

public:
    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, Hit);
    DECLARE_NBO_SERIALIZE;
    using UP = std::unique_ptr<Hit>;

    Hit() : _rank() {}
    Hit(RawRank rank) : _rank(rank) {}
    RawRank getRank() const { return _rank; }
    virtual Hit *clone() const = 0;
    int onCmp(const Identifiable &b) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

}
