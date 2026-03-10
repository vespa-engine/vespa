// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_blueprint.h"
#include "array_bool_search.h"
#include "field_spec.h"
#include "filter_wrapper.h"
#include "flow_tuning.h"
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <sstream>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.array_bool_blueprint");

using namespace search::queryeval::flow;

namespace search::queryeval {

ArrayBoolBlueprint::ArrayBoolBlueprint(FieldSpecBase field, const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter, bool want_true)
    : SimpleLeafBlueprint(field), _attr(attr), _element_filter(element_filter), _want_true(want_true) {
    auto num_docs = _attr.getNumDocs();
    setEstimate(HitEstimate(num_docs, num_docs == 0));
}

search::queryeval::FlowStats ArrayBoolBlueprint::calculate_flow_stats(uint32_t /*docid_limit*/) const {
    // Arrays do not have fast-search, and we are not able to provide a hit estimate.
    // In addition, matching is lookup based, and we are not able to skip documents efficiently when being strict.
    size_t indirections = get_num_indirections(_attr.getBasicType(), _attr.getCollectionType());
    return {estimate_when_unknown(), lookup_cost(indirections), lookup_strict_cost(indirections)};
}

std::unique_ptr<SearchIterator> ArrayBoolBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const {
    assert(tfmda.size() == 1); // always search in only one field
    return ArrayBoolSearch::create(_attr, _element_filter, _want_true, strict(), tfmda[0]);
}

std::unique_ptr<SearchIterator> ArrayBoolBlueprint::createFilterSearchImpl(FilterConstraint constraint) const {
    (void) constraint; // We provide an iterator with exact results, so no need to take constraint into consideration.
    auto wrapper = std::make_unique<FilterWrapper>(getState().numFields());
    wrapper->wrap(createLeafSearch(wrapper->tfmda()));
    return wrapper;
}


void ArrayBoolBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const {
    SimpleLeafBlueprint::visitMembers(visitor);
    std::stringstream ss;
    ss << "[";
    bool first = true;
    for (uint32_t element : _element_filter) {
        if (!first) {
            ss << ",";
        }
        first = false;
        ss << element;
    }
    ss << "]";
    visitor.visitString("element_filter", ss.str());
    visitor.visitBool("want_true", _want_true);
}

}
