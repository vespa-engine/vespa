// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_calculator_bundle.h"
#include "utils.h"
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/tensor/distance_calculator.h>

using search::fef::ITermData;
using search::fef::IllegalHandle;
using search::fef::TermFieldHandle;

namespace search::features {

DistanceCalculatorBundle::Element::Element(fef::TermFieldHandle handle_in)
    : handle(handle_in),
      calc()
{
}

DistanceCalculatorBundle::Element::~Element() = default;

DistanceCalculatorBundle::DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                                                   uint32_t field_id)
    : _elems()
{
    _elems.reserve(env.getNumTerms());
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, field_id);
        if (handle != search::fef::IllegalHandle) {
            _elems.emplace_back(handle);
        }
    }
}

DistanceCalculatorBundle::DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                                                   const vespalib::string& label)
    : _elems()
{
    const ITermData *term = util::getTermByLabel(env, label);
    if (term != nullptr) {
        // expect numFields() == 1
        for (uint32_t i = 0; i < term->numFields(); ++i) {
            TermFieldHandle handle = term->field(i).getHandle();
            if (handle != IllegalHandle) {
                _elems.emplace_back(handle);
            }
        }
    }
}

}

