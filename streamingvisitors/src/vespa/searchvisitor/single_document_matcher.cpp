// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_document_matcher.h"

#include "ext_attribute_factory.h"
#include "rankprocessor.h"
#include "search_environment_snapshot.h"
#include "searchvisitor.h"

#include <vespa/searchlib/engine/search_protocol_proto.h>

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fnet/databuffer.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/query/query_normalization.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>

#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.single_document_matcher");

using search::AttributeVector;
using search::SerializedQueryTree;
using search::fef::Properties;
using search::streaming::Query;
using search::streaming::QueryTermDataFactory;

namespace streaming {

namespace {

// Bounds memory when stored queries use many distinct reconfiguring term kinds and indexes.
constexpr size_t max_cached_specs = 256;

class SpecMapNormalization : public search::QueryNormalization {
    const vsm::FieldSearchSpecMap& _specs;

public:
    explicit SpecMapNormalization(const vsm::FieldSearchSpecMap& specs) noexcept : _specs(specs) {}
    bool is_text_matching(std::string_view index) const noexcept override { return _specs.is_text_matching(index); }
    Normalizing normalizing_mode(std::string_view index) const noexcept override {
        return _specs.normalizing_mode(index);
    }
};

// Decodes an int32 map count followed by FS4 property maps, as sent by the container's streaming path.
bool decode_properties(std::span<const char> blob, Properties& rank, Properties& feature) {
    if (blob.empty()) {
        return true;
    }
    if (blob.size() < sizeof(uint32_t)) {
        return false;
    }
    uint32_t        len = blob.size();
    FNET_DataBuffer src(const_cast<char*>(blob.data()), len);
    uint32_t        cnt = src.ReadInt32();
    len -= sizeof(uint32_t);
    for (uint32_t i = 0; i < cnt; ++i) {
        search::fs4transport::FS4Properties prop;
        if (!prop.decode(src, len)) {
            return false;
        }
        Properties* target = (prop.name() == "rank") ? &rank : (prop.name() == "feature") ? &feature : nullptr;
        if (target != nullptr) {
            for (uint32_t j = 0; j < prop.size(); ++j) {
                target->add(prop.key(j), prop.value(j));
            }
        }
    }
    return true;
}

// FieldSearchSpecMap::reconfigFromQuery replaces a field's searcher only for these term kinds, so the reconfigured
// map is determined by the index and kind of such terms, in query order. Empty when no searcher is replaced.
std::string reconfig_signature(const Query& query) {
    search::streaming::ConstQueryTermList leaves;
    query.getLeaves(leaves);
    std::string signature;
    for (const auto* term : leaves) {
        unsigned kind = term->isSubstring() | term->isSuffix() << 1 | term->isExactstring() << 2 |
                        term->isPrefix() << 3 | term->isRegex() << 4 | term->isFuzzy() << 5 |
                        term->is_string_range() << 6 | term->is_sparse_tensor_wand() << 7;
        if (kind != 0) {
            signature.append(term->index()).push_back('\0');
            signature.push_back(static_cast<char>(kind));
        }
    }
    return signature;
}

std::string attribute_name(const vsm::FieldPath& field_path) {
    std::string name(field_path.front().getName());
    for (auto it = field_path.begin() + 1; it != field_path.end(); ++it) {
        name.append(".");
        name.append((*it)->getName());
    }
    return name;
}

} // namespace

SingleDocumentMatcher::SingleDocumentMatcher(std::shared_ptr<const SearchEnvironmentSnapshot>  snapshot,
                                             std::shared_ptr<const document::DocumentTypeRepo> repo,
                                             const document::Document&                         doc)
    : _snapshot(std::move(snapshot)),
      _repo(std::move(repo)),
      _rank_manager(std::make_shared<std::shared_ptr<const RankManager::Snapshot>>(
                        _snapshot->get_rank_manager_snapshot()),
                    _snapshot->get_rank_manager_snapshot().get()),
      _doc_type_mapping(),
      _field_path_map(),
      _document(),
      _attr_man(),
      _search_buffer(std::make_shared<vsm::SearcherBuf>()),
      _specs(),
      _annotated_tokens(),
      _reconfigured_specs() {
    _search_buffer->reserve(0x10000);
    _specs.buildFromConfig(_snapshot->get_vsm_fields_config(),
                           _snapshot->get_rank_manager_snapshot()->get_proto_index_environment());
    if (_specs.documentTypeMap().empty()) {
        throw vespalib::IllegalStateException("No document type in the 'vsmfields' config");
    }
    _doc_type_mapping.init(_specs.documentTypeMap().begin()->first, _specs.nameIdMap().map(), *_repo);
    _doc_type_mapping.prepareBaseDoc(_field_path_map);
    _document = std::make_unique<vsm::StorageDocument>(std::make_unique<document::Document>(doc), _field_path_map,
                                                       _specs.nameIdMap().highestFieldNo());
    setup_attributes();
}

SingleDocumentMatcher::~SingleDocumentMatcher() = default;

void SingleDocumentMatcher::setup_attributes() {
    // shortcut: all fields become ext attributes (filled once, docid 0) and positions are skipped,
    // track accessed attributes like SearchVisitor if setup cost shows up in profiles.
    const auto& paths = *_field_path_map;
    for (size_t field_id = 0; field_id < paths.size(); ++field_id) {
        const vsm::FieldPath& path = paths[field_id];
        if (path.empty() || path.back().getFieldValueToSetPtr() == nullptr) {
            continue;
        }
        bool array = false;
        bool wset = false;
        for (const auto& entry : path) {
            const document::DataType& dt = entry->getDataType();
            array |= dt.isArray() || dt.isMap();
            wset |= dt.isWeightedSet();
        }
        std::string                 name = attribute_name(path);
        const document::FieldValue& fv = path.back().getFieldValueToSet();
        AttributeVector::SP attr = (array || wset) ? createMultiValueAttribute(name, fv, array)
                                                   : createAttribute(name, fv, _specs.get_distance_metric(name));
        if (!attr || !_attr_man.add(attr)) {
            continue;
        }
        AttributeVector::DocId docid(0);
        attr->addDoc(docid);
        const auto& sub_doc = _document->getComplexField(field_id);
        if (sub_doc.getFieldValue() != nullptr) {
            SearchVisitor::AttributeInserter inserter(*attr, docid);
            sub_doc.getFieldValue()->iterateNested(sub_doc.getRange(), inserter);
        }
    }
}

std::optional<SingleDocumentMatcher::Result> SingleDocumentMatcher::evaluate(std::span<const char> query_tree,
                                                                             std::span<const char> query_props,
                                                                             const std::string&    rank_profile) {
    const auto& rank_manager = _rank_manager;
    if (!rank_manager->has_rank_profile(rank_profile)) {
        LOG(debug, "Unknown rank profile '%s'", rank_profile.c_str());
        return std::nullopt;
    }
    auto proto_tree = std::make_unique<SerializedQueryTree::ProtobufQueryTree>();
    if (!proto_tree->ParseFromArray(query_tree.data(), query_tree.size()) || !proto_tree->has_root()) {
        LOG(debug, "Could not parse stored query tree (%zu bytes)", query_tree.size());
        return std::nullopt;
    }
    auto       serialized = SerializedQueryTree::fromProtobuf(std::move(proto_tree));
    Properties rank_props;
    Properties feature_props;
    if (!decode_properties(query_props, rank_props, feature_props)) {
        LOG(debug, "Could not decode stored query properties (%zu bytes)", query_props.size());
        return std::nullopt;
    }
    // shortcut: the query and rank program are rebuilt per stored query, cache prepared queries if profiles show it.
    SpecMapNormalization normalization(_specs);
    // shortcut: no element gap inspector, so phrase/near across array elements uses the default gap.
    QueryTermDataFactory     factory(&normalization, nullptr);
    Query                    query(factory, *serialized);
    vsm::FieldSearchSpecMap        uncached;
    const vsm::FieldSearchSpecMap* specs = &_specs;
    if (std::string signature = reconfig_signature(query); !signature.empty()) {
        auto cached = _reconfigured_specs.find(signature);
        vsm::FieldSearchSpecMap* target = &uncached;
        if (cached != _reconfigured_specs.end()) {
            target = cached->second.get();
        } else if (_reconfigured_specs.size() < max_cached_specs) {
            target = _reconfigured_specs.emplace(signature, std::make_unique<vsm::FieldSearchSpecMap>()).first->second.get();
        }
        if (cached == _reconfigured_specs.end()) {
            target->buildFromConfig(_snapshot->get_vsm_fields_config(), rank_manager->get_proto_index_environment());
            target->reconfigFromQuery(query);
        }
        specs = target;
    }
    vsm::StringFieldIdTMap   fields_in_query = specs->buildFieldsInQuery(query);
    vsm::FieldIdTSearcherMap searchers;
    specs->buildSearcherMap(fields_in_query.map(), searchers);
    for (const auto& searcher : searchers) {
        // The field config and proton's document type repo are fetched separately, so they can disagree for a
        // moment after a schema change. Searchers assume their field is in the document type (else they crash).
        if (searcher->field() >= _field_path_map->size() || (*_field_path_map)[searcher->field()].empty()) {
            vespalib::Issue::report("reverseMatch: field %u is not in the input document's type (schema change in "
                                    "progress?), stored queries searching it cannot be verified", searcher->field());
            return std::nullopt;
        }
    }
    for (auto& searcher : searchers) {
        // Match on the input document's linguistics annotations (when present), like indexed mode.
        if (auto* text = dynamic_cast<vsm::UTF8StrChrFieldSearcher*>(searcher.get())) {
            text->use_annotations(_annotated_tokens);
        }
    }

    // shortcut: like streaming search, the second phase score is used when the profile has one.
    RankProcessor rank_processor(rank_manager, rank_profile, query, "", rank_props, feature_props, &_attr_man);
    searchers.prepare(specs->documentTypeMap(), _search_buffer, query, *_field_path_map,
                      rank_processor.get_query_env());
    for (auto& searcher : searchers) {
        searcher->search(*_document);
    }
    if (!query.evaluate()) {
        return Result{false, 0.0};
    }
    // Rank programs are only built for matches: most candidates passing pruning do not match.
    rank_processor.initForRanking(1, false);
    rank_processor.unpackMatchData(0);
    rank_processor.runRankProgram(0);
    return Result{true, rank_processor.getRankScore()};
}

} // namespace streaming
