// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "term_vector.h"
#include <vector>

namespace search::query {

class WeightedIntegerTermVector final : public TermVector {
public:
    explicit WeightedIntegerTermVector(uint32_t sz);
    ~WeightedIntegerTermVector();
    void addTerm(std::string_view, Weight) override;
    void addTerm(int64_t term, Weight weight) override;
    StringAndWeight getAsString(uint32_t index) const override;
    IntegerAndWeight getAsInteger(uint32_t index) const override;
    Weight getWeight(uint32_t index) const override;
    uint32_t size() const override;
private:
    std::vector<IntegerAndWeight> _terms;
    mutable char                  _scratchPad[24];
};

}
