// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributevector.hpp"
#include "address_space_components.h"
#include "attribute_read_guard.h"
#include "attributefilesavetarget.h"
#include "attributesaver.h"
#include "floatbase.h"
#include "interlock.h"
#include "ipostinglistattributebase.h"
#include "stringbase.h"
#include "enummodifier.h"
#include "valuemodifier.h"
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/query/query_term_decoder.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attributevector");

using vespalib::getLastErrorString;

using document::ValueUpdate;
using document::AssignValueUpdate;
using vespalib::IllegalStateException;
using search::attribute::SearchContextParams;
using search::common::FileHeaderContext;
using search::index::DummyFileHeaderContext;
using search::queryeval::SearchIterator;
using namespace vespalib::make_string_short;

namespace {

const vespalib::string enumeratedTag = "enumerated";
const vespalib::string dataTypeTag = "datatype";
const vespalib::string collectionTypeTag = "collectiontype";
const vespalib::string docIdLimitTag = "docIdLimit";

}

namespace search {

namespace {

bool
allow_paged(const search::attribute::Config& config)
{
    if (!config.paged()) {
        return false;
    }
    using Type = search::attribute::BasicType::Type;
    if (config.basicType() == Type::PREDICATE) {
        return false;
    }
    if (config.basicType() == Type::TENSOR) {
        return (!config.tensorType().is_error() && (config.tensorType().is_dense() || !config.fastSearch()));
    }
    return true;
}

std::unique_ptr<vespalib::alloc::MemoryAllocator>
make_memory_allocator(const vespalib::string& name, const search::attribute::Config& config)
{
    if (allow_paged(config)) {
        return vespalib::alloc::MmapFileAllocatorFactory::instance().make_memory_allocator(name);
    }
    return {};
}

}

AttributeVector::AttributeVector(vespalib::stringref baseFileName, const Config &c)
    : _baseFileName(baseFileName),
      _config(std::make_unique<Config>(c)),
      _interlock(std::make_shared<attribute::Interlock>()),
      _enumLock(),
      _genHandler(),
      _genHolder(),
      _status(),
      _highestValueCount(1),
      _enumMax(0),
      _committedDocIdLimit(0u),
      _uncommittedDocIdLimit(0u),
      _createSerialNum(0u),
      _compactLidSpaceGeneration(0u),
      _hasEnum(false),
      _loaded(false),
      _isUpdateableInMemoryOnly(attribute::isUpdateableInMemoryOnly(getName(), getConfig())),
      _nextStatUpdateTime(),
      _memory_allocator(make_memory_allocator(_baseFileName.getAttributeName(), c))
{
}

AttributeVector::~AttributeVector() = default;

void
AttributeVector::updateStat(bool force) {
    if (force) {
        onUpdateStat();
    } else if (_nextStatUpdateTime < vespalib::steady_clock::now()) {
        onUpdateStat();
        _nextStatUpdateTime = vespalib::steady_clock::now() + 5s;
    }
}

bool AttributeVector::hasEnum() const { return _hasEnum; }
uint32_t AttributeVector::getMaxValueCount() const { return _highestValueCount.load(std::memory_order_relaxed); }
bool AttributeVector::hasMultiValue() const { return _config->collectionType().isMultiValue(); }
bool AttributeVector::hasWeightedSetType() const { return _config->collectionType().isWeightedSet(); }
size_t AttributeVector::getFixedWidth() const { return _config->basicType().fixedSize(); }
attribute::BasicType AttributeVector::getInternalBasicType() const { return _config->basicType(); }
attribute::CollectionType AttributeVector::getInternalCollectionType() const { return _config->collectionType(); }
bool AttributeVector::hasArrayType() const { return _config->collectionType().isArray(); }
bool AttributeVector::getIsFilter() const  { return _config->getIsFilter(); }
bool AttributeVector::getIsFastSearch() const { return _config->fastSearch(); }
bool AttributeVector::isMutable() const { return _config->isMutable(); }
bool AttributeVector::getEnableOnlyBitVector() const { return _config->getEnableOnlyBitVector(); }

bool
AttributeVector::isEnumerated(const vespalib::GenericHeader &header)
{
    return header.hasTag(enumeratedTag) &&
           header.getTag(enumeratedTag).asInteger() != 0;
}

void
AttributeVector::commit(bool forceUpdateStats)
{
    onCommit();
    updateCommittedDocIdLimit();
    updateStat(forceUpdateStats);
    _loaded = true;
}

void
AttributeVector::commit(const CommitParam & param)
{
    if (param.firstSerialNum() < getStatus().getLastSyncToken()) {
        LOG(error, "Expected first token to be >= %" PRIu64 ", got %" PRIu64 ".",
            getStatus().getLastSyncToken(), param.firstSerialNum());
        LOG_ABORT("should not be reached");
    }
    commit(param.forceUpdateStats());
    _status.setLastSyncToken(param.lastSerialNum());
}

bool
AttributeVector::addDocs(DocId &startDoc, DocId &lastDoc, uint32_t numDocs)
{
    if (numDocs != 0) {
        onAddDocs(getNumDocs() + numDocs);
        if (!addDoc(startDoc)) {
            return false;
        }
        lastDoc = startDoc;
        for (uint32_t i = 1; i < numDocs; ++i) {
            if (!addDoc(lastDoc)) {
                return false;
            }
        }
    }
    return true;
}

bool
AttributeVector::addDocs(uint32_t numDocs)
{
    DocId doc;
    return addDocs(doc, doc, numDocs);
}

void
AttributeVector::incGeneration()
{
    // Freeze trees etc, to stop new readers from accessing currently held data
    before_inc_generation(_genHandler.getCurrentGeneration());
    _genHandler.incGeneration();
    // Remove old data on hold lists that can no longer be reached by readers
    reclaim_unused_memory();
}

void
AttributeVector::updateStatistics(uint64_t numValues, uint64_t numUniqueValue, uint64_t allocated,
                                  uint64_t used, uint64_t dead, uint64_t onHold)
{
    _status.updateStatistics(numValues, numUniqueValue, allocated, used, dead, onHold);
}

vespalib::MemoryUsage
AttributeVector::getEnumStoreValuesMemoryUsage() const
{
    return vespalib::MemoryUsage();
}

void
AttributeVector::populate_address_space_usage(AddressSpaceUsage& usage) const
{
    // TODO: Stop inserting defaults here when code using AddressSpaceUsage no longer require these two components.
    usage.set(AddressSpaceComponents::enum_store, AddressSpaceComponents::default_enum_store_usage());
    usage.set(AddressSpaceComponents::multi_value, AddressSpaceComponents::default_multi_value_usage());
}

AddressSpaceUsage
AttributeVector::getAddressSpaceUsage() const
{
    AddressSpaceUsage usage;
    populate_address_space_usage(usage);
    return usage;
}

bool
AttributeVector::isImported() const
{
    return false;
}

bool
AttributeVector::headerTypeOK(const vespalib::GenericHeader &header) const
{
    return header.hasTag(dataTypeTag) &&
        header.hasTag(collectionTypeTag) &&
        header.hasTag(docIdLimitTag) &&
        header.getTag(dataTypeTag).asString() == 
        getConfig().basicType().asString() &&
        header.getTag(collectionTypeTag).asString() == 
        getConfig().collectionType().asString();
}

void AttributeVector::reclaim_memory(generation_t oldest_used_gen) { (void) oldest_used_gen; }
void AttributeVector::before_inc_generation(generation_t current_gen) { (void) current_gen; }
const IEnumStore* AttributeVector::getEnumStoreBase() const { return nullptr; }
IEnumStore* AttributeVector::getEnumStoreBase() { return nullptr; }
const attribute::MultiValueMappingBase * AttributeVector::getMultiValueBase() const { return nullptr; }

bool
AttributeVector::save(vespalib::stringref fileName)
{
    TuneFileAttributes tune;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tune, fileHeaderContext);
    return save(saveTarget, fileName);
}

