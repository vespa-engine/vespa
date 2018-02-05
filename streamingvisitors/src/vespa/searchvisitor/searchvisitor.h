// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hitcollector.h"
#include "indexenvironment.h"
#include "queryenvironment.h"
#include "rankmanager.h"
#include "rankprocessor.h"
#include "searchenvironment.h"
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
#include <vespa/searchlib/query/query.h>
#include <vespa/searchlib/aggregation/aggregation.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/storage/visiting/visitor.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/documentapi/messagebus/messages/queryresultmessage.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>

using namespace search::aggregation;

namespace storage {

/**
 * @class storage::SearchVisitor
 *
 * @brief Visitor that applies a search query to visitor data and
 * converts them to a SearchResultCommand and a DocumentSummaryCommand.
 **/
class SearchVisitor : public Visitor {
public:
    SearchVisitor(StorageComponent&, VisitorEnvironment& vEnv,
                  const vdslib::Parameters & params);

    ~SearchVisitor();
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
        AttrInfo(vsm::FieldIdT fid, search::AttributeGuard::UP attr) :
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
        AttrInfo(vsm::FieldIdT fid, search::AttributeGuard::UP attr, bool ascending, const search::common::BlobConverter * converter) :
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
        ~PositionInserter();
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
        RankManager::Snapshot::SP      _rankManagerSnapshot;
        const search::fef::RankSetup * _rankSetup;
        search::fef::Properties        _queryProperties;
        bool                           _hasRanking;
        RankProcessor::UP              _rankProcessor;
        bool                           _dumpFeatures;
        RankProcessor::UP              _dumpProcessor;

        /**
         * Process attribute hints and add needed attributes to the given list.
         **/
        void processHintedAttributes(const IndexEnvironment & indexEnv, bool rank,
                                     const search::IAttributeManager & attrMan,
                                     std::vector<AttrInfo> & attributeFields);

    public:
        RankController();
        ~RankController();
        bool valid() const { return _rankProcessor.get() != nullptr; }
        void setRankProfile(const vespalib::string &rankProfile) { _rankProfile = rankProfile; }
        const vespalib::string &getRankProfile() const { return _rankProfile; }
        void setRankManagerSnapshot(const RankManager::Snapshot::SP & snapshot) { _rankManagerSnapshot = snapshot; }
        search::fef::Properties & getQueryProperties() { return _queryProperties; }
        RankProcessor * getRankProcessor() { return _rankProcessor.get(); }
        void setDumpFeatures(bool dumpFeatures) { _dumpFeatures = dumpFeatures; }
        bool getDumpFeatures() const { return _dumpFeatures; }
        const search::fef::RankSetup * getRankSetup() const { return _rankSetup; }

        /**
         * Setup rank processors used for ranking and dumping.
         *
         * @param query the query associated with the search visitor.
         * @param wantedHitCount number of hits wanted.
         * @param attrMan the attribute manager.
         * @param attributeFields the list of attribute vectors needed.
         **/
        void setupRankProcessors(search::Query & query,
                                 const vespalib::string & location,
                                 size_t wantedHitCount,
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
         * @return true if the document was added to the heap
         **/
        bool collectMatchedDocument(bool hasSorting,
                                    SearchVisitor & visitor,
                                    const std::vector<char> & tmpSortBuffer,
                                    const vsm::StorageDocument * document);
        /**
         * Callback function that is called when visiting is completed.
         * Perform second phase ranking and calculate summary features / rank features if asked for.
         *
         * @param docsumsStateCallback state object to store summary features and rank features.
         **/
        void onCompletedVisiting(vsm::GetDocsumsStateCallback & docsumsStateCallback, vdslib::SearchResult & searchResult);
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
    void registerAdditionalFields(const std::vector<vsm::DocsumTools::FieldSpec> & docsumSpec,
                                  std::vector<vespalib::string> & fieldList);

    /**
     * Setup the field searchers used when matching the query with the stream of documents.
     * This includes setting up various mappings in FieldSearchSpecMap and building mapping
     * for fields used by the query.
     *
     * @param additionalFields list of additional field names used when setting up the mappings.
     * @param fieldsInQuery mapping from field name to field id that are built based on the query.
     **/
    void setupFieldSearchers(const std::vector<vespalib::string> & additionalFields,
                             vsm::StringFieldIdTMap & fieldsInQuery);

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
                         std::vector<spi::DocEntry::UP>& entries,
                         HitCounter& hitCounter) override;

    bool compatibleDocumentTypes(const document::DocumentType& typeA,
                                 const document::DocumentType& typeB) const;

