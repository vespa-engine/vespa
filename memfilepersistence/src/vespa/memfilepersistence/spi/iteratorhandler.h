// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::IteratorHandler
 * \ingroup memfile
 *
 * \brief Class exposing iterators over a bucket
 */
#pragma once

#include <map>
#include <vespa/memfilepersistence/spi/operationhandler.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/select/node.h>

namespace document {

class FieldSet;

}

namespace storage {

class GetIterCommand;

namespace memfile {

class CachePrefetchRequirements : public Types
{
public:
    CachePrefetchRequirements()
        : _headerPrefetchRequired(false),
          _bodyPrefetchRequired(false),
          _fromTimestamp(0),
          _toTimestamp(UINT64_MAX)
    {
    }

    bool noPrefetchRequired() const {
        return !_headerPrefetchRequired && !_bodyPrefetchRequired;
    }

    bool isHeaderPrefetchRequired() const { return _headerPrefetchRequired; }
    void setHeaderPrefetchRequired(bool required) { _headerPrefetchRequired = required; }

    bool isBodyPrefetchRequired() const { return _bodyPrefetchRequired; }
    void setBodyPrefetchRequired(bool required) { _bodyPrefetchRequired = required; }

    bool prefetchEntireBlocks() const {
        return (_fromTimestamp == Timestamp(0)
                && _toTimestamp == Timestamp(UINT64_MAX));
    }

    Timestamp getFromTimestamp() const { return _fromTimestamp; }
    void setFromTimestamp(Timestamp fromTimestamp) { _fromTimestamp = fromTimestamp; }
    Timestamp getToTimestamp() const { return _toTimestamp; }
    void setToTimestamp(Timestamp toTimestamp) { _toTimestamp = toTimestamp; }

    static CachePrefetchRequirements createFromSelection(
            const document::DocumentTypeRepo& repo,
            const document::select::Node& sel);
private:
    // Whether or not document selection requires header/body to be read
    // beforehand to work efficiently.
    bool _headerPrefetchRequired;
    bool _bodyPrefetchRequired;

    Timestamp _fromTimestamp;
    Timestamp _toTimestamp;
};

class IteratorState
{
    spi::Bucket _bucket;
    spi::Selection _selection;
    std::unique_ptr<document::FieldSet> _fieldSet;
    std::unique_ptr<document::select::Node> _documentSelection;
    std::vector<Types::Timestamp> _remaining;
    spi::IncludedVersions _versions;
    CachePrefetchRequirements _prefetchRequirements;
    bool _isActive;
    bool _isCompleted;
    std::map<std::string, bool> _headerOnlyForDocumentType;

public:
    IteratorState(const spi::Bucket& bucket,
                  const spi::Selection& sel,
                  document::FieldSet::UP fieldSet,
                  spi::IncludedVersions versions,
                  std::unique_ptr<document::select::Node> docSel,
                  const CachePrefetchRequirements& prefetchRequirements)
        : _bucket(bucket),
          _selection(sel),
          _fieldSet(std::move(fieldSet)),
          _documentSelection(std::move(docSel)),
          _remaining(),
          _versions(versions),
          _prefetchRequirements(prefetchRequirements),
          _isActive(false),
          _isCompleted(false)
    {}

    const spi::Bucket& getBucket() const { return _bucket; }

    const CachePrefetchRequirements& getCachePrefetchRequirements() const {
        return _prefetchRequirements;
    }

    bool isActive() const { return _isActive; }
    void setActive(bool active) { _isActive = active; }

    bool isCompleted() const { return _isCompleted; }
    void setCompleted(bool completed = true) { _isCompleted = completed; }

    const spi::Selection& getSelection() const { return _selection; }
    spi::Selection& getSelection() { return _selection; }
    const document::FieldSet& getFields() const { return *_fieldSet; }

    spi::IncludedVersions getIncludedVersions() const { return _versions; }
    void setIncludedVersions(spi::IncludedVersions versions) { _versions = versions; }
    bool hasDocumentSelection() const { return _documentSelection.get() != 0; }

