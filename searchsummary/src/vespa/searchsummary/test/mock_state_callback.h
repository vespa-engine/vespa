// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>

namespace search::docsummary::test {

class MockStateCallback : public GetDocsumsStateCallback {
private:
    MatchingElements _matching_elems;

public:
    MockStateCallback()
        : GetDocsumsStateCallback(),
          _matching_elems()
    {
    }
    ~MockStateCallback() override { }
    void fillSummaryFeatures(GetDocsumsState&) override { }
    void fillRankFeatures(GetDocsumsState&) override { }
    std::unique_ptr<MatchingElements> fill_matching_elements(const search::MatchingElementsFields&) override {
        return std::make_unique<MatchingElements>(_matching_elems);
    }

    void add_matching_elements(uint32_t docid, const vespalib::string& field_name,
                               const std::vector<uint32_t>& elements) {
        _matching_elems.add_matching_elements(docid, field_name, elements);
    }
    void clear() {
        _matching_elems = MatchingElements();
    }
};

}
