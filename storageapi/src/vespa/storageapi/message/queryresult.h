// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vdslib/container/documentsummary.h>
#include <vespa/storageapi/message/visitor.h>

namespace storage {
namespace api {

/**
 * @class QueryResultCommand
 * @ingroup message
 *
 * @brief The result of a searchvisitor.
 */
class QueryResultCommand : public StorageCommand {
public:
    QueryResultCommand();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    uint32_t getMemoryFootprint() const override {
        return getSearchResult().getSerializedSize() + getDocumentSummary().getSerializedSize();
    }
    const vdslib::SearchResult & getSearchResult() const { return _searchResult; }
    vdslib::SearchResult & getSearchResult() { return _searchResult; }
    const vdslib::DocumentSummary & getDocumentSummary() const { return _summary; }
    vdslib::DocumentSummary & getDocumentSummary() { return _summary; }

    DECLARE_STORAGECOMMAND(QueryResultCommand, onQueryResult)
private:
    vdslib::SearchResult    _searchResult;
    vdslib::DocumentSummary _summary;
};

/**
 * @class QueryResultReply
 * @ingroup message
 *
 * @brief Response to a search result command.
 */
class QueryResultReply : public StorageReply {
public:
    explicit QueryResultReply(const QueryResultCommand& command);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(QueryResultReply, onQueryResultReply)
};

} // api
} // storage

