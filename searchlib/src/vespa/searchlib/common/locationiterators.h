// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "location.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::common {

std::unique_ptr<search::queryeval::SearchIterator>
create_location_iterator(search::fef::TermFieldMatchData &tfmd,
                         unsigned int numDocs,
                         bool strict,
                         const Location & location);

} // namespace

std::unique_ptr<search::queryeval::SearchIterator>
FastS_AllocLocationIterator(unsigned int numDocs,
                            bool strict,
                            const search::common::Location & location);
