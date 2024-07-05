// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hitcollector.h"
#include "indexenvironment.h"
#include "queryenvironment.h"
#include "rankmanager.h"
#include "rankprocessor.h"
#include "searchenvironment.h"
#include "querytermdata.h"
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/documenttypemapping.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/searcher/fieldsearcher.h>
#include <vespa/vsm/vsm/docsumfilter.h>
#include <vespa/vsm/vsm/fieldsearchspec.h>
#include <vespa/vsm/vsm/snippetmodifier.h>
#include <vespa/vsm/vsm/vsm-adapter.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/storage/visiting/visitor.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/documentapi/messagebus/messages/queryresultmessage.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <optional>

using namespace search::aggregation;

namespace streaming {

class SearchEnvironmentSnapshot;

/**
 * @class storage::SearchVisitor
 *
 * @brief Visitor that applies a search query to visitor data and
 * converts them to a QueryResultCommand.
 **/
class SearchVisitor : public storage::Visitor,
                      public search::QueryNormalization {
public:
    SearchVisitor(storage::StorageComponent&, storage::VisitorEnvironment& vEnv,
                  const vdslib::Parameters & params);

    ~SearchVisitor() override;

    // This should only be used by unit tests.
    std::unique_ptr<documentapi::QueryResultMessage> generate_query_result(HitCounter& counter);

private:
    /**
     * This struct wraps an attribute vector.
     **/
    struct AttrInfo {
    public:
        /**
         * Construct a new object.
         *
         * @param fid the field id of the attribute field.
         * @param attr a guard to the attribute vector.
         **/
        AttrInfo(vsm::FieldIdT fid, search::AttributeGuard::UP attr) noexcept :
            _field(fid),
            _ascending(true),
            _converter(nullptr),
            _attr(std::move(attr))
        { }
        /**
         * Construct a new object.
         *
         * @param fid the field id of the attribute field.
         * @param attr a guard to the attribute vector.
         * @param ascending whether this attribute should be sorted ascending or not.
         * @param converter is a converter to apply to the attribute before sorting.
         **/
        AttrInfo(vsm::FieldIdT fid, search::AttributeGuard::UP attr, bool ascending, const search::common::BlobConverter * converter) noexcept :
            _field(fid),
            _ascending(ascending),
            _converter(converter),
            _attr(std::move(attr))
        { }
        vsm::FieldIdT          _field;
        bool                   _ascending;
        const search::common::BlobConverter * _converter;
        search::AttributeGuard::UP _attr;
    };

    /**
     * This class gets callbacks when iterating through a field value and
     * inserts the values into a given attribute vector.
     **/
    class AttributeInserter : public document::fieldvalue::IteratorHandler {
    protected:
        search::AttributeVector &      _attribute;
        search::AttributeVector::DocId _docId;

        void onPrimitive(uint32_t fid, const Content & c) override;

    public:
        AttributeInserter(search::AttributeVector & attribute, search::AttributeVector::DocId docId);
    };

    class PositionInserter : public AttributeInserter {
    public:
        PositionInserter(search::AttributeVector & attribute, search::AttributeVector::DocId docId);
        ~PositionInserter() override;
    private:
        void onPrimitive(uint32_t fid, const Content & c) override;
        void onStructStart(const Content & fv) override;
        document::Field _fieldX;
        document::Field _fieldY;
        document::IntFieldValue _valueX;
        document::IntFieldValue _valueY;
    };

    /**
     * This class controls all the ranking related objects.
     **/
    class RankController {
    private:
        vespalib::string               _rankProfile;
        std::shared_ptr<const RankManager::Snapshot>  _rankManagerSnapshot;
        std::optional<search::feature_t> _rank_score_drop_limit;
        bool                           _hasRanking;
        bool                           _hasSummaryFeatures;
        bool                           _dumpFeatures;
        search::fef::Properties        _queryProperties;
        search::fef::Properties        _featureOverrides;
        RankProcessor::UP              _rankProcessor;
        RankProcessor::UP              _dumpProcessor;

        /**
         * Process attribute accessed and add needed attributes to the
         * given list.
         **/
        static void processAccessedAttributes(const QueryEnvironment& queryEnv,
                                              bool rank,
                                              const search::IAttributeManager& attrMan,
                                              std::vector<AttrInfo>& attributeFields);

    public:
        RankController();
        ~RankController();
        bool valid() const { return bool(_rankProcessor); }
        void setRankProfile(const vespalib::string &rankProfile) { _rankProfile = rankProfile; }
        const vespalib::string &getRankProfile() const { return _rankProfile; }
        void setRankManagerSnapshot(const std::shared_ptr<const RankManager::Snapshot>& snapshot) { _rankManagerSnapshot = snapshot; }
        search::fef::Properties & getQueryProperties() { return _queryProperties; }
        search::fef::Properties & getFeatureOverrides() { return _featureOverrides; }
        RankProcessor * getRankProcessor() { return _rankProcessor.get(); }
        void setDumpFeatures(bool dumpFeatures) { _dumpFeatures = dumpFeatures; }
        bool getDumpFeatures() const { return _dumpFeatures; }
        std::optional<search::feature_t> rank_score_drop_limit() const noexcept { return _rank_score_drop_limit; }

        /**
         * Setup rank processors used for ranking and dumping.
         *
         * @param query the query associated with the search visitor.
         * @param wantedHitCount number of hits wanted.
         * @param attrMan the attribute manager.
         * @param attributeFields the list of attribute vectors needed.
         **/
        void setupRankProcessors(search::streaming::Query & query,
                                 const vespalib::string & location,
                                 size_t wantedHitCount, bool use_sort_blob,
                                 const search::IAttributeManager & attrMan,
                                 std::vector<AttrInfo> & attributeFields);
        /**
         * Callback function that is called for each document that match.
         * Unpack match data.
         *
         * @param docId the docId to use for this hit
         **/
        void onDocumentMatch(uint32_t docId);

        /**
         * Calculate rank for a matched document.
         **/
        void rankMatchedDocument(uint32_t docId);

        /**
         * Returns whether we should keep the matched document.
         * Use the rank-score-drop-limit to decide this.
         **/
        bool keepMatchedDocument();

        /**
         * Collect a matched document in the hit collector.
         * Take sort spec into consideration if used.
         *
         * @param hasSorting whether the search result should be sorted.
         * @param visitor the search visitor.
         * @param tmpSortBuffer the sort buffer containing the sort data.
         * @param document the document to collect. Must be kept alive on the outside.
         **/
        void collectMatchedDocument(bool hasSorting,
                                    SearchVisitor & visitor,
                                    const std::vector<char> & tmpSortBuffer,
                                    vsm::StorageDocument::SP document);
        /**
         * Callback function that is called when visiting is completed.
         * Perform second phase ranking and calculate summary features / rank features if asked for.
         *
         * @param docsumsStateCallback state object to store summary features and rank features.
         **/
        void onCompletedVisiting(vsm::GetDocsumsStateCallback & docsumsStateCallback, vdslib::SearchResult & searchResult);
        vespalib::FeatureSet::SP getFeatureSet(search::DocumentIdT docId);
    };

    /**
     * This class controls all the synthetic fields
     **/
    class SyntheticFieldsController {
    private:
        vsm::FieldIdT _documentIdFId;

    public:
        SyntheticFieldsController();

        /**
         * Setup synthetic fields, like 'sddocname' and 'documentid'.
         *
         * @param fieldRegistry mapping from field name to field id for all known fields.
         * @param fieldsInQuery mapping from field name to field id for fields mentioned in the query.
         **/
        void setup(const vsm::StringFieldIdTMap & fieldRegistry,
                   const vsm::StringFieldIdTMap & fieldsInQuery);

        /**
         * Callback function that is called for each document received.
         *
         * @param document the document received.
         **/
        void onDocument(vsm::StorageDocument & document);

        /**
         * Callback function that is called for each document matched.
         *
         * @param document the document matched.
         * @param documentId the document id of the matched document.
         **/
        void onDocumentMatch(vsm::StorageDocument & document,
                             const vespalib::string & documentId);
    };

    /**
     * Register field names from the given docsum spec into the given field name list.
     * These field names are in addition to the field names found in the vsmfields config.
     * Duplicates are removed when later building mapping from field name to field id.
     *
     * @param docsumSpec config with the field names used by the docsum setup.
     * @param fieldList list of field names that are built.
     **/
    static std::vector<vespalib::string> registerAdditionalFields(const std::vector<vsm::DocsumTools::FieldSpec> & docsumSpec);

    /**
     * Setup the field searchers used when matching the query with the stream of documents.
     * This includes setting up various mappings in FieldSearchSpecMap and building mapping
     * for fields used by the query.
     *
     **/
    vsm::StringFieldIdTMap setupFieldSearchers();

    /**
     * Prepare the field searchers for the given query.
     * This includes connecting the query terms searching a given field to that field searcher,
     * and setting up objects in the field searcher needed when matching later on.
     **/
    void prepare_field_searchers();

    /**
     * Setup snippet modifiers for the fields where we have substring search.
     * The modifiers will be used when generating docsum.
     **/
    void setupSnippetModifiers();

    /**
     * Setup the scratch document that is used when receiving a stream of documents through the visitor api.
     * Each document in this stream is serialized into the scratch document and passed to vsm for matching.
     **/
    void setupScratchDocument(const vsm::StringFieldIdTMap & fieldsInQuery);

    /**
     * Setup the objects used for document summary.
     **/
    void setupDocsumObjects();

    /**
     * Create and register an attribute vector in the attribute manager for each field value in the scratch document.
     * If later needed during evaluation, these attribute vectors are filled with the actual
     * value(s) from the scratch document.
     **/
    void setupAttributeVectors();

    /**
     * Setup attribute vectors needed for sorting.
     *
     * @param sortList the list of attributes needed for sorting.
     **/
    void setupAttributeVectorsForSorting(const search::common::SortSpec & sortList);

    /**
     * Setup grouping based on the given grouping blob.
     *
     * @param groupingBlob the binary representation of the grouping specification.
     **/
    void setupGrouping(const std::vector<char> & groupingBlob);

    // Inherit doc from Visitor
    void handleDocuments(const document::BucketId&,
                         DocEntryList& entries,
                         HitCounter& hitCounter) override;

    static bool compatibleDocumentTypes(const document::DocumentType& typeA,
                                        const document::DocumentType& typeB);

    /**
     * Process one document
     * @param document Document to process.
     */
    void handleDocument(vsm::StorageDocument::SP document);

    /**
     * Collect the given document for grouping.
     *
     * @param doc the document used for grouping.
     * @param all whether we should group all documents, not just hits.
     **/
    void group(const document::Document & doc, search::HitRank rank, bool all);

    /**
     * Check if the given document matches the query.
     *
     * @param doc the document to match.
     * @return whether the document matched the query.
     **/
    bool match(const vsm::StorageDocument & doc);

    /**
     * Fill attribute vectors needed for aggregation and sorting with values from the scratch document.
     *
     * @param documentId the document id of the matched document.
     **/
    void fillAttributeVectors(const vespalib::string & documentId, const vsm::StorageDocument & document);

    /**
     * Fill the sort buffer based on the attribute vectors needed for sorting.
     *
     * @return the position of the sort buffer.
     **/
    size_t fillSortBuffer();

    // Inherit doc from Visitor
    void completedBucket(const document::BucketId&, HitCounter& counter) override;

    // Inherit doc from Visitor
    void completedVisiting(HitCounter& counter) override;

    storage::spi::ReadConsistency getRequiredReadConsistency() const override {
        // Searches are not considered to require strong consistency.
        return storage::spi::ReadConsistency::WEAK;
    }

    /**
     * Required to be called at least once.
     */
    void completedVisitingInternal(HitCounter& counter);

    /**
     * Generate grouping results from the new grouping framework (if any) and add them to the search result.
     **/
    void generateGroupingResults();

    /**
     * Generate document summaries for a specified subset of the hits.
     **/
    void generateDocumentSummaries();

    class GroupingEntry : std::shared_ptr<Grouping> {
    public:
        explicit GroupingEntry(Grouping * grouping);
        ~GroupingEntry();
        void aggregate(const document::Document & doc, search::HitRank rank);
        const Grouping & operator * () const { return *_grouping; }
        Grouping & operator * () { return *_grouping; }
        const Grouping * operator -> () const { return _grouping.get(); }
    private:
        std::shared_ptr<Grouping> _grouping;
        size_t _count;
        size_t _limit;
    };
    using GroupingList = std::vector< GroupingEntry >;

    class StreamingDocsumsState {
        using ResolveClassInfo = search::docsummary::IDocsumWriter::ResolveClassInfo;
        GetDocsumsState  _state;
        ResolveClassInfo _resolve_class_info;
    public:
        StreamingDocsumsState(search::docsummary::GetDocsumsStateCallback& callback, ResolveClassInfo& resolve_class_info);
        ~StreamingDocsumsState();
        GetDocsumsState& get_state() noexcept { return _state; }
        const ResolveClassInfo& get_resolve_class_info() const noexcept { return _resolve_class_info; }
    };

    class SummaryGenerator : public HitsAggregationResult::SummaryGenerator
    {
    public:
        explicit SummaryGenerator(const search::IAttributeManager&, const search::QueryNormalization &);
        ~SummaryGenerator() override;
        vsm::GetDocsumsStateCallback & getDocsumCallback() { return _callback; }
        void setFilter(std::unique_ptr<vsm::DocsumFilter> filter) { _docsumFilter = std::move(filter); }
        void setDocsumCache(const vsm::IDocSumCache & cache) { _docsumFilter->setDocSumStore(cache); }
        void setDocsumWriter(IDocsumWriter & docsumWriter) { _docsumWriter = & docsumWriter; }
        vespalib::ConstBufferRef fillSummary(search::AttributeVector::DocId lid, std::string_view summaryClass) override;
        void set_dump_features(bool dump_features) { _dump_features = dump_features; }
        void set_location(const vespalib::string& location) { _location = location; }
        void set_stack_dump(std::vector<char> stack_dump) { _stack_dump = std::move(stack_dump); }
        void add_summary_field(std::string_view field) { _summaryFields.emplace_back(field); }
        search::fef::Properties & highlightTerms() { return _highlight_terms;}
    private:
        StreamingDocsumsState& get_streaming_docsums_state(std::string_view summary_class);
        vsm::GetDocsumsStateCallback            _callback;
        vespalib::hash_map<vespalib::string, std::unique_ptr<StreamingDocsumsState>> _docsum_states;
        std::vector<vespalib::string>           _summaryFields;
        std::unique_ptr<vsm::DocsumFilter>      _docsumFilter;
        search::docsummary::IDocsumWriter     * _docsumWriter;
        vespalib::SmartBuffer                   _buf;
        std::optional<bool>                     _dump_features;
        std::optional<vespalib::string>         _location;
        std::optional<std::vector<char>>        _stack_dump;
        search::fef::Properties                 _highlight_terms;
        const search::IAttributeManager&        _attr_manager;
        const search::QueryNormalization &      _query_normalization;
    };

    class HitsResultPreparator : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        explicit HitsResultPreparator(SummaryGenerator & summaryGenerator)
            : _summaryGenerator(summaryGenerator),
              _numHitsAggregators(0)
        { }
        size_t getNumHitsAggregators() const  { return _numHitsAggregators; }
    private:
        void execute(vespalib::Identifiable &obj) override;
        bool check(const vespalib::Identifiable &obj) const override;
        SummaryGenerator & _summaryGenerator;
        size_t             _numHitsAggregators;
    };

