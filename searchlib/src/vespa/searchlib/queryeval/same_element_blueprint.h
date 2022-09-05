// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class SameElementSearch;

class SameElementBlueprint : public ComplexLeafBlueprint
{
private:
    HitEstimate                _estimate;
    fef::MatchDataLayout       _layout;
    std::vector<Blueprint::UP> _terms;
    vespalib::string           _field_name;

public:
    SameElementBlueprint(const vespalib::string &field_name_in, bool expensive);
    SameElementBlueprint(const SameElementBlueprint &) = delete;
    SameElementBlueprint &operator=(const SameElementBlueprint &) = delete;
    ~SameElementBlueprint() override;

    // no match data
    bool isWhiteList() const override { return true; }

    // used by create visitor
    FieldSpec getNextChildField(const vespalib::string &field_name, uint32_t field_id);

    // used by create visitor
    void addTerm(Blueprint::UP term);

    void optimize_self() override;
    void fetchPostings(const ExecuteInfo &execInfo) override;

    std::unique_ptr<SameElementSearch> create_same_element_search(bool strict) const;
    SearchIteratorUP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                                      bool strict) const override;
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const std::vector<Blueprint::UP> &terms() const { return _terms; }
    const vespalib::string &field_name() const { return _field_name; }
};

}
