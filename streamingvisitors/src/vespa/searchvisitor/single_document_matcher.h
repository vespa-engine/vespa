// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/vsm/common/document.h>
#include <vespa/vsm/common/documenttypemapping.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/searcher/fieldsearcher.h>
#include <vespa/vsm/searcher/utf8strchrfieldsearcher.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>

#include "rankmanager.h"

#include <map>
#include <memory>
#include <optional>
#include <span>
#include <string>

namespace document {
class Document;
class DocumentTypeRepo;
} // namespace document

namespace streaming {

class SearchEnvironmentSnapshot;

/**
 * Evaluates stored (already container-processed) queries against one in-memory document,
 * using the streaming search matcher and ranker. Used for reverse matching (saved searches).
 *
 * Not thread safe: create one per thread.
 */
class SingleDocumentMatcher {
public:
    struct Result {
        bool   matched;
        double score;
    };

    SingleDocumentMatcher(std::shared_ptr<const SearchEnvironmentSnapshot>  snapshot,
                          std::shared_ptr<const document::DocumentTypeRepo> repo, const document::Document& doc);
    ~SingleDocumentMatcher();

    /**
     * Evaluates one stored query.
     *
     * @param query_tree serialized protobuf QueryTree
     * @param query_props FS4 property maps ("rank" and "feature"), prefixed by an int32 map count
     * @param rank_profile rank profile of the document's schema
     * @return std::nullopt if the stored query cannot be evaluated (bad blob, unknown rank profile)
     */
    std::optional<Result> evaluate(std::span<const char> query_tree, std::span<const char> query_props,
                                   const std::string& rank_profile);

private:
    std::shared_ptr<const SearchEnvironmentSnapshot>  _snapshot;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    // Aliases the shared rank manager snapshot with a control block private to this matcher: RankProcessor copies
    // it per stored query, and copies of the shared one contend on its reference count across match threads.
    std::shared_ptr<const RankManager::Snapshot>      _rank_manager;
    vsm::DocumentTypeMapping                          _doc_type_mapping;
    vsm::SharedFieldPathMap                           _field_path_map;
    std::unique_ptr<vsm::StorageDocument>             _document;
    search::AttributeManager                          _attr_man;
    vsm::SharedSearcherBuf                            _search_buffer;
    vsm::FieldSearchSpecMap                           _specs; // built once: rebuilding per stored query dominates cost
    vsm::AnnotatedTokenCache                          _annotated_tokens; // the input document's text, tokenized once
    // Spec maps with searchers reconfigured for the query's term kinds, by reconfig signature.
    std::map<std::string, std::unique_ptr<vsm::FieldSearchSpecMap>> _reconfigured_specs;

    void setup_attributes();
};

} // namespace streaming
