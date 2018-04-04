// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node.h"
#include "parsing_failed_exception.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <memory>
#include <string>

namespace document { class DocumentTypeRepo; }

namespace document::select {

/**
 * Document selection parser built around Flex/Bison. O(n) on input size
 * and non-locking.
 *
 * Thread safety: same as a std::vector
 */
class Parser {
    const DocumentTypeRepo&_doc_type_repo;
    const BucketIdFactory& _bucket_id_factory;
public:
    Parser(const DocumentTypeRepo& repo, const BucketIdFactory& bucket_id_factory)
        : _doc_type_repo(repo),
          _bucket_id_factory(bucket_id_factory)
    {}

    /**
     * Returns a newly allocated AST root node representing the selection
     * if parsing is successful. Otherwise, ParsingFailedException will be
     * thrown.
     *
     * Thread safe, assuming referenced DocumentTypeRepo and BucketIdFactory
     * instances are immutable.
     */
    std::unique_ptr<Node> parse(const std::string& str) const;
};

}