bool
AttributeVector::save()
{
    return save(getBaseFileName());
}


bool
AttributeVector::save(IAttributeSaveTarget &saveTarget, vespalib::stringref fileName)
{
    commit();
    // First check if new style save is available.
    std::unique_ptr<AttributeSaver> saver(onInitSave(fileName));
    if (saver) {
        // Normally, new style save happens in background, but here it
        // will occur in the foreground.
        return saver->save(saveTarget);
    }
    // New style save not available, use old style save
    saveTarget.setHeader(createAttributeHeader(fileName));
    if (!saveTarget.setup()) {
        return false;
    }
    onSave(saveTarget);
    saveTarget.close();
    return true;
}

attribute::AttributeHeader
AttributeVector::createAttributeHeader(vespalib::stringref fileName) const {
    return attribute::AttributeHeader(fileName,
                                      getConfig().basicType(),
                                      getConfig().collectionType(),
                                      getConfig().tensorType(),
                                      getEnumeratedSave(),
                                      getConfig().predicateParams(),
                                      getConfig().hnsw_index_params(),
                                      getCommittedDocIdLimit(),
                                      getUniqueValueCount(),
                                      getTotalValueCount(),
                                      getCreateSerialNum(),
                                      getVersion());
}

void AttributeVector::onSave(IAttributeSaveTarget &)
{
    LOG_ABORT("should not be reached");
}