    void init(const vdslib::Parameters & params);
    std::shared_ptr<const SearchEnvironmentSnapshot> _env;
    vdslib::Parameters                      _params;
    bool                                    _init_called;
    bool                                    _collectGroupingHits;
    size_t                                  _docSearchedCount;
    size_t                                  _hitCount;
    size_t                                  _hitsRejectedCount;
    search::streaming::Query                _query;
    std::unique_ptr<documentapi::QueryResultMessage>    _queryResult;
    vsm::FieldIdTSearcherMap                _fieldSearcherMap;
    vsm::SharedFieldPathMap                 _fieldPathMap;
    vsm::DocumentTypeMapping                _docTypeMapping;
    vsm::FieldSearchSpecMap                 _fieldSearchSpecMap;
    vsm::SnippetModifierManager             _snippetModifierManager;
    vespalib::string                        _summaryClass;
    search::AttributeManager                _attrMan;
    search::attribute::IAttributeContext::UP _attrCtx;
    SummaryGenerator                        _summaryGenerator;
    GroupingList                            _groupingList;
    std::vector<AttrInfo>                   _attributeFields;
    search::common::SortSpec                _sortSpec;
    std::vector<size_t>                     _sortList;
    vsm::SharedSearcherBuf                  _searchBuffer;
    std::vector<char>                       _tmpSortBuffer;
    search::AttributeVector::SP             _documentIdAttributeBacking;
    search::AttributeVector::SP             _rankAttributeBacking;
    search::SingleStringExtAttribute      & _documentIdAttribute;
    search::SingleFloatExtAttribute       & _rankAttribute;
    bool                                    _shouldFillRankAttribute;
    SyntheticFieldsController               _syntheticFieldsController;
    RankController                          _rankController;
    vsm::StringFieldIdTMapT                 _fieldsUnion;

    void setupAttributeVector(const vsm::FieldPath &fieldPath);
    bool is_text_matching(std::string_view index) const noexcept override;
    Normalizing normalizing_mode(std::string_view index) const noexcept override;
};

class SearchVisitorFactory : public storage::VisitorFactory {
    config::ConfigUri _configUri;
    std::shared_ptr<storage::VisitorEnvironment> _env;
    std::shared_ptr<storage::VisitorEnvironment> makeVisitorEnvironment(storage::StorageComponent&) override;

    storage::Visitor* makeVisitor(storage::StorageComponent&, storage::VisitorEnvironment&env,
                                  const vdslib::Parameters& params) override;
public:
    explicit SearchVisitorFactory(const config::ConfigUri & configUri, FNET_Transport* transport, const vespalib::string& file_distributor_connection_spec);
    ~SearchVisitorFactory() override;
    std::optional<int64_t> get_oldest_config_generation() const;
};

}
