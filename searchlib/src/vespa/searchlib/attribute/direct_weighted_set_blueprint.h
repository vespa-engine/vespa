// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_object_visitor.h"
#include "i_document_weight_attribute.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>

namespace search::queryeval { class SearchIterator; }

namespace search::attribute {

/**
 * Blueprint used for WeightedSetTerm or DotProduct over a multi-value attribute
 * which supports the IDocumentWeightAttribute interface.
 *
 * This allows access to low-level posting lists, which speeds up query execution.
 */
template <typename SearchType>
class DirectWeightedSetBlueprint : public queryeval::ComplexLeafBlueprint
{
private:
    std::vector<int32_t>                                _weights;
    std::vector<IDocumentWeightAttribute::LookupResult> _terms;
    const IAttributeVector                             &_iattr;
    const IDocumentWeightAttribute                     &_attr;
    vespalib::datastore::EntryRef                       _dictionary_snapshot;

public:
    DirectWeightedSetBlueprint(const queryeval::FieldSpec &field, const IAttributeVector &iattr, const IDocumentWeightAttribute &attr, size_t size_hint);
    ~DirectWeightedSetBlueprint() override;

    void addTerm(const IDocumentWeightAttribute::LookupKey & key, int32_t weight, HitEstimate & estimate) {
        IDocumentWeightAttribute::LookupResult result = _attr.lookup(key, _dictionary_snapshot);
        HitEstimate childEst(result.posting_size, (result.posting_size == 0));
        if (!childEst.empty) {
            if (estimate.empty) {
                estimate = childEst;
            } else {
                estimate.estHits += childEst.estHits;
            }
            _weights.push_back(weight);
            _terms.push_back(result);
        }
    }
    void complete(HitEstimate estimate) {
        setEstimate(estimate);
    }

    std::unique_ptr<queryeval::SearchIterator> createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const override;

    std::unique_ptr<queryeval::SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
    std::unique_ptr<queryeval::MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const override {
        if (fields.has_field(_iattr.getName())) {
            return queryeval::MatchingElementsSearch::create(_iattr, _dictionary_snapshot, vespalib::ConstArrayRef<IDocumentWeightAttribute::LookupResult>(_terms));
        } else {
            return {};
        }
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override {
        LeafBlueprint::visitMembers(visitor);
        visit_attribute(visitor, _iattr);
    }
};

}