bool
AttributeVector::hasLoadData() const {
    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(fmt("%s.dat", getBaseFileName().c_str()).c_str(), &statInfo)) {
        return false;
    }
    if (hasMultiValue() &&
        !FastOS_File::Stat(fmt("%s.idx", getBaseFileName().c_str()).c_str(), &statInfo))
    {
        return false;
    }
    if (hasWeightedSetType() &&
        !FastOS_File::Stat(fmt("%s.weight", getBaseFileName().c_str()).c_str(), &statInfo))
    {
        return false;
    }
    if (isEnumeratedSaveFormat() &&
        !FastOS_File::Stat(fmt("%s.udat", getBaseFileName().c_str()).c_str(), &statInfo))
    {
        return false;
    }
    return true;
}


bool
AttributeVector::isEnumeratedSaveFormat() const
{
    vespalib::string datName(fmt("%s.dat", getBaseFileName().c_str()));
    Fast_BufferedFile   datFile;
    vespalib::FileHeader datHeader(FileSettings::DIRECTIO_ALIGNMENT);
    if ( ! datFile.OpenReadOnly(datName.c_str()) ) {
        LOG(error, "could not open %s: %s", datFile.GetFileName(), getLastErrorString().c_str());
        throw IllegalStateException(fmt("Failed opening attribute data file '%s' for reading",
                                                datFile.GetFileName()));
    }
    datHeader.readFile(datFile);
    
    return isEnumerated(datHeader);
}

bool
AttributeVector::load() {
    return load(nullptr);
}

bool
AttributeVector::load(vespalib::Executor * executor) {
    assert(!_loaded);
    bool loaded = onLoad(executor);
    if (loaded) {
        commit();
        incGeneration();
        updateStat(true);
    }
    _loaded = loaded;
    return _loaded;
}

bool AttributeVector::onLoad(vespalib::Executor *) { return false; }
int32_t AttributeVector::getWeight(DocId, uint32_t) const { return 1; }

bool AttributeVector::findEnum(const char *, EnumHandle &) const { return false; }

std::vector<search::attribute::IAttributeVector::EnumHandle>
AttributeVector::findFoldedEnums(const char *) const {
    std::vector<EnumHandle> empty;
    return empty;
}

const char * AttributeVector::getStringFromEnum(EnumHandle) const { return nullptr; }

std::unique_ptr<attribute::SearchContext>
AttributeVector::getSearch(QueryPacketT searchSpec, const SearchContextParams &params) const
{
    return getSearch(QueryTermDecoder::decodeTerm(searchSpec), params);
}

std::unique_ptr<attribute::ISearchContext>
AttributeVector::createSearchContext(QueryTermSimpleUP term, const attribute::SearchContextParams &params) const
{
    return getSearch(std::move(term), params);
}


