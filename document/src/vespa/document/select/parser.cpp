// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "parser.h"
#include "scanner.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

namespace document::select {

std::unique_ptr<Node> Parser::parse(const std::string& str) const {
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

