// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchenvironment.h"
#include "search_environment_snapshot.h"
#include "searchvisitor.h"
#include "matching_elements_filler.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/fnet/databuffer.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/i_sort_blob_writer.h>
#include <vespa/searchlib/aggregation/modifiers.h>
#include <vespa/searchlib/attribute/make_sort_blob_writer.h>
#include <vespa/searchlib/attribute/array_bool_ext_attribute.h>
#include <vespa/searchlib/attribute/single_raw_ext_attribute.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/tensor/tensor_ext_attribute.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/size_literals.h>
#include <optional>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.searchvisitor");

using document::DataType;
using document::PositionDataType;
using search::AttributeGuard;
using search::AttributeVector;
using search::SerializedQueryTree;
using search::SerializedQueryTreeSP;
using search::aggregation::HitsAggregationResult;
using search::attribute::IAttributeVector;
using search::attribute::make_sort_blob_writer;
using search::expression::ConfigureStaticParams;
using search::fef::ElementGap;
using search::streaming::Query;
using search::Normalizing;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;
using storage::StorageComponent;
using storage::VisitorEnvironment;
using vespalib::Issue;
using vdslib::Parameters;
using vsm::DocsumFilter;
using vsm::FieldPath;
using vsm::StorageDocument;
using vsm::StringFieldIdTMap;
using std::string;

