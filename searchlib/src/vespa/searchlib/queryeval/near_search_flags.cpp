// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "near_search_flags.h"

namespace search::queryeval {

bool NearSearchFlags::_filter_terms = true;

NearSearchFlags::FilterTermsTweak::FilterTermsTweak(bool filter_terms_in) : _old_filter_terms(_filter_terms) {
    _filter_terms = filter_terms_in;
}

NearSearchFlags::FilterTermsTweak::~FilterTermsTweak() {
    _filter_terms = _old_filter_terms;
}

} // namespace search::queryeval
