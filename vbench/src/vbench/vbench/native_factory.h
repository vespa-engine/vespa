// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/data/slime/slime.h>
#include "generator.h"
#include "tagger.h"
#include "analyzer.h"

namespace vbench {

struct NativeFactory {
    Generator::UP createGenerator(const vespalib::slime::Inspector &spec,
                                  Handler<Request> &next);
    Tagger::UP createTagger(const vespalib::slime::Inspector &spec,
                            Handler<Request> &next);
    Analyzer::UP createAnalyzer(const vespalib::slime::Inspector &spec,
                                Handler<Request> &next);
};

} // namespace vbench

