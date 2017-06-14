// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/iterators.h>
#include <vespa/searchlib/common/location.h>

search::queryeval::SearchIterator *
FastS_AllocLocationIterator(unsigned int numDocs,
                            bool strict,
                            const search::common::Location & location);

