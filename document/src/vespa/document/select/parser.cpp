// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "parser.h"
#include "parser_limits.h"
#include "scanner.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

namespace document::select {

namespace {

void verify_expression_not_too_large(const std::string& expr) {
    if (expr.size() > ParserLimits::MaxSelectionByteSize) {
        throw ParsingFailedException(vespalib::make_string(
                "expression is too large to be parsed (max %zu bytes)",
                ParserLimits::MaxSelectionByteSize));
    }
}

}

std::unique_ptr<Node> Parser::parse(const std::string& str) const {
    verify_expression_not_too_large(str);
    try {
        std::istringstream ss(str);
        DocSelScanner scanner(&ss);

        std::unique_ptr<Node> root;
        DocSelParser parser(scanner, _bucket_id_factory, _doc_type_repo, root);
        if (parser.parse() != 0) {
            throw ParsingFailedException(
                    vespalib::make_string("Unknown parse failure while parsing selection '%s'", str.c_str()),
                    VESPA_STRLOC);
        }
        return root;
    } catch (const DocSelParser::syntax_error& err) {
        throw ParsingFailedException(
                vespalib::make_string("%s at column %u when parsing selection '%s'",
                                      err.what(), err.location.begin.column, str.c_str()),
                VESPA_STRLOC);
    }
}

}