namespace streaming {

namespace {

std::string_view
extract_search_cluster(const vdslib::Parameters& params)
{
    Parameters::ValueRef searchCluster;
    if (params.lookup("searchcluster", searchCluster)) {
        LOG(spam, "Received searchcluster blob of %zd bytes", searchCluster.size());
    }
    return searchCluster;
}

std::string_view
extract_schema(const vdslib::Parameters& params)
{
    Parameters::ValueRef schema;
    if (params.lookup("schema", schema)) {
        LOG(spam, "Received searchcluster blob of %zd bytes", schema.size());
    }
    return schema;
}

std::shared_ptr<const SearchEnvironmentSnapshot>
get_search_environment_snapshot(VisitorEnvironment& v_env, const Parameters& params)
{
    auto& env = dynamic_cast<SearchEnvironment&>(v_env);
    auto search_cluster = extract_search_cluster(params);
    if ( !search_cluster.empty()) {
        auto schema = extract_schema(params);
        return schema.empty()
            ? env.get_snapshot(std::string(search_cluster))
            : env.get_snapshot(std::string(search_cluster) + "/" + std::string(schema));
    }
    return {};
}

}

class ForceWordfolderInit
{
public:
    ForceWordfolderInit();
};

ForceWordfolderInit::ForceWordfolderInit()
{
    Fast_NormalizeWordFolder::Setup(Fast_NormalizeWordFolder::DO_ACCENT_REMOVAL |
                                    Fast_NormalizeWordFolder::DO_SHARP_S_SUBSTITUTION |
                                    Fast_NormalizeWordFolder::DO_LIGATURE_SUBSTITUTION |
                                    Fast_NormalizeWordFolder::DO_MULTICHAR_EXPANSION);
}

static ForceWordfolderInit G_forceNormWordFolderInit;

// Leftovers from FS4 protocol with limited use here.
enum queryflags {
    QFLAG_DUMP_FEATURES        = 0x00040000
};


AttributeVector::SP
createMultiValueAttribute(const std::string & name, const document::FieldValue & fv, bool arrayType)
{
    const DataType * ndt = fv.getDataType();
    const document::CollectionDataType * cdt = ndt->cast_collection();
    if (cdt != nullptr) {
        ndt = &cdt->getNestedType();
    }
    LOG(debug, "Create %s attribute '%s' with data type '%s' (%s)",
        arrayType ? "array" : "weighted set", name.c_str(), ndt->getName().c_str(), fv.className());
    if (ndt->getId() == DataType::T_BOOL && arrayType) {
        return std::make_shared<search::attribute::ArrayBoolExtAttribute>(name);
    }
    if (ndt->getId() == DataType::T_BYTE ||
        ndt->getId() == DataType::T_INT ||
        ndt->getId() == DataType::T_LONG)
    {
        return arrayType ? std::make_shared<search::MultiIntegerExtAttribute>(name)
                         : std::make_shared<search::WeightedSetIntegerExtAttribute>(name);
    } else if (ndt->getId() == DataType::T_DOUBLE ||
               ndt->getId() == DataType::T_FLOAT)
    {
        return arrayType ? std::make_shared<search::MultiFloatExtAttribute>(name)
                         : std::make_shared<search::WeightedSetFloatExtAttribute>(name);
    } else if (ndt->getId() == DataType::T_STRING) {
        return arrayType ? std::make_shared<search::MultiStringExtAttribute>(name)
                         : std::make_shared<search::WeightedSetStringExtAttribute>(name);
    } else {
        LOG(debug, "Can not make an multivalue attribute out of %s with data type '%s' (%s)",
            name.c_str(), ndt->getName().c_str(), fv.className());
    }
    return {};
}

const document::TensorDataType*
get_tensor_type(const document::FieldValue& fv)
{
    auto tfv = dynamic_cast<const document::TensorFieldValue*>(&fv);
    if (tfv == nullptr) {
        return nullptr;
    }
    return dynamic_cast<const document::TensorDataType*>(tfv->getDataType());
}

AttributeVector::SP
createAttribute(const std::string & name, const document::FieldValue & fv, search::attribute::DistanceMetric dm)
{
    LOG(debug, "Create single value attribute '%s' with value type '%s'", name.c_str(), fv.className());
    if (fv.isA(document::FieldValue::Type::BOOL) || fv.isA(document::FieldValue::Type::BYTE) ||
        fv.isA(document::FieldValue::Type::INT) || fv.isA(document::FieldValue::Type::LONG))
    {
        return std::make_shared<search::SingleIntegerExtAttribute>(name);
    } else if (fv.isA(document::FieldValue::Type::DOUBLE) || fv.isA(document::FieldValue::Type::FLOAT)) {
        return std::make_shared<search::SingleFloatExtAttribute>(name);
    } else if (fv.isA(document::FieldValue::Type::STRING)) {
        return std::make_shared<search::SingleStringExtAttribute>(name);
    } else if (fv.isA(document::FieldValue::Type::RAW)) {
        return std::make_shared<search::attribute::SingleRawExtAttribute>(name);
    } else if (fv.isA(document::FieldValue::Type::TENSOR) && get_tensor_type(fv) != nullptr) {
        search::attribute::Config cfg(search::attribute::BasicType::TENSOR, search::attribute::CollectionType::SINGLE);
        auto tdt = get_tensor_type(fv);
        assert(tdt != nullptr);
        cfg.setTensorType(tdt->getTensorType());
        cfg.set_distance_metric(dm);
        return std::make_shared<search::tensor::TensorExtAttribute>(name, cfg);
    } else {
        LOG(debug, "Can not make an attribute out of %s of type '%s'.", name.c_str(), fv.className());
    }
    return {};
}

SearchVisitor::AttrInfo::AttrInfo(vsm::FieldIdT fid, search::AttributeGuard::UP attr) noexcept
    : _field(fid),
      _attr(std::move(attr)),
      _sort_blob_writer()
{
}

SearchVisitor::StreamingDocsumsState::StreamingDocsumsState(search::docsummary::GetDocsumsStateCallback& callback, ResolveClassInfo& resolve_class_info)
    : _state(callback),
      _resolve_class_info(resolve_class_info)
{
}

SearchVisitor::StreamingDocsumsState::~StreamingDocsumsState() = default;

SearchVisitor::SummaryGenerator::SummaryGenerator(const search::IAttributeManager& attr_manager,
                                                  const search::QueryNormalization & query_normalization)
    : HitsAggregationResult::SummaryGenerator(),
      _callback(),
      _docsum_states(),
      _summaryFields(),
      _docsumFilter(),
      _docsumWriter(nullptr),
      _buf(4_Ki),
      _dump_features(),
      _location(),
      _serialized_query_tree(),
      _highlight_terms(),
      _attr_manager(attr_manager),
      _query_normalization(query_normalization)
{
}

SearchVisitor::SummaryGenerator::~SummaryGenerator() = default;

SearchVisitor::StreamingDocsumsState&
SearchVisitor::SummaryGenerator::get_streaming_docsums_state(std::string_view summary_class) {
    auto itr = _docsum_states.find(summary_class);
    if (itr != _docsum_states.end()) {
        return *itr->second;
    }
    vespalib::hash_set<std::string> fields;
    for (const auto& field: _summaryFields) {
        fields.insert(field);
    }
    auto rci = _docsumWriter->resolveClassInfo(summary_class, fields);
    auto state = std::make_unique<StreamingDocsumsState>(_callback, rci);
    auto &ds = state->get_state();
    ds._omit_summary_features = (rci.res_class == nullptr) || rci.res_class->omit_summary_features();
    ds._args.setResultClassName(summary_class);
    ds._args.set_fields(fields);
    ds.query_normalization(&_query_normalization);
    if (_dump_features.has_value()) {
        ds._args.dumpFeatures(_dump_features.value());
    }
    if (_location.has_value()) {
        ds._args.setLocation(_location.value());
    }
    if (_serialized_query_tree) {
        ds._args.setSerializedQueryTree(*_serialized_query_tree);
    }
    ds._args.highlightTerms(_highlight_terms);
    _docsumWriter->initState(_attr_manager, ds, state->get_resolve_class_info());
    auto insres = _docsum_states.insert(std::make_pair(std::string(summary_class), std::move(state)));
    return *insres.first->second;
}

vespalib::ConstBufferRef
SearchVisitor::SummaryGenerator::fillSummary(AttributeVector::DocId lid, std::string_view summaryClass)
{
    if (_docsumWriter != nullptr) {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        auto& sds = get_streaming_docsums_state(summaryClass);
        _docsumWriter->insertDocsum(sds.get_resolve_class_info(), lid, sds.get_state(), *_docsumFilter, inserter);
        _buf.reset();
        vespalib::WritableMemory magicId = _buf.reserve(4);
        memcpy(magicId.data, &search::docsummary::SLIME_MAGIC_ID, 4);
        _buf.commit(4);
        vespalib::slime::BinaryFormat::encode(slime, _buf);
        vespalib::Memory mem = _buf.obtain();
        return {mem.data, mem.size};
    }
    return {};
}

void
SearchVisitor::HitsResultPreparator::execute(vespalib::Identifiable & obj)
{
    auto & hitsAggr(static_cast<HitsAggregationResult &>(obj));
    hitsAggr.setSummaryGenerator(_summaryGenerator);
    _numHitsAggregators++;
}

bool
SearchVisitor::HitsResultPreparator::check(const vespalib::Identifiable & obj) const
{
    return obj.getClass().inherits(HitsAggregationResult::classId);
}

SearchVisitor::GroupingEntry::GroupingEntry(Grouping * grouping)
    : _grouping(grouping),
      _count(0),
      _limit(grouping->getMaxN(std::numeric_limits<size_t>::max()))
{
}

SearchVisitor::GroupingEntry::~GroupingEntry() = default;

void
SearchVisitor::GroupingEntry::aggregate(const document::Document & doc, search::HitRank rank)
{
    if (_count < _limit) {
        _grouping->aggregate(doc, rank);
        _count++;
    }
}

SearchVisitor::~SearchVisitor() {
    if (!isCompletedCalled() && _queryResult) {
        HitCounter hc;
        completedVisitingInternal(hc);
    }
}

SearchVisitor::SearchVisitor(StorageComponent& component,
                             VisitorEnvironment& vEnv,
                             const Parameters& params)
    : Visitor(component),
      _env(get_search_environment_snapshot(vEnv, params)),
      _params(params),
      _init_called(false),
      _collectGroupingHits(false),
      _docSearchedCount(0),
      _hitCount(0),
      _hitsRejectedCount(0),
      _query(),
      _queryResult(std::make_unique<documentapi::QueryResultMessage>()),
      _fieldSearcherMap(),
      _docTypeMapping(),
      _fieldSearchSpecMap(),
      _snippetModifierManager(),
      _summaryClass("default"),
      _attrMan(),
      _attrCtx(_attrMan.createContext()),
      _summaryGenerator(_attrMan, *this),
      _groupingList(),
      _attributeFields(),
      _sortList(),
      _searchBuffer(std::make_shared<vsm::SearcherBuf>()),
      _tmpSortBuffer(256),
      _documentIdAttributeBacking(std::make_shared<search::SingleStringExtAttribute>("[docid]") ),
      _rankAttributeBacking(std::make_shared<search::SingleFloatExtAttribute>("[rank]") ),
      _documentIdAttribute(dynamic_cast<search::SingleStringExtAttribute &>(*_documentIdAttributeBacking)),
      _rankAttribute(dynamic_cast<search::SingleFloatExtAttribute &>(*_rankAttributeBacking)),
      _shouldFillRankAttribute(false),
      _syntheticFieldsController(),
      _rankController(),
      _unique_issues(),
      _element_gap_inspector()
{
    LOG(debug, "Created SearchVisitor");
}

bool
SearchVisitor::is_text_matching(std::string_view index) const noexcept {
    StringFieldIdTMap fieldIdMap;
    _fieldSearchSpecMap.addFieldsFromIndex(index, fieldIdMap);
    return std::any_of(fieldIdMap.map().begin(), fieldIdMap.map().end(),[&specMap=_fieldSearchSpecMap.specMap()](const auto & fieldId) {
        auto found = specMap.find(fieldId.second);
        return (found != specMap.end() && found->second.uses_string_search_method());
    });
}

namespace {

uint32_t
count_normalize_lowercase(const vsm::FieldSearchSpecMapT & specMap, const StringFieldIdTMap & fieldIdMap) {
    size_t count = 0;
    for (const auto & fieldId : fieldIdMap.map()) {
        auto found = specMap.find(fieldId.second);
        if ((found != specMap.end()) && found->second.searcher().normalize_mode() == Normalizing::LOWERCASE) {
            count++;
        }
    }
    return count;
}

uint32_t
count_normalize_none(const vsm::FieldSearchSpecMapT & specMap, const StringFieldIdTMap & fieldIdMap) {
    size_t count = 0;
    for (const auto & fieldId : fieldIdMap.map()) {
        auto found = specMap.find(fieldId.second);
        if ((found != specMap.end()) && found->second.searcher().normalize_mode() == Normalizing::NONE) {
            count++;
        }
    }
    return count;
}

}

search::Normalizing
SearchVisitor::normalizing_mode(std::string_view index) const noexcept {
    StringFieldIdTMap fieldIdMap;
    _fieldSearchSpecMap.addFieldsFromIndex(index, fieldIdMap);
    if (count_normalize_none(_fieldSearchSpecMap.specMap(), fieldIdMap) == fieldIdMap.map().size()) return Normalizing::NONE;
    if (count_normalize_lowercase(_fieldSearchSpecMap.specMap(), fieldIdMap) == fieldIdMap.map().size()) return Normalizing::LOWERCASE;
    return Normalizing::LOWERCASE_AND_FOLD;
}

void
SearchVisitor::init(const Parameters & params)
{
    VISITOR_TRACE(6, "About to lazily init VSM adapter");
    _attrMan.add(_documentIdAttributeBacking);
    _attrMan.add(_rankAttributeBacking);
    Parameters::ValueRef valueRef;
    if ( params.lookup("summaryclass", valueRef) ) {
        _summaryClass = std::string(valueRef.data(), valueRef.size());
        LOG(debug, "Received summary class: %s", _summaryClass.c_str());
    }
    if ( params.lookup("summary-fields", valueRef) ) {
        vespalib::StringTokenizer fieldTokenizer(valueRef, " ");
        for (const auto & field : fieldTokenizer) {
            _summaryGenerator.add_summary_field(field);
            LOG(debug, "Received field: %s", std::string(field).c_str());
        }
    }

    size_t wantedSummaryCount(10);
    if (params.lookup("summarycount", valueRef) ) {
        std::string tmp(valueRef.data(), valueRef.size());
        wantedSummaryCount = strtoul(tmp.c_str(), nullptr, 0);
        LOG(debug, "Received summary count: %ld", wantedSummaryCount);
    }
    _queryResult->getSearchResult().setWantedHitCount(wantedSummaryCount);

    std::string_view sortRef;
    bool hasSortSpec = params.lookup("sort", sortRef);
    std::string_view groupingRef;
    bool hasGrouping = params.lookup("aggregation", groupingRef);

    if (params.lookup("rankprofile", valueRef) ) {
        if ( ! hasGrouping && (wantedSummaryCount == 0)) {
            // If no hits and no grouping, just use unranked profile
            // TODO, optional could also include check for if grouping needs rank
            valueRef = "unranked";
        }
        std::string tmp(valueRef.data(), valueRef.size());
        _rankController.setRankProfile(tmp);
        LOG(debug, "Received rank profile: %s", _rankController.getRankProfile().c_str());
    }

    int queryFlags = params.get("queryflags", 0);
    if (queryFlags) {
        bool dumpFeatures = (queryFlags & QFLAG_DUMP_FEATURES) != 0;
        _summaryGenerator.set_dump_features(dumpFeatures);
        _rankController.setDumpFeatures(dumpFeatures);
        LOG(debug, "QFLAG_DUMP_FEATURES: %s", _rankController.getDumpFeatures() ? "true" : "false");
    }

    if (params.lookup("rankproperties", valueRef) && ! valueRef.empty()) {
        LOG(spam, "Received rank properties of %zd bytes", valueRef.size());
        uint32_t len = valueRef.size();
        char * data = const_cast<char *>(valueRef.data());
        FNET_DataBuffer src(data, len);
        uint32_t cnt = src.ReadInt32();
        len -= sizeof(uint32_t);
        LOG(debug, "Properties count: '%u'", cnt);
        for (uint32_t i = 0; i < cnt; ++i) {
            search::fs4transport::FS4Properties prop;
            if (!prop.decode(src, len)) {
                Issue::report("Could not decode rank properties");
            } else {
                LOG(debug, "Properties[%u]: name '%s', size '%u'", i, prop.name().c_str(), prop.size());
                if (prop.name() == "rank") { // pick up rank properties
                    for (uint32_t j = 0; j < prop.size(); ++j) {
                        LOG(debug, "Properties[%u][%u]: key '%s' -> value '%s'",
                            i, j, string(prop.key(j)).c_str(), string(prop.value(j)).c_str());
                        _rankController.getQueryProperties().add(prop.key(j), prop.value(j));
                    }
                } else if (prop.name() == "feature") { // pick up feature overrides
                    for (uint32_t j = 0; j < prop.size(); ++j) {
                        LOG(debug, "Feature override[%u][%u]: key '%s' -> value '%s'",
                            i, j, string(prop.key(j)).c_str(), string(prop.value(j)).c_str());
                        _rankController.getFeatureOverrides().add(prop.key(j), prop.value(j));
                    }
                } else if (prop.name() == "highlightterms") {
                    for (uint32_t j = 0; j < prop.size(); ++j) {
                        LOG(debug, "Hightligthterms[%u][%u]: key '%s' -> value '%s'",
                            i, j, string(prop.key(j)).c_str(), string(prop.value(j)).c_str());
                        std::string_view index = prop.key(j);
                        std::string_view term = prop.value(j);
                        std::string norm_term = QueryNormalization::optional_fold(term, search::TermType::WORD, normalizing_mode(index));
                        _summaryGenerator.highlightTerms().add(index, norm_term);
                    }
                }
            }
        }
    } else {
        LOG(debug, "No rank properties received");
    }

    std::string location;
    if (params.lookup("location", valueRef)) {
        location = std::string(valueRef.data(), valueRef.size());
        LOG(debug, "Location = '%s'", location.c_str());
        _summaryGenerator.set_location(location);
    }

    if (_env) {
        _init_called = true;

        if ( hasSortSpec ) {
            search::uca::UcaConverterFactory ucaFactory;
            _sortSpec = search::common::SortSpec(std::string(sortRef.data(), sortRef.size()), ucaFactory);
            LOG(debug, "Received sort specification: '%s'", _sortSpec.getSpec().c_str());
        }

        Parameters::ValueRef queryBlob;
        if ( params.lookup("query", queryBlob) ) {
            LOG(spam, "Received query blob of %zu bytes", queryBlob.size());
            VISITOR_TRACE(9, vespalib::make_string("Setting up for query blob of %zu bytes", queryBlob.size()));
            // Create mapping from field name to field id, from field id to search spec,
            // and from index name to list of field ids
            _fieldSearchSpecMap.buildFromConfig(_env->get_vsm_fields_config(), _env->get_rank_manager_snapshot()->get_proto_index_environment());
            auto additionalFields = registerAdditionalFields(_env->get_docsum_tools()->getFieldSpecs());
            // Add extra elements to mapping from field name to field id
            _fieldSearchSpecMap.buildFromConfig(additionalFields);

            QueryTermDataFactory addOnFactory(this, &_element_gap_inspector);
            // Check if protobuf query tree is available (preferred over stack dump)
            Parameters::ValueRef protoQueryTree;
            SerializedQueryTreeSP serialized_query_tree;
            if (params.lookup("querytree", protoQueryTree)) {
                LOG(debug, "Received protobuf query tree of %zu bytes", protoQueryTree.size());
                auto proto_tree = std::make_unique<SerializedQueryTree::ProtobufQueryTree>();
                if (proto_tree->ParseFromArray(protoQueryTree.data(), protoQueryTree.size())) {
                    serialized_query_tree = SerializedQueryTree::fromProtobuf(std::move(proto_tree));
                } else {
                    Issue::report("Failed to parse protobuf query tree, falling back to stack dump");
                    serialized_query_tree = SerializedQueryTree::fromStackDump(std::vector<char>(queryBlob.begin(), queryBlob.end()));
                }
            } else {
                serialized_query_tree = SerializedQueryTree::fromStackDump(std::vector<char>(queryBlob.begin(), queryBlob.end()));
            }
            _query = Query(addOnFactory, *serialized_query_tree);
            _searchBuffer->reserve(0x10000);

            int stackCount = 0;
            if (params.get("querystackcount", stackCount)) {
                _summaryGenerator.set_serialized_query_tree(serialized_query_tree);
            } else {
                Issue::report("Request without query stack count");
            }

            StringFieldIdTMap fieldsInQuery = setupFieldSearchers();
            setupScratchDocument(fieldsInQuery);
            _syntheticFieldsController.setup(_fieldSearchSpecMap.nameIdMap(), fieldsInQuery);

            setupAttributeVectors();
            setupAttributeVectorsForSorting(_sortSpec);

            _rankController.setRankManagerSnapshot(_env->get_rank_manager_snapshot());
            _rankController.setupRankProcessors(_query, location, wantedSummaryCount, ! _sortList.empty(), _attrMan, _attributeFields);
            _element_gap_inspector.set_query_env(_rankController.getRankProcessor()->get_query_env());

            // This depends on _fieldPathMap (from setupScratchDocument),
            // and IQueryEnvironment (from setupRankProcessors).
            setupSnippetModifiers();

            // Depends on hitCollector setup and _snippetModifierManager
            setupDocsumObjects();

            // This depends on _fieldPathMap (from setupScratchDocument),
            // and IQueryEnvironment (from setupRankProcessors).
            prepare_field_searchers();
        } else {
            Issue::report("No query received");
        }

        if (hasGrouping) {
            std::vector<char> newAggrBlob;
            newAggrBlob.resize(groupingRef.size());
            memcpy(&newAggrBlob[0], groupingRef.data(), newAggrBlob.size());
            LOG(debug, "Received new aggregation blob of %zd bytes", newAggrBlob.size());
            setupGrouping(newAggrBlob);
        }

    } else {
        Issue::report("No searchcluster specified");
    }

    if ( params.lookup("unique", valueRef) ) {
        LOG(spam, "Received unique specification of %zd bytes", valueRef.size());
    } else {
        LOG(debug, "No unique specification received");
    }
    VISITOR_TRACE(6, "Completed lazy VSM adapter initialization");
}

SearchVisitorFactory::SearchVisitorFactory(const config::ConfigUri & configUri, FNET_Transport* transport, const std::string& file_distributor_connection_spec)
    : VisitorFactory(),
      _configUri(configUri),
      _env(std::make_shared<SearchEnvironment>(_configUri, transport, file_distributor_connection_spec))
{ }

SearchVisitorFactory::~SearchVisitorFactory() = default;

std::shared_ptr<VisitorEnvironment>
SearchVisitorFactory::makeVisitorEnvironment(StorageComponent&)
{
    return _env;
}

storage::Visitor*
SearchVisitorFactory::makeVisitor(StorageComponent& component,
                                  storage::VisitorEnvironment& env,
                                  const vdslib::Parameters& params)
{
    return new SearchVisitor(component, env, params);
}

std::optional<int64_t>
SearchVisitorFactory::get_oldest_config_generation() const
{
    auto& env = dynamic_cast<SearchEnvironment&>(*_env);
    return env.get_oldest_config_generation();
}

void
SearchVisitor::AttributeInserter::onPrimitive(uint32_t, const Content & c)
{
    const document::FieldValue & value = c.getValue();
    LOG(debug, "AttributeInserter: Adding value '%s'(%d) to attribute '%s' for docid '%d'",
        value.toString().c_str(), c.getWeight(), _attribute.getName().c_str(), _docId);
    search::IExtendAttribute & attr = *_attribute.getExtendInterface();
    if (_attribute.isIntegerType()) {
        attr.add(value.getAsLong(), c.getWeight());
    } else if (_attribute.isFloatingPointType()) {
        attr.add(value.getAsDouble(), c.getWeight());
    } else if (_attribute.isStringType()) {
        attr.add(value.getAsString().c_str(), c.getWeight());
    } else if (_attribute.is_raw_type()) {
        auto raw_value = value.getAsRaw();
        attr.add(std::span<const char>(raw_value.first, raw_value.second), c.getWeight());
    } else if (_attribute.isTensorType()) {
        auto tfvalue = dynamic_cast<const document::TensorFieldValue*>(&value);
        if (tfvalue != nullptr) {
            const auto* tensor = tfvalue->getAsTensorPtr();
            if (tensor != nullptr) {
                attr.add(*tensor, c.getWeight());
            }
        }
    } else {
        assert(false && "We got an attribute vector that is of an unknown type");
    }
}

SearchVisitor::AttributeInserter::AttributeInserter(AttributeVector & attribute, AttributeVector::DocId docId)
    : _attribute(attribute),
      _docId(docId)
{ }

SearchVisitor::PositionInserter::PositionInserter(AttributeVector & attribute, AttributeVector::DocId docId)
    : AttributeInserter(attribute, docId),
      _fieldX(PositionDataType::getInstance().getField(PositionDataType::FIELD_X)),
      _fieldY(PositionDataType::getInstance().getField(PositionDataType::FIELD_Y))
{ }

SearchVisitor::PositionInserter::~PositionInserter() = default;

void
SearchVisitor::PositionInserter::onPrimitive(uint32_t, const Content &) { }

void
SearchVisitor::PositionInserter::onStructStart(const Content & c)
{
    const auto & value = static_cast<const document::StructuredFieldValue &>(c.getValue());
    LOG(debug, "PositionInserter: Adding value '%s'(%d) to attribute '%s' for docid '%d'",
        value.toString().c_str(), c.getWeight(), _attribute.getName().c_str(), _docId);

    value.getValue(_fieldX, _valueX);
    value.getValue(_fieldY, _valueY);
    int64_t zcurve = vespalib::geo::ZCurve::encode(_valueX.getValue(), _valueY.getValue());
    LOG(debug, "X=%d, Y=%d, zcurve=%" PRId64, _valueX.getValue(), _valueY.getValue(), zcurve);
    search::IExtendAttribute & attr = *_attribute.getExtendInterface();
    attr.add(zcurve, c.getWeight());
}

void
SearchVisitor::RankController::processAccessedAttributes(const QueryEnvironment &queryEnv, bool rank,
                                                         const search::IAttributeManager & attrMan,
                                                         std::vector<AttrInfo> & attributeFields)
{
    auto attributes = queryEnv.get_accessed_attributes();
    auto& indexEnv = queryEnv.getIndexEnvironment();
    for (const std::string & name : attributes) {
        LOG(debug, "Process attribute access hint (%s): '%s'", rank ? "rank" : "dump", name.c_str());
        const search::fef::FieldInfo * fieldInfo = indexEnv.getFieldByName(name);
        if (fieldInfo != nullptr) {
            bool found = false;
            uint32_t fid = fieldInfo->id();
            for (size_t j = 0; !found && (j < attributeFields.size()); ++j) {
                found = (attributeFields[j]._field == fid);
            }
            if (!found) {
                AttributeGuard::UP attr(attrMan.getAttribute(name));
                if (attr->valid()) {
                    LOG(debug, "Add attribute '%s' with field id '%u' to the list of needed attributes", name.c_str(), fid);
                    attributeFields.emplace_back(fid, std::move(attr));
                } else {
                    Issue::report("Cannot locate attribute '%s' in the attribute manager. "
                                  "Ignore access hint about this attribute", name.c_str());
                }
            }
        } else {
            Issue::report("Cannot locate field '%s' in the index environment. Ignore access hint about this attribute",
                          name.c_str());
        }
    }
}

SearchVisitor::RankController::RankController()
    : _rankProfile("default"),
      _rankManagerSnapshot(nullptr),
      _rank_score_drop_limit(),
      _hasRanking(false),
      _hasSummaryFeatures(false),
      _dumpFeatures(false),
      _queryProperties(),
      _featureOverrides(),
      _rankProcessor(),
      _dumpProcessor()
{ }

SearchVisitor::RankController::~RankController() = default;

void
SearchVisitor::RankController::setupRankProcessors(Query & query,
                                                   const std::string & location,
                                                   size_t wantedHitCount, bool use_sort_blob,
                                                   const search::IAttributeManager & attrMan,
                                                   std::vector<AttrInfo> & attributeFields)
{
    using FirstPhaseRankScoreDropLimit = search::fef::indexproperties::hitcollector::FirstPhaseRankScoreDropLimit;
    const search::fef::RankSetup & rankSetup = _rankManagerSnapshot->getRankSetup(_rankProfile);
    _rank_score_drop_limit = FirstPhaseRankScoreDropLimit::lookup(_queryProperties, rankSetup.get_first_phase_rank_score_drop_limit());
    _rankProcessor = std::make_unique<RankProcessor>(_rankManagerSnapshot, _rankProfile, query, location, _queryProperties, _featureOverrides, &attrMan);
    _rankProcessor->initForRanking(wantedHitCount, use_sort_blob);
    // register attribute vectors needed for ranking
    processAccessedAttributes(_rankProcessor->get_real_query_env(), true, attrMan, attributeFields);

    if (_dumpFeatures) {
        _dumpProcessor = std::make_unique<RankProcessor>(_rankManagerSnapshot, _rankProfile, query, location, _queryProperties, _featureOverrides, &attrMan);
        LOG(debug, "Initialize dump processor");
        _dumpProcessor->initForDumping(wantedHitCount, use_sort_blob);
        // register attribute vectors needed for dumping
        processAccessedAttributes(_dumpProcessor->get_real_query_env(), false, attrMan, attributeFields);
    }

    _hasRanking = true;
    _hasSummaryFeatures = ! rankSetup.getSummaryFeatures().empty();
}


void
SearchVisitor::RankController::onDocumentMatch(uint32_t docId)
{
    // unpacking into match data
    _rankProcessor->unpackMatchData(docId);
    if (_dumpFeatures) {
        _dumpProcessor->unpackMatchData(docId);
    }
}

void
SearchVisitor::RankController::rankMatchedDocument(uint32_t docId)
{
    _rankProcessor->runRankProgram(docId);
    LOG(debug, "Rank score for matched document %u: %f",
        docId, _rankProcessor->getRankScore());
    if (_dumpFeatures) {
        _dumpProcessor->runRankProgram(docId);
        // we must transfer the score to this match data to make sure that the same hits
        // are kept on the hit collector used in the dump processor as the one used in the rank processor
        _dumpProcessor->setRankScore(_rankProcessor->getRankScore());
    }
}

bool
SearchVisitor::RankController::keepMatchedDocument()
{
    if (!_rank_score_drop_limit.has_value()) {
        return true;
    }
    // also make sure that NaN scores are added
    return (!(_rankProcessor->getRankScore() <= _rank_score_drop_limit.value()));
}

void
SearchVisitor::RankController::collectMatchedDocument(bool hasSorting,
                                                      SearchVisitor & visitor,
                                                      const std::vector<char> & tmpSortBuffer,
                                                      StorageDocument::SP document)
{
    uint32_t docId = _rankProcessor->getDocId();
    if (!hasSorting) {
        bool amongTheBest = _rankProcessor->getHitCollector().addHit(std::move(document), docId,
                                                                     _rankProcessor->getMatchData(),
                                                                     _rankProcessor->getRankScore());
        if (amongTheBest && _dumpFeatures) {
            _dumpProcessor->getHitCollector().addHit({}, docId, _dumpProcessor->getMatchData(), _dumpProcessor->getRankScore());
        }
    } else {
        size_t pos = visitor.fillSortBuffer();
        LOG(spam, "SortBlob is %ld bytes", pos);
        bool amongTheBest = _rankProcessor->getHitCollector().addHit(std::move(document), docId,
                                                                     _rankProcessor->getMatchData(),
                                                                     _rankProcessor->getRankScore(),
                                                                     &tmpSortBuffer[0], pos);
        if (amongTheBest && _dumpFeatures) {
            _dumpProcessor->getHitCollector().addHit({}, docId, _dumpProcessor->getMatchData(),
                                                     _dumpProcessor->getRankScore(), &tmpSortBuffer[0], pos);
        }
    }
}

vespalib::FeatureSet::SP
SearchVisitor::RankController::getFeatureSet(search::DocumentIdT docId) {
    if (_hasRanking && _hasSummaryFeatures) {
        return _rankProcessor->calculateFeatureSet(docId);
    }
    return {};
}

void
SearchVisitor::RankController::onCompletedVisiting(vsm::GetDocsumsStateCallback & docsumsStateCallback, vdslib::SearchResult & searchResult)
{
    if (_hasRanking) {
        // fill the search result with the hits from the hit collector
        _rankProcessor->fillSearchResult(searchResult);

        // calculate summary features and set them on the callback object
        if (_hasSummaryFeatures) {
            LOG(debug, "Calculate summary features");
            docsumsStateCallback.setSummaryFeatures(_rankProcessor->calculateFeatureSet());
        }

        // calculate rank features and set them on the callback object
        if (_dumpFeatures) {
            LOG(debug, "Calculate rank features");
            docsumsStateCallback.setRankFeatures(_dumpProcessor->calculateFeatureSet());
        }
    }
}

SearchVisitor::SyntheticFieldsController::SyntheticFieldsController()
    : _documentIdFId(StringFieldIdTMap::npos)
{ }

void
SearchVisitor::SyntheticFieldsController::setup(const StringFieldIdTMap & fieldRegistry,
                                                const StringFieldIdTMap & /*fieldsInQuery*/)
{
    _documentIdFId = fieldRegistry.fieldNo("documentid");
    assert(_documentIdFId != StringFieldIdTMap::npos);
}

void
SearchVisitor::SyntheticFieldsController::onDocument(StorageDocument &)
{
}

void
SearchVisitor::SyntheticFieldsController::onDocumentMatch(StorageDocument & document,
                                                          const std::string & documentId)
{
    document.setField(_documentIdFId, std::make_unique<document::StringFieldValue>(documentId));
}

std::vector<std::string>
SearchVisitor::registerAdditionalFields(const std::vector<vsm::DocsumTools::FieldSpec> & docsumSpec)
{
    std::vector<std::string> fieldList;
    for (const vsm::DocsumTools::FieldSpec & spec : docsumSpec) {
        fieldList.push_back(spec.getOutputName());
        const std::vector<std::string> & inputNames = spec.getInputNames();
        for (const auto & name : inputNames) {
            fieldList.push_back(name);
            if (PositionDataType::isZCurveFieldName(name)) {
                fieldList.emplace_back(PositionDataType::cutZCurveFieldName(name));
            }
        }
    }
    // fields used during sorting
    fieldList.emplace_back("[docid]");
    fieldList.emplace_back("[rank]");
    fieldList.emplace_back("documentid");
    return fieldList;
}

StringFieldIdTMap
SearchVisitor::setupFieldSearchers()
{
    // Reconfig field searchers based on the query
    _fieldSearchSpecMap.reconfigFromQuery(_query);

    // Map field name to field id for all fields in the query
    StringFieldIdTMap fieldsInQuery = _fieldSearchSpecMap.buildFieldsInQuery(_query);
    // Connect field names in the query to field searchers
    _fieldSearchSpecMap.buildSearcherMap(fieldsInQuery.map(), _fieldSearcherMap);
    return fieldsInQuery;
}

void
SearchVisitor::prepare_field_searchers()
{
    // prepare the field searchers
    _fieldSearcherMap.prepare(_fieldSearchSpecMap.documentTypeMap(), _searchBuffer, _query,
                              *_fieldPathMap, _rankController.getRankProcessor()->get_query_env());
}

void
SearchVisitor::setupSnippetModifiers()
{
    QueryTermList qtl;
    _query.getLeaves(qtl);
    _snippetModifierManager.setup(qtl, _fieldSearchSpecMap.specMap(), _fieldSearchSpecMap.documentTypeMap().begin()->second,
                                  *_fieldPathMap, _rankController.getRankProcessor()->get_query_env());
}

void
SearchVisitor::setupScratchDocument(const StringFieldIdTMap & fieldsInQuery)
{
    if (_fieldSearchSpecMap.documentTypeMap().empty()) {
        throw vespalib::IllegalStateException("Illegal config: There must be at least 1 document type in the 'vsmfields' config");
    }
    // Setup document type mapping
    if (_fieldSearchSpecMap.documentTypeMap().size() != 1) {
        Issue::report("We have %zd document types in the vsmfields config when we expected 1. Using the first one",
                      _fieldSearchSpecMap.documentTypeMap().size());
    }
    _fieldsUnion = fieldsInQuery.map();
    for(const auto & entry : _fieldSearchSpecMap.nameIdMap().map()) {
        if (_fieldsUnion.find(entry.first) == _fieldsUnion.end()) {
            LOG(debug, "Adding field '%s' from _fieldSearchSpecMap", entry.first.c_str());
            _fieldsUnion[entry.first] = entry.second;
        }
    }
    // Init based on default document type and mapping from field name to field id
    _docTypeMapping.init(_fieldSearchSpecMap.documentTypeMap().begin()->first,
                         _fieldsUnion, *_component.getTypeRepo()->documentTypeRepo);
    _docTypeMapping.prepareBaseDoc(_fieldPathMap);
}

void
SearchVisitor::setupDocsumObjects()
{
    auto docsumFilter = std::make_unique<DocsumFilter>(_env->get_docsum_tools(),
                                                       _rankController.getRankProcessor()->getHitCollector());
    docsumFilter->init(_fieldSearchSpecMap.nameIdMap(), *_fieldPathMap);
    docsumFilter->setSnippetModifiers(_snippetModifierManager.getModifiers());
    _summaryGenerator.setFilter(std::move(docsumFilter));
    auto& docsum_tools = _env->get_docsum_tools();
    if (docsum_tools) {
        _summaryGenerator.setDocsumWriter(*docsum_tools->getDocsumWriter());
    } else {
        Issue::report("No docsum tools available");
    }
}

void
SearchVisitor::setupAttributeVectors()
{
    for (const FieldPath & fieldPath : *_fieldPathMap) {
        if ( ! fieldPath.empty() && fieldPath.back().getFieldValueToSetPtr() != nullptr) {
            setupAttributeVector(fieldPath);
        }
    }
}

void SearchVisitor::setupAttributeVector(const FieldPath &fieldPath) {
    std::string attrName(fieldPath.front().getName());
    for (auto ft(fieldPath.begin() + 1), fmt(fieldPath.end()); ft != fmt; ft++) {
        attrName.append(".");
        attrName.append((*ft)->getName());
    }

    enum FieldDataType { OTHER = 0, ARRAY, WSET };
    FieldDataType typeSeen = OTHER;
    for (const auto & entry : fieldPath) {
        const document::DataType & dt = entry->getDataType();
        if (dt.isArray()) {
            typeSeen = ARRAY;
        } else if (dt.isMap()) {
            typeSeen = ARRAY;
        } else if (dt.isWeightedSet()) {
            typeSeen = WSET;
        }
    }
    const document::FieldValue & fv = fieldPath.back().getFieldValueToSet();
    AttributeVector::SP attr;
    if (typeSeen == ARRAY) {
        attr = createMultiValueAttribute(attrName, fv, true);
    } else if (typeSeen == WSET) {
        attr = createMultiValueAttribute (attrName, fv, false);
    } else {
        attr = createAttribute(attrName, fv, _fieldSearchSpecMap.get_distance_metric(attrName));
    }

    if (attr) {
        LOG(debug, "Adding attribute '%s' for field '%s' with data type '%s' (%s)",
            attr->getName().c_str(), attrName.c_str(), fv.getDataType()->getName().c_str(), fv.className());
        if ( ! _attrMan.add(attr) ) {
            Issue::report("Failed adding attribute '%s' for field '%s' with data type '%s' (%s)",
                          attr->getName().c_str(), attrName.c_str(), fv.getDataType()->getName().c_str(), fv.className());
        }
    } else {
        LOG(debug, "Cannot setup attribute for field '%s' with data type '%s' (%s). Aggregation and sorting will not work for this field",
            attrName.c_str(), fv.getDataType()->getName().c_str(), fv.className());
    }
}

namespace {
bool notContained(const std::vector<size_t> &sortList, size_t idx) {
    for (size_t v : sortList) {
        if (v == idx) return false;
    }
    return true;
}
}

void
SearchVisitor::setupAttributeVectorsForSorting(const search::common::SortSpec & sortList)
{
    if ( ! sortList.empty() ) {
        for (const search::common::FieldSortSpec & field_sort_spec : sortList) {
            vsm::FieldIdT fid = _fieldSearchSpecMap.nameIdMap().fieldNo(field_sort_spec._field);
            if ( fid != StringFieldIdTMap::npos ) {
                AttributeGuard::UP attr(_attrMan.getAttribute(field_sort_spec._field));
                if (attr->valid()) {
                    if (attr->get()->is_sortable()) {
                        size_t index(_attributeFields.size());
                        auto sort_blob_writer = make_sort_blob_writer(attr->get(), field_sort_spec);
                        if (!sort_blob_writer) {
                            continue;
                        }
                        for (size_t j(0); j < index; j++) {
                            if ((_attributeFields[j]._field == fid) && notContained(_sortList, j)) {
                                index = j;
                            }
                        }
                        if (index == _attributeFields.size()) {
                            _attributeFields.emplace_back(fid, std::move(attr));
                        }
                        _attributeFields[index]._sort_blob_writer = std::move(sort_blob_writer);
                        _sortList.push_back(index);
                    } else {
                        Issue::report("Attribute '%s' is not sortable", field_sort_spec._field.c_str());
                    }
                } else {
                    Issue::report("Attribute '%s' is not valid", field_sort_spec._field.c_str());
                }
            } else {
                Issue::report("Cannot locate field '%s' in field name registry", field_sort_spec._field.c_str());
            }
        }
    } else {
        LOG(debug, "No sort specification received");
    }
}

void
SearchVisitor::setupGrouping(const std::vector<char> & groupingBlob)
{
    vespalib::nbostream iss(&groupingBlob[0], groupingBlob.size());
    vespalib::NBOSerializer is(iss);
    uint32_t numGroupings(0);
    is >> numGroupings;
    for (size_t i(0); i < numGroupings; i++) {
        auto ag = std::make_unique<Grouping>();
        ag->deserialize(is);
        GroupingList::value_type groupingPtr(ag.release());
        Grouping & grouping = *groupingPtr;
        Attribute2DocumentAccessor attr2Doc;
        grouping.select(attr2Doc, attr2Doc);
        LOG(debug, "Grouping # %ld with id(%d)", i, grouping.getId());
        try {
            ConfigureStaticParams stuff(_attrCtx.get(), &_docTypeMapping.getCurrentDocumentType(), true);
            grouping.configureStaticStuff(stuff);
            HitsResultPreparator preparator(_summaryGenerator);
            grouping.select(preparator, preparator);
            if (preparator.getNumHitsAggregators() > 0) {
                _collectGroupingHits = true;
            }
            grouping.preAggregate(false);
            if (!grouping.getAll() || (preparator.getNumHitsAggregators() == 0)) {
                _groupingList.push_back(groupingPtr);
            } else {
                Issue::report("You can not collect hits with an all aggregator yet.");
            }
        } catch (const document::FieldNotFoundException & e) {
            Issue::report("Could not locate field for grouping number %ld : %s", i, e.getMessage().c_str());
        } catch (const std::exception & e) {
            Issue::report("Unknown issue for grouping number %ld : %s", i, e.what());
        }
    }
}

class SingleDocumentStore : public vsm::IDocSumCache
{
public:
    explicit SingleDocumentStore(const StorageDocument & doc) noexcept : _doc(doc) { }
    const vsm::Document & getDocSum(const search::DocumentIdT &) const override {
        return _doc;
    }
private:
    const StorageDocument & _doc;
};

bool
SearchVisitor::compatibleDocumentTypes(const document::DocumentType& typeA,
                                       const document::DocumentType& typeB)
{
    return (&typeA == &typeB) || (typeA.getName() == typeB.getName());
}

void
SearchVisitor::handleDocuments(const document::BucketId&, DocEntryList & entries, HitCounter& )
{
    auto capture_issues = Issue::listen(_unique_issues);
    if (!_init_called) {
        init(_params);
    }
    if ( ! _rankController.valid() ) {
        //Prevent continuing with bad config.
        return;
    }
    document::DocumentId emptyId;
    LOG(debug, "SearchVisitor '%s' handling block of %zu documents", _id.c_str(), entries.size());
    size_t highestFieldNo(_fieldSearchSpecMap.nameIdMap().highestFieldNo());

    const document::DocumentType* defaultDocType = _docTypeMapping.getDefaultDocumentType();
    assert(defaultDocType);
    for (const auto & entry : entries) {
        auto document = std::make_shared<StorageDocument>(entry->releaseDocument(), _fieldPathMap, highestFieldNo);

        try {
            if (defaultDocType != nullptr
                && !compatibleDocumentTypes(*defaultDocType, document->docDoc().getType()))
            {
                LOG(debug, "Skipping document of type '%s' when handling only documents of type '%s'",
                    document->docDoc().getType().getName().c_str(), defaultDocType->getName().c_str());
            } else {
                handleDocument(document);
            }
        } catch (const std::exception & e) {
            Issue::report("Caught exception handling document '%s'. Exception='%s'",
                          document->docDoc().getId().getScheme().toString().c_str(), e.what());
        }
    }
}

void
SearchVisitor::handleDocument(StorageDocument::SP documentSP)
{
    StorageDocument & document = *documentSP;
    _syntheticFieldsController.onDocument(document);
    group(document.docDoc(), 0, true);
    if (match(document)) {
        RankProcessor & rp = *_rankController.getRankProcessor();
        std::string documentId(document.docDoc().getId().getScheme().toString());
        LOG(debug, "Matched document with id '%s'", documentId.c_str());
        document.setDocId(rp.getDocId());
        fillAttributeVectors(documentId, document);
        _rankController.rankMatchedDocument(rp.getDocId());
        if (_shouldFillRankAttribute) {
            _rankAttribute.add(rp.getRankScore());
        }
        if (_rankController.keepMatchedDocument()) {
            _rankController.collectMatchedDocument(!_sortList.empty(), *this, _tmpSortBuffer, std::move(documentSP));
            _syntheticFieldsController.onDocumentMatch(document, documentId);
            SingleDocumentStore single(document);
            _summaryGenerator.setDocsumCache(single);
            if (_collectGroupingHits) {
                _summaryGenerator.getDocsumCallback().setSummaryFeatures(_rankController.getFeatureSet(document.getDocId()));
            }
            group(document.docDoc(), rp.getRankScore(), false);
        } else {
            _hitsRejectedCount++;
            LOG(debug, "Do not keep document with id '%s' because rank score (%f) <= rank score drop limit (%f)",
                documentId.c_str(), rp.getRankScore(), _rankController.rank_score_drop_limit().value());
        }
    } else {
        LOG(debug, "Did not match document with id '%s'", document.docDoc().getId().getScheme().toString().c_str());
    }
}

void
SearchVisitor::group(const document::Document & doc, search::HitRank rank, bool all)
{
    LOG(spam, "Group all: %s",  all ? "true" : "false");
    for(GroupingEntry & grouping : _groupingList) {
        if (all == grouping->getAll()) {
            grouping.aggregate(doc, rank);
            LOG(spam, "Actually group document with id '%s'", doc.getId().getScheme().toString().c_str());
        }
    }
}

bool
SearchVisitor::match(const StorageDocument & doc)
{
    for (vsm::FieldSearcherContainer & fSearch : _fieldSearcherMap) {
        fSearch->search(doc);
    }
    bool hit(_query.evaluate());
    if (hit) {
        _hitCount++;
        LOG(spam, "Match in doc %d", doc.getDocId());

        _rankController.onDocumentMatch(_hitCount - 1); // send in the local docId to use for this hit
    }
    _docSearchedCount++;
    _query.reset();
    return hit;
}

void
SearchVisitor::fillAttributeVectors(const std::string & documentId, const StorageDocument & document)
{
    for (const AttrInfo & finfo : _attributeFields) {
        const AttributeGuard &finfoGuard(*finfo._attr);
        bool isPosition = finfoGuard->isIntegerType() && PositionDataType::isZCurveFieldName(finfoGuard->getName());
        LOG(debug, "Filling attribute '%s',  isPosition='%s'", finfoGuard->getName().c_str(), isPosition ? "true" : "false");
        uint32_t fieldId = finfo._field;
        if (isPosition) {
            std::string_view org = PositionDataType::cutZCurveFieldName(finfoGuard->getName());
            fieldId = _fieldsUnion.find(org)->second;
        }
        const StorageDocument::SubDocument & subDoc = document.getComplexField(fieldId);
        auto & attrV = const_cast<AttributeVector & >(*finfoGuard);
        AttributeVector::DocId docId(0);
        attrV.addDoc(docId);
        if (subDoc.getFieldValue() != nullptr) {
            LOG(debug, "value = '%s'", subDoc.getFieldValue()->toString().c_str());
            if (isPosition) {
                LOG(spam, "Position");
                PositionInserter pi(attrV, docId);
                subDoc.getFieldValue()->iterateNested(subDoc.getRange(), pi);
            } else {
                AttributeInserter ai(attrV, docId);
                subDoc.getFieldValue()->iterateNested(subDoc.getRange(), ai);
            }
        } else if (finfoGuard->getName() == "[docid]") {
            _documentIdAttribute.add(documentId.c_str());
            // assert((_docsumCache.cache().size() + 1) == _documentIdAttribute.getNumDocs());
        } else if (finfoGuard->getName() == "[rank]") {
            _shouldFillRankAttribute = true;
        }
    }
}

size_t
SearchVisitor::fillSortBuffer()
{
    size_t pos(0);
    for (size_t index : _sortList) {
        const AttrInfo & finfo = _attributeFields[index];
        int written(0);
        const AttributeGuard &finfoGuard(*finfo._attr);
        LOG(debug, "Adding sortdata for document %d for attribute '%s'",
            finfoGuard->getNumDocs() - 1, finfoGuard->getName().c_str());
        do {
            written = finfo._sort_blob_writer->write(finfoGuard->getNumDocs()-1, &_tmpSortBuffer[0]+pos, _tmpSortBuffer.size() - pos);
            if (written == -1) {
                _tmpSortBuffer.resize(_tmpSortBuffer.size()*2);
            }
        } while (written == -1);
        pos += written;
    }
    return pos;
}

void
SearchVisitor::completedBucket(const document::BucketId&, HitCounter&)
{
    LOG(debug, "Completed bucket");
}

std::unique_ptr<documentapi::QueryResultMessage>
SearchVisitor::generate_query_result(HitCounter& counter)
{
    completedVisitingInternal(counter);
    return std::move(_queryResult);
}

void
SearchVisitor::completedVisitingInternal(HitCounter& hitCounter)
{
    auto capture_issues = std::make_unique<Issue::Binding>(_unique_issues);
    if (!_init_called) {
        init(_params);
    }
    LOG(debug, "Completed visiting");
    vdslib::SearchResult & searchResult(_queryResult->getSearchResult());
    vdslib::DocumentSummary & documentSummary(_queryResult->getDocumentSummary());
    LOG(debug, "Hit count: %lu", searchResult.getHitCount());

    _rankController.onCompletedVisiting(_summaryGenerator.getDocsumCallback(), searchResult);
    LOG(debug, "Hit count: %lu", searchResult.getHitCount());

    /// Now I can sort. No more documentid access order.
    searchResult.sort();
    searchResult.setTotalHitCount(_hitCount - _hitsRejectedCount);

    const char* docId;
    vdslib::SearchResult::RankType rank;
    for (uint32_t i = 0; i < searchResult.getHitCount(); i++) {
        searchResult.getHit(i, docId, rank);
        hitCounter.addHit(document::DocumentId(docId), 0);
    }

    generateGroupingResults();
    generateDocumentSummaries();
    documentSummary.sort();
    capture_issues.reset();
    generate_errors();
    LOG(debug, "Docsum count: %lu", documentSummary.getSummaryCount());
}

void SearchVisitor::completedVisiting(HitCounter& hitCounter)
{
    completedVisitingInternal(hitCounter);
    sendMessage(documentapi::DocumentMessage::UP(_queryResult.release()));
}

void
SearchVisitor::generateGroupingResults()
{
    vdslib::SearchResult & searchResult(_queryResult->getSearchResult());
    for (auto & groupingPtr : _groupingList) {
        Grouping & grouping(*groupingPtr);
        LOG(debug, "grouping before postAggregate: %s", grouping.asString().c_str());
        grouping.postAggregate();
        grouping.postMerge();
        grouping.sortById();
        LOG(debug, "grouping after postAggregate: %s", grouping.asString().c_str());
        vespalib::nbostream os;
        vespalib::NBOSerializer nos(os);
        grouping.serialize(nos);
        vespalib::MallocPtr blob(os.size());
        memcpy(blob, os.data(), os.size());
        searchResult.getGroupingList().add(grouping.getId(), blob);
    }
}

void
SearchVisitor::generateDocumentSummaries()
{
    if ( ! _rankController.valid()) {
        return;
    }
    auto& hit_collector = _rankController.getRankProcessor()->getHitCollector();
    _summaryGenerator.setDocsumCache(hit_collector);
    vdslib::SearchResult & searchResult(_queryResult->getSearchResult());
    auto& index_env = _rankController.getRankProcessor()->get_query_env().getIndexEnvironment();
    _summaryGenerator.getDocsumCallback().set_matching_elements_filler(std::make_unique<MatchingElementsFiller>(_fieldSearcherMap, index_env, _query, hit_collector, searchResult));
    vdslib::DocumentSummary & documentSummary(_queryResult->getDocumentSummary());
    for (size_t i(0), m(searchResult.getHitCount()); (i < m) && (i < searchResult.getWantedHitCount()); i++ ) {
        const char * docId(nullptr);
        vdslib::SearchResult::RankType rank(0);
        uint32_t lid = searchResult.getHit(i, docId, rank);
        vespalib::ConstBufferRef docsum = _summaryGenerator.fillSummary(lid, _summaryClass);
        documentSummary.addSummary(docId, docsum.c_str(), docsum.size());
        LOG(debug, "Adding summary %ld: globalDocId(%s), localDocId(%u), rank(%f), bytes(%lu)",
            i, docId, lid, rank, docsum.size());
    }
}

void
SearchVisitor::generate_errors()
{
    auto num_issues = _unique_issues.size();
    if (num_issues == 0) {
        return;
    }
    std::vector<std::string> errors;
    errors.reserve(num_issues);
    _unique_issues.for_each_message([&](const std::string &issue) { errors.emplace_back(issue); });
    _queryResult->getSearchResult().set_errors(std::move(errors));
}

SearchVisitor::ElementGapInspector::ElementGapInspector() noexcept
  : _query_env(nullptr),
    _cache(16)
{
}

SearchVisitor::ElementGapInspector::~ElementGapInspector() = default;

ElementGap
SearchVisitor::ElementGapInspector::get_element_gap(uint32_t field_id) const noexcept
{
    if (_query_env != nullptr) {
        if (_cache.size() <= field_id) {
            _cache.resize(2 * field_id + 1);
        } else if (_cache[field_id].has_value()) {
            return _cache[field_id].value();
        }
        auto field = _query_env->getIndexEnvironment().getField(field_id);
        if (field != nullptr) {
            ElementGap result = field->get_element_gap();
            using mprops = search::fef::indexproperties::matching::ElementGap;
            std::optional<ElementGap> query_override =
                    mprops::lookup_for_field(_query_env->getProperties(), field->name());
            if (query_override.has_value()) {
                result = query_override.value();
            }
            _cache[field_id] = result;
            return result;
        }
    }
    return std::nullopt;
}

}