bool
AttributeVector::apply(DocId doc, const MapValueUpdate &map) {
    bool retval(doc < getNumDocs());
    if (retval) {
        const ValueUpdate & vu(map.getUpdate());
        if (vu.getType() == ValueUpdate::Arithmetic) {
            const ArithmeticValueUpdate &au(static_cast<const ArithmeticValueUpdate &>(vu));
            retval = applyWeight(doc, map.getKey(), au);
        } else if (vu.getType() == ValueUpdate::Assign) {
            const AssignValueUpdate &au(static_cast<const AssignValueUpdate &>(vu));
            retval = applyWeight(doc, map.getKey(), au);
        } else {
            retval = false;
        }
    }
    return retval;
}


bool AttributeVector::applyWeight(DocId, const FieldValue &, const ArithmeticValueUpdate &) { return false; }

bool AttributeVector::applyWeight(DocId, const FieldValue&, const AssignValueUpdate&) { return false; }

void
AttributeVector::reclaim_unused_memory() {
    _genHandler.update_oldest_used_generation();
    reclaim_memory(_genHandler.get_oldest_used_generation());
}


void
AttributeVector::divideByZeroWarning() {
    LOG(warning,
        "applyArithmetic(): "
        "Divide by zero is an illegal operation on integer attributes "
        "or weighted sets. Ignoring operation.");
}


void
AttributeVector::performCompactionWarning()
{
    LOG(warning,
        "Could not perform compaction on MultiValueMapping "
        "with current generation = %" PRIu64,
        _genHandler.getCurrentGeneration());
}


void
AttributeVector::addReservedDoc()
{
    uint32_t docId = 42;
    addDoc(docId);      // Reserved
    assert(docId == 0u);
    assert(docId < getNumDocs());
    set_reserved_doc_values();
}

void
AttributeVector::set_reserved_doc_values()
{
    uint32_t docId = 0;
    if (docId >= getNumDocs()) {
        return;
    }
    clearDoc(docId);
    commit();
}

attribute::IPostingListAttributeBase *AttributeVector::getIPostingListAttributeBase() { return nullptr; }
const attribute::IPostingListAttributeBase *AttributeVector::getIPostingListAttributeBase() const { return nullptr; }
const IDocumentWeightAttribute * AttributeVector::asDocumentWeightAttribute() const { return nullptr; }
const tensor::ITensorAttribute *AttributeVector::asTensorAttribute() const { return nullptr; }
const attribute::IMultiValueAttribute* AttributeVector::as_multi_value_attribute() const { return nullptr; }
bool AttributeVector::hasPostings() { return getIPostingListAttributeBase() != nullptr; }
uint64_t AttributeVector::getUniqueValueCount() const { return getTotalValueCount(); }
uint64_t AttributeVector::getTotalValueCount() const { return getNumDocs(); }
void AttributeVector::setCreateSerialNum(uint64_t createSerialNum) { _createSerialNum = createSerialNum; }
uint64_t AttributeVector::getCreateSerialNum() const { return _createSerialNum; }
uint32_t AttributeVector::getVersion() const { return 0; }

void
AttributeVector::compactLidSpace(uint32_t wantedLidLimit) {
    commit();
    uint32_t committed_doc_id_limit = _committedDocIdLimit.load(std::memory_order_relaxed);
    assert(committed_doc_id_limit >= wantedLidLimit);
    if (wantedLidLimit < committed_doc_id_limit) {
        clearDocs(wantedLidLimit, committed_doc_id_limit, false);
    }
    commit();
    _committedDocIdLimit.store(wantedLidLimit, std::memory_order_release);
    _compactLidSpaceGeneration.store(_genHandler.getCurrentGeneration(), std::memory_order_relaxed);
    incGeneration();
}

bool
AttributeVector::canShrinkLidSpace() const {
    return wantShrinkLidSpace() &&
        _compactLidSpaceGeneration.load(std::memory_order_relaxed) < get_oldest_used_generation();
}

void
AttributeVector::shrinkLidSpace()
{
    commit();
    reclaim_unused_memory();
    if (!canShrinkLidSpace()) {
        return;
    }
    uint32_t committed_doc_id_limit = _committedDocIdLimit.load(std::memory_order_relaxed);
    clearDocs(committed_doc_id_limit, getNumDocs(), true);
    clear_uncommitted_doc_id_limit();
    commit();
    assert(committed_doc_id_limit == _committedDocIdLimit.load(std::memory_order_relaxed));
    onShrinkLidSpace();
    attribute::IPostingListAttributeBase *pab = getIPostingListAttributeBase();
    if (pab != NULL) {
        pab->forwardedShrinkLidSpace(committed_doc_id_limit);
    }
    incGeneration();
    updateStat(true);
}