    /**
     * Can only be called if hasDocumentSelection() == true
     */
    const document::select::Node& getDocumentSelection() const
    {
        return *_documentSelection;
    }
    /**
     * @return pointer to doc selection if one has been given, NULL otherwise.
     */
    const document::select::Node* getDocumentSelectionPtr() const
    {
        return _documentSelection.get();
    }
    const std::vector<Types::Timestamp>& getRemaining() const { return _remaining; }
    std::vector<Types::Timestamp>& getRemaining() { return _remaining; }
};

class SharedIteratorHandlerState
{
public:
    typedef std::map<uint64_t, IteratorState> IteratorStateMap;
private:
    IteratorStateMap _iterators;
    uint64_t _nextId;
    vespalib::Lock _stateLock;
    // Debugging aid:
    static const size_t WARN_ACTIVE_ITERATOR_COUNT = 2048;
    bool _hasWarnedLargeIteratorCount;

    friend class IteratorHandler;
    friend class IteratorHandlerTest;
public:
    SharedIteratorHandlerState() : _nextId(1) {}
};

class IteratorHandler : public OperationHandler
{
private:
    typedef SharedIteratorHandlerState::IteratorStateMap IteratorStateMap;

    class ActiveGuard
    {
        IteratorState& _state;
    public:
        ActiveGuard(IteratorState& state) : _state(state) {}
        ~ActiveGuard() {
            _state.setActive(false);
        }
    };

    /**
     * Get the serialized size of a document, only counting the header if
     * headerOnly is true.
     */
    spi::DocEntry::SizeType getDocumentSize(const MemFile&,
                                            const MemSlot&,
                                            bool headerOnly) const;
    /**
     * Get the in-memory size of a single DocEntry object to more accurately
     * limit per-iteration memory usage.
     */
    spi::DocEntry::SizeType getEntrySize(spi::DocEntry::SizeType docSize) const;
    /**
     * Populate the state's remaining timestamps-vector, either from an
     * explicitly specified timestamp subset in the selection, or from its
     * document selection if no timestamp subset is given.
     * @return mutable reference to the state's remaining-vector.
     */
    std::vector<Types::Timestamp>& getOrFillRemainingTimestamps(
            MemFile& file,
            IteratorState&);

    /**
     * If header/body precaching is required, cache _all_ documents in the
     * required part(s) for the file. Otherwise, do nothing.
     */
    void prefetch(const CachePrefetchRequirements& requirements,
                  MemFile& file) const;

    bool addMetaDataEntry(spi::IterateResult::List& result,
                          const MemSlot& slot,
                          uint64_t& totalSize,
                          uint64_t maxByteSize) const;
    bool addRemoveEntry(spi::IterateResult::List& result,
                        const MemFile& file,
                        const MemSlot& slot,
                        uint64_t& totalSize,
                        uint64_t maxByteSize) const;
    bool addPutEntry(spi::IterateResult::List& result,
                     const MemFile& file,
                     const MemSlot& slot,
                     bool headerOnly,
                     const document::FieldSet& fieldsToKeep,
                     uint64_t& totalSize,
                     uint64_t maxByteSize) const;

    /**
     * Sanity checking to ensure we don't leak iterators. Checks if the number
     * of active iterators exceeds a predefined Large Number(tm) and warns
     * if this is the case. Mutates shared state (sets a "has warned" flag),
     * so must only be called when holding shared state mutex.
     */
    void sanityCheckActiveIteratorCount();

public:
    typedef std::unique_ptr<IteratorHandler> UP;

    SharedIteratorHandlerState _sharedState;

    IteratorHandler(Environment&);
    ~IteratorHandler();

    spi::CreateIteratorResult createIterator(const spi::Bucket& bucket,
                                             const document::FieldSet& fieldSet,
                                             const spi::Selection& sel,
                                             spi::IncludedVersions versions);
    spi::Result destroyIterator(spi::IteratorId id);
    spi::IterateResult iterate(spi::IteratorId id, uint64_t maxByteSize);

    const SharedIteratorHandlerState& getState() const {
        return _sharedState;
    }
};

} // memfile
} // storage

