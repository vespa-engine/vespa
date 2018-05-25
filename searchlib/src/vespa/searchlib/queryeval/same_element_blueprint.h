// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class SameElementBlueprint : public ComplexLeafBlueprint
{
private:
    HitEstimate                _estimate;
    fef::MatchDataLayout       _layout;
    std::vector<Blueprint::UP> _terms;

public:
    SameElementBlueprint();
    SameElementBlueprint(const SameElementBlueprint &) = delete;
    SameElementBlueprint &operator=(const SameElementBlueprint &) = delete;
    ~SameElementBlueprint() = default;

    // no match data
    bool isWhiteList() const override { return true; }

    // used by create visitor
    FieldSpec getNextChildField(const vespalib::string &field_name, uint32_t field_id);

    // used by create visitor
    void addTerm(Blueprint::UP term);

    void optimize_self() override;
    void fetchPostings(bool strict) override;

    SearchIteratorUP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                                      bool strict) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const std::vector<Blueprint::UP> &terms() const { return _terms; }
};

}
