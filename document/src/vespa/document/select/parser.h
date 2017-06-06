// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/sync.h>

namespace document {
class DocumentTypeRepo;

namespace select {

VESPA_DEFINE_EXCEPTION(ParsingFailedException, vespalib::Exception);

class Parser {
public:
    Parser(const DocumentTypeRepo&, const BucketIdFactory& bucketIdFactory);

    /**
     * Returns a newly allocated AST root node representing the selection
     * if parsing is successful. Otherwise, ParsingFailedException will be
     * thrown.
     */
    std::unique_ptr<Node> parse(const vespalib::stringref& s);

private:
    std::unique_ptr<Node> fullParse(const vespalib::stringref& s);
    static vespalib::Lock _G_parseLock;
    const DocumentTypeRepo& _repo;
    const BucketIdFactory& _bucketIdFactory;
};

} // select
} // parser