void AttributeVector::onShrinkLidSpace() {}

void
AttributeVector::clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    uint32_t count = 0;
    constexpr uint32_t commit_interval = 1000;
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        clearDoc(lid);
        if ((++count % commit_interval) == 0) {
            if (in_shrink_lid_space) {
                clear_uncommitted_doc_id_limit();
            }
            commit();
        }
    }
}

attribute::EnumModifier
AttributeVector::getEnumModifier()
{
    attribute::InterlockGuard interlockGuard(*_interlock);
    return attribute::EnumModifier(_enumLock, interlockGuard);
}

attribute::ValueModifier
AttributeVector::getValueModifier() {
    return ValueModifier(*this);
}


void AttributeVector::setInterlock(const std::shared_ptr<attribute::Interlock> &interlock) {
    _interlock = interlock;
}


std::unique_ptr<AttributeSaver>
AttributeVector::initSave(vespalib::stringref fileName)
{
    commit();
    return onInitSave(fileName);
}

std::unique_ptr<AttributeSaver>
AttributeVector::onInitSave(vespalib::stringref)
{
    return std::unique_ptr<AttributeSaver>();
}

bool
AttributeVector::hasActiveEnumGuards()
{
    std::unique_lock<std::shared_mutex> lock(_enumLock, std::defer_lock);
    for (size_t i = 0; i < 1000; ++i) {
        // Note: Need to run this in loop as try_lock() is allowed to fail spuriously and return false
        // even if the mutex is not currently locked by any other thread.
        if (lock.try_lock()) {
            return false;
        }
    }
    return true;
}

IExtendAttribute *AttributeVector::getExtendInterface() { return nullptr; }

uint64_t
AttributeVector::getEstimatedSaveByteSize() const
{
    uint64_t headerSize = FileSettings::DIRECTIO_ALIGNMENT;
    uint64_t totalValueCount = _status.getNumValues();
    uint64_t uniqueValueCount = _status.getNumUniqueValues();
    uint64_t docIdLimit = getCommittedDocIdLimit();
    uint64_t datFileSize = 0;
    uint64_t weightFileSize = 0;
    uint64_t idxFileSize = 0;
    uint64_t udatFileSize = 0;
    size_t fixedWidth = getFixedWidth();

    if (hasMultiValue()) {
        idxFileSize = headerSize + sizeof(uint32_t) * (docIdLimit + 1);
    }
    if (hasWeightedSetType()) {
        weightFileSize = headerSize + sizeof(int32_t) * totalValueCount;
    }
    if (hasEnum()) {
        datFileSize =  headerSize + sizeof(uint32_t) * totalValueCount;
        if (fixedWidth != 0) {
            udatFileSize = headerSize + fixedWidth * uniqueValueCount;
        } else {
            vespalib::MemoryUsage values_mem_usage = getEnumStoreValuesMemoryUsage();
            size_t unique_values_bytes = values_mem_usage.usedBytes() -
                    (values_mem_usage.deadBytes() + values_mem_usage.allocatedBytesOnHold());
            size_t ref_count_mem_usage = sizeof(uint32_t) * uniqueValueCount;
            udatFileSize = headerSize + unique_values_bytes - ref_count_mem_usage;
        }
    } else {
        BasicType::Type basicType(getBasicType());
        const Status &status = getStatus();
        int64_t memorySize = status.getUsed() - status.getDead();
        if (memorySize < 0) {
            memorySize = 0;
        }
        switch (basicType) {
        case BasicType::Type::PREDICATE:
        case BasicType::Type::TENSOR:
            datFileSize = headerSize + memorySize;
            break;
        case BasicType::Type::STRING:
            abort();
            break;
        default:
            datFileSize = headerSize + fixedWidth * totalValueCount;
            break;
        }
    }
    return datFileSize + weightFileSize + idxFileSize + udatFileSize;
}

