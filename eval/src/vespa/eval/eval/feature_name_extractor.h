// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "function.h"

namespace vespalib::eval {

/**
 * Custom symbol extractor used to extract ranking feature names when
 * parsing ranking expressions.
 **/
struct FeatureNameExtractor : public vespalib::eval::SymbolExtractor {
    void extract_symbol(const char *pos_in, const char *end_in,
                        const char *&pos_out, vespalib::string &symbol_out) const override;
};

}