    /**
     * Process one document
     * @param document Document to process.
     * @return true if the underlying buffer is needed later on, then it must be kept.
     */
    bool handleDocument(vsm::StorageDocument & document);

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

    spi::ReadConsistency getRequiredReadConsistency() const override {
        // Searches are not considered to require strong consistency.
        return spi::ReadConsistency::WEAK;
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
        GroupingEntry(Grouping * grouping);
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
    typedef std::vector< GroupingEntry > GroupingList;
    typedef std::vector<vsm::StorageDocument::UP> DocumentVector;

    class SummaryGenerator : public HitsAggregationResult::SummaryGenerator
    {
    public:
        SummaryGenerator();
        ~SummaryGenerator();
        GetDocsumsState & getDocsumState() { return _docsumState; }
        vsm::GetDocsumsStateCallback & getDocsumCallback() { return _callback; }
        void setFilter(std::unique_ptr<vsm::DocsumFilter> filter) { _docsumFilter = std::move(filter); }
        void setDocsumCache(const vsm::IDocSumCache & cache) { _docsumFilter->setDocSumStore(cache); }
        void setDocsumWriter(IDocsumWriter & docsumWriter) { _docsumWriter = & docsumWriter; }
        virtual vespalib::ConstBufferRef fillSummary(search::AttributeVector::DocId lid, const HitsAggregationResult::SummaryClassType & summaryClass) override;
    private:
        vsm::GetDocsumsStateCallback            _callback;
        GetDocsumsState                         _docsumState;
        std::unique_ptr<vsm::DocsumFilter>      _docsumFilter;
        search::docsummary::IDocsumWriter     * _docsumWriter;
        search::RawBuf                          _rawBuf;
    };

    class HitsResultPreparator : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        HitsResultPreparator(SummaryGenerator & summaryGenerator) :
           _summaryGenerator(summaryGenerator),
           _numHitsAggregators(0)
        { }
        size_t getNumHitsAggregators() const  { return _numHitsAggregators; }
    private:
        virtual void execute(vespalib::Identifiable &obj) override;
        virtual bool check(const vespalib::Identifiable &obj) const override;
        SummaryGenerator & _summaryGenerator;
        size_t             _numHitsAggregators;
    };

    void init(const vdslib::Parameters & params);
    SearchEnvironment                     & _env;
    vdslib::Parameters                      _params;
    const vsm::VSMAdapter                 * _vsmAdapter;
    size_t                                  _docSearchedCount;
    size_t                                  _hitCount;
    size_t                                  _hitsRejectedCount;
    search::Query                           _query;
    std::unique_ptr<documentapi::QueryResultMessage>    _queryResult;
    vsm::FieldIdTSearcherMap                _fieldSearcherMap;
    vsm::SharedFieldPathMap                 _fieldPathMap;
    vsm::DocumentTypeMapping                _docTypeMapping;
    vsm::FieldSearchSpecMap                 _fieldSearchSpecMap;
    vsm::SnippetModifierManager             _snippetModifierManager;
    SummaryGenerator                        _summaryGenerator;
    vespalib::string                        _summaryClass;
    search::AttributeManager                _attrMan;
    search::attribute::IAttributeContext::UP _attrCtx;
    GroupingList                            _groupingList;
    std::vector<AttrInfo>                   _attributeFields;
    search::common::SortSpec                _sortSpec;
    std::vector<size_t>                     _sortList;
    IDocsumWriter                         * _docsumWriter;
    vsm::SharedSearcherBuf                  _searchBuffer;
    std::vector<char>                       _tmpSortBuffer;
    search::AttributeVector::SP    _documentIdAttributeBacking;
    search::AttributeVector::SP    _rankAttributeBacking;
    search::SingleStringExtAttribute      & _documentIdAttribute;
    search::SingleFloatExtAttribute       & _rankAttribute;
    bool                                    _shouldFillRankAttribute;
    SyntheticFieldsController               _syntheticFieldsController;
    RankController                          _rankController;
    DocumentVector                          _backingDocuments;
    vsm::StringFieldIdTMapT                 _fieldsUnion;

    void setupAttributeVector(const vsm::FieldPath &fieldPath);
};

class SearchVisitorFactory : public VisitorFactory {
    config::ConfigUri _configUri;
    VisitorEnvironment::UP makeVisitorEnvironment(StorageComponent&) override;

    Visitor* makeVisitor(StorageComponent&, VisitorEnvironment&env,
                         const vdslib::Parameters& params) override;
public:
    SearchVisitorFactory(const config::ConfigUri & configUri);
};

}