size_t
AttributeVector::getEstimatedShrinkLidSpaceGain() const
{
    size_t canFree = 0;
    if (canShrinkLidSpace()) {
        uint32_t committedDocIdLimit = getCommittedDocIdLimit();
        uint32_t numDocs = getNumDocs();
        const attribute::Config &cfg = getConfig();
        if (committedDocIdLimit < numDocs) {
            size_t elemSize = 4;
            if (!cfg.collectionType().isMultiValue() && !cfg.fastSearch()) {
                BasicType::Type basicType(getBasicType());
                switch (basicType) {
                case BasicType::Type::PREDICATE:
                case BasicType::Type::TENSOR:
                case BasicType::Type::REFERENCE:
                    break;
                default:
                    elemSize = cfg.basicType().fixedSize();
                }
            }
            canFree = elemSize * (numDocs - committedDocIdLimit);
        }
    }
    return canFree;
}


namespace {

class ReadGuard : public attribute::AttributeReadGuard
{
    using GenerationHandler = vespalib::GenerationHandler;
    GenerationHandler::Guard _generationGuard;
    using EnumGuard = std::shared_lock<std::shared_mutex>;
    EnumGuard _enumGuard;
public:
    ReadGuard(const attribute::IAttributeVector *attr, GenerationHandler::Guard &&generationGuard, std::shared_mutex *enumLock)
        : attribute::AttributeReadGuard(attr),
          _generationGuard(std::move(generationGuard)),
          _enumGuard(enumLock != nullptr ? EnumGuard(*enumLock) : EnumGuard())
    {
    }
};

}

std::unique_ptr<attribute::AttributeReadGuard>
AttributeVector::makeReadGuard(bool stableEnumGuard) const
{
    return std::make_unique<ReadGuard>(this, _genHandler.takeGuard(), stableEnumGuard ? &_enumLock : nullptr);
}

vespalib::MemoryUsage
AttributeVector::getChangeVectorMemoryUsage() const
{
    return vespalib::MemoryUsage(0, 0, 0, 0);
}

bool
AttributeVector::commitIfChangeVectorTooLarge() {
    bool needCommit = getChangeVectorMemoryUsage().usedBytes() > getConfig().getMaxUnCommittedMemory();
    if (needCommit) {
        commit(false);
    }
    return needCommit;
}

void
AttributeVector::logEnumStoreEvent(const char *reason, const char *stage)
{
    vespalib::JSONStringer jstr;
    jstr.beginObject();
    jstr.appendKey("path").appendString(getBaseFileName());
    jstr.endObject();
    vespalib::string eventName(fmt("%s.attribute.enumstore.%s", reason, stage));
    EV_STATE(eventName.c_str(), jstr.toString().data());
}

void
AttributeVector::drain_hold(uint64_t hold_limit)
{
    incGeneration();
    for (int retry = 0; retry < 40; ++retry) {
        reclaim_unused_memory();
        updateStat(true);
        if (_status.getOnHold() <= hold_limit) {
            return;
        }
        std::this_thread::sleep_for(retry < 20 ? 20ms : 100ms);
    }
}

void
AttributeVector::update_config(const Config& cfg)
{
    commit(true);
    _config->setGrowStrategy(cfg.getGrowStrategy());
    if (cfg.getCompactionStrategy() == _config->getCompactionStrategy()) {
        return;
    }
    drain_hold(1_Mi); // Wait until 1MiB or less on hold
    _config->setCompactionStrategy(cfg.getCompactionStrategy());
    updateStat(true);
    commit(); // might trigger compaction
    drain_hold(1_Mi); // Wait until 1MiB or less on hold
}

vespalib::alloc::Alloc
AttributeVector::get_initial_alloc()
{
    return (_memory_allocator ? vespalib::alloc::Alloc::alloc_with_allocator(_memory_allocator.get()) : vespalib::alloc::Alloc::alloc());
}

template bool AttributeVector::append<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &, int32_t, bool);
template bool AttributeVector::update<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &);
template bool AttributeVector::remove<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &, int32_t);

}
