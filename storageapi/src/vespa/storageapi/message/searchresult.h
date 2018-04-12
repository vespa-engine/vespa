// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/vdslib/container/searchresult.h>

namespace storage {
namespace api {

/**
 * @class SearchResultCommand
 * @ingroup message
 *
 * @brief The result of a searchvisitor.
 */
class SearchResultCommand : public StorageCommand, public vdslib::SearchResult {
public:
    SearchResultCommand();
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(SearchResultCommand, onSearchResult)
};

/**
 * @class SearchResultReply
 * @ingroup message
 *
 * @brief Response to a search result command.
 */
class SearchResultReply : public StorageReply {
public:
    explicit SearchResultReply(const SearchResultCommand& command);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(SearchResultReply, onSearchResultReply)
};

} // api
} // storage
