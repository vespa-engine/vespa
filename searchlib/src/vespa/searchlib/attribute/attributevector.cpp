// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "attributevector.h"
#include "attributevector.hpp"
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/searchlib/util/filesizecalculator.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/data/fileheader.h>
#include <functional>
#include <stdexcept>
#include "ipostinglistsearchcontext.h"
#include "ipostinglistattributebase.h"
#include <vespa/searchlib/queryeval/emptysearch.h>
#include "interlock.h"
#include "attributesaver.h"
#include <vespa/document/update/mapvalueupdate.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attributevector");

using vespalib::getLastErrorString;

using document::ValueUpdate;
using vespalib::make_string;
using vespalib::Array;
using vespalib::IllegalStateException;
using search::common::FileHeaderContext;
using search::index::DummyFileHeaderContext;
using search::queryeval::SearchIterator;

namespace {

const vespalib::string enumeratedTag = "enumerated";
const vespalib::string dataTypeTag = "datatype";
const vespalib::string collectionTypeTag = "collectiontype";
const vespalib::string createSerialNumTag = "createSerialNum";
const vespalib::string versionTag = "version";
const vespalib::string docIdLimitTag = "docIdLimit";

bool allowEnumeratedLoad = true;
const size_t DIRECTIO_ALIGNMENT(4096);

bool
isEnumerated(const vespalib::GenericHeader &header)
{
    return header.hasTag(enumeratedTag) &&
        header.getTag(enumeratedTag).asInteger() != 0;
}

uint64_t
extractCreateSerialNum(const vespalib::GenericHeader &header)
{
    if (header.hasTag(createSerialNumTag))
        return header.getTag(createSerialNumTag).asInteger();
    else
        return 0u;
}

template <typename T>
struct FuncMax : public std::binary_function<T, T, T> {
    T operator() (const T & x, const T & y) const {
        return std::max(x, y);
    }
};

}

namespace search {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(AttributeVector, vespalib::Identifiable);

AttributeVector::BaseName::BaseName(const vespalib::stringref &base,
                                    const vespalib::stringref &snap,
                                    const vespalib::stringref &name)
    : string(base),
      _name(name)
{
    if (!empty()) {
        push_back('/');
    }
    if ( ! snap.empty() ) {
        append(snap);
        push_back('/');
    }
    append(name);
}


AttributeVector::BaseName::string
AttributeVector::BaseName::getIndexName() const
{
    // "$VESPA_HOME/var/db/vespa/search/cluster.search/r0/c0/typetest_search/1.ready/attribute/stringfield/snapshot-0/stringfield"
    string index;
    size_t snapshotPos(rfind("/snapshot-"));
    if (snapshotPos == string::npos)
        return index;
    size_t attrNamePos(rfind('/', snapshotPos  - 1));
    if (attrNamePos == string::npos || attrNamePos == 0)
        return index;
    size_t attrStrPos(rfind('/', attrNamePos - 1));
    if (attrStrPos == string::npos || attrStrPos == 0)
        return index;
    size_t subDBPos(rfind('/', attrStrPos - 1));
    if (subDBPos == string::npos || subDBPos == 0)
        return index;
    size_t indexNamePos(rfind('/', subDBPos - 1));
    if (indexNamePos == string::npos)
        return substr(0, subDBPos);
    return substr(indexNamePos + 1, subDBPos - indexNamePos - 1);
}


AttributeVector::BaseName::string
AttributeVector::BaseName::getSnapshotName() const
{
    string snapShot;
    size_t p(rfind("snapshot-"));
    if (p != string::npos) {
        string fullSnapshot(substr(p));
        p = fullSnapshot.find('/');
        if (p != string::npos) {
            snapShot = fullSnapshot.substr(0, p);
        }
    }
    return snapShot;
}


AttributeVector::BaseName::string
AttributeVector::BaseName::createAttributeName(const vespalib::stringref & s)
{
    size_t p(s.rfind('/'));
    if (p == string::npos) {
       return s;
    } else {
        return s.substr(p+1);
    }
}


AttributeVector::BaseName::string
AttributeVector::BaseName::getDirName() const
{
    size_t p = rfind('/');
    if (p == string::npos) {
       return "";
    } else {
        return substr(0, p);
    }
}


AttributeVector::ValueModifier::ValueModifier(AttributeVector &attr)
    : _attr(&attr)
{
}


AttributeVector::ValueModifier::ValueModifier(const ValueModifier &rhs)
    : _attr(rhs.stealAttr())
{
}


AttributeVector::ValueModifier::~ValueModifier()
{
    if (_attr) {
        _attr->incGeneration();
    }
}


AttributeVector::AttributeVector(const vespalib::stringref &baseFileName,
                                 const Config &c)
    : _baseFileName(baseFileName),
      _config(c),
      _interlock(std::make_shared<attribute::Interlock>()),
      _enumLock(),
      _genHandler(),
      _genHolder(),
      _status(Status::createName((_baseFileName.getIndexName() +
                                  (_baseFileName.getSnapshotName().empty() ?
                                   "" :
                                   ".") +
                                  _baseFileName.getSnapshotName()),
                                 _baseFileName.getAttributeName())),
      _highestValueCount(1),
      _enumMax(0),
      _committedDocIdLimit(0u),
      _uncommittedDocIdLimit(0u),
      _createSerialNum(0u),
      _compactLidSpaceGeneration(0u),
      _hasEnum(false),
      _hasSortedEnum(false),
      _loaded(false),
      _enableEnumeratedSave(false)
{
}


AttributeVector::~AttributeVector()
{
}

void AttributeVector::updateStat(bool force)
{
    if (force) {
        onUpdateStat();
    } else if (_nextStatUpdateTime < fastos::ClockSystem::now()) {
        onUpdateStat();
        _nextStatUpdateTime = fastos::ClockSystem::now() +
                              fastos::TimeStamp::SEC;
    }
}


void
AttributeVector::commit(bool forceUpdateStat)
{
    onCommit();
    updateCommittedDocIdLimit();
    updateStat(forceUpdateStat);
    _loaded = true;
}


void
AttributeVector::commit(uint64_t firstSyncToken, uint64_t lastSyncToken)
{
    if (firstSyncToken < getStatus().getLastSyncToken()) {
        LOG(error,
            "Expected first token to be >= %" PRIu64 ", got %" PRIu64 ".",
            getStatus().getLastSyncToken(), firstSyncToken);
        abort();
    }
    commit();
    _status.setLastSyncToken(lastSyncToken);
}


bool
AttributeVector::addDocs(DocId &startDoc, DocId &lastDoc, uint32_t numDocs)
{
    if (numDocs != 0) {
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
    onGenerationChange(_genHandler.getNextGeneration());
    _genHandler.incGeneration();
    // Remove old data on hold lists that can no longer be reached by readers
    removeAllOldGenerations();
}


void
AttributeVector::updateStatistics(uint64_t numValues,
                                  uint64_t numUniqueValue,
                                  uint64_t allocated,
                                  uint64_t used,
                                  uint64_t dead,
                                  uint64_t onHold)
{
    _status.updateStatistics(numValues,
                             numUniqueValue,
                             allocated,
                             used,
                             dead,
                             onHold);
}

AddressSpace
AttributeVector::getEnumStoreAddressSpaceUsage() const
{
    return AddressSpaceUsage::defaultEnumStoreUsage();
}

AddressSpace
AttributeVector::getMultiValueAddressSpaceUsage() const
{
    return AddressSpaceUsage::defaultMultiValueUsage();
}

AddressSpaceUsage
AttributeVector::getAddressSpaceUsage() const
{
    return AddressSpaceUsage(getEnumStoreAddressSpaceUsage(),
                             getMultiValueAddressSpaceUsage());
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


std::unique_ptr<Fast_BufferedFile>
AttributeVector::openFile(const char *suffix)
{
    BaseName::string fileName(getBaseFileName());
    fileName += suffix;
    return FileUtil::openFile(fileName);
}


std::unique_ptr<Fast_BufferedFile>
AttributeVector::openDAT()
{
    return openFile(".dat");
}


std::unique_ptr<Fast_BufferedFile>
AttributeVector::openIDX()
{
    return openFile(".idx");
}


std::unique_ptr<Fast_BufferedFile>
AttributeVector::openWeight()
{
    return openFile(".weight");
}


std::unique_ptr<Fast_BufferedFile>
AttributeVector::openUDAT()
{
    return openFile(".dat");
}


AttributeVector::ReaderBase::ReaderBase(AttributeVector &attr)
    : _datFile(attr.openDAT()),
      _weightFile(attr.hasWeightedSetType() ?
                  attr.openWeight() : std::unique_ptr<Fast_BufferedFile>()),
      _idxFile(attr.hasMultiValue() ?
               attr.openIDX() : std::unique_ptr<Fast_BufferedFile>()),
      _udatFile(),
      _weightReader(*_weightFile),
      _idxReader(*_idxFile),
      _enumReader(*_datFile),
      _currIdx(0),
      _datHeaderLen(0u),
      _idxHeaderLen(0u),
      _weightHeaderLen(0u),
      _udatHeaderLen(0u),
      _createSerialNum(0u),
      _fixedWidth(attr.getFixedWidth()),
      _enumerated(false),
      _hasLoadData(false),
      _version(0),
      _docIdLimit(0),
      _datHeader(DIRECTIO_ALIGNMENT),
      _datFileSize(0),
      _idxFileSize(0)
{
    _datHeaderLen = _datHeader.readFile(*_datFile);
    _datFile->SetPosition(_datHeaderLen);
    if (!attr.headerTypeOK(_datHeader) ||
        !extractFileSize(_datHeader, *_datFile, _datFileSize)) {
        _datFile->Close();
    }
    _createSerialNum = extractCreateSerialNum(_datHeader);
    if (_datHeader.hasTag(versionTag)) {
        _version = _datHeader.getTag(versionTag).asInteger();
    }
    _docIdLimit = _datHeader.getTag(docIdLimitTag).asInteger();
    if (hasIdx()) {
        vespalib::FileHeader idxHeader(DIRECTIO_ALIGNMENT);
        _idxHeaderLen = idxHeader.readFile(*_idxFile);
        _idxFile->SetPosition(_idxHeaderLen);
        if (!attr.headerTypeOK(idxHeader) ||
            !extractFileSize(idxHeader, *_idxFile, _idxFileSize)) {
            _idxFile->Close();
        } else  {
            _currIdx = _idxReader.readHostOrder();
        }
    }
    if (hasWeight()) {
        vespalib::FileHeader weightHeader(DIRECTIO_ALIGNMENT);
        _weightHeaderLen = weightHeader.readFile(*_weightFile);
        _weightFile->SetPosition(_weightHeaderLen);
        if (!attr.headerTypeOK(weightHeader))
            _weightFile->Close();
    }
    if (hasData() && isEnumerated(_datHeader)) {
#if 1
        if (!allowEnumeratedLoad) {
            /*
             * Block loading of enumerated attribute vector files until we have
             * working unit tests in place.
             */
            vespalib::string s;
            s = vespalib::make_string("Attribute vector file '%s' is"
                                      " enumerated."
                                      " Install a newer version of vespa that"
                                      " supports enumerated"
                                      " attribute vector files, or ask" 
                                      " vespa team to help "
                                      " converting attribute vector to "
                                      " non-enumerated form.",
                                      _datFile->GetFileName());
            LOG(error, "%s", s.c_str());
            throw IllegalStateException(s);
        }
#endif
        _enumerated = true;
        _udatFile = attr.openUDAT();
        vespalib::FileHeader udatHeader(DIRECTIO_ALIGNMENT);
        _udatHeaderLen = udatHeader.readFile(*_udatFile);
        _udatFile->SetPosition(_udatHeaderLen);
        if (!attr.headerTypeOK(udatHeader))
            _udatFile->Close();
    }
    _hasLoadData = hasData() &&
                   (!attr.hasMultiValue() || hasIdx()) &&
                   (!attr.hasWeightedSetType() || hasWeight()) &&
                   (!getEnumerated() || hasUData());
}


AttributeVector::ReaderBase::~ReaderBase()
{
}


bool
AttributeVector::ReaderBase::
extractFileSize(const vespalib::GenericHeader &header,
                FastOS_FileInterface &file, uint64_t &fileSize)
{
    fileSize = file.GetSize();
    return FileSizeCalculator::extractFileSize(header, header.getSize(),
                                               file.GetFileName(), fileSize);
}


void
AttributeVector::ReaderBase::rewind()
{
    _datFile->SetPosition(_datHeaderLen);
    _currIdx = 0;
    if (hasIdx()) {
        _idxFile->SetPosition(_idxHeaderLen);
        _currIdx = _idxReader.readHostOrder();
    }
    if (hasWeight()) {
        _weightFile->SetPosition(_weightHeaderLen);
    }
    if (getEnumerated()) {
        _udatFile->SetPosition(_udatHeaderLen);
    }
}


size_t
AttributeVector::ReaderBase::getNumValues()
{
    if (getEnumerated()) {
       return getEnumCount();
    } else {
       if (_fixedWidth > 0) {
           size_t dataSize(_datFileSize - _datHeaderLen);
           assert((dataSize % _fixedWidth) == 0);
           return dataSize / _fixedWidth;
        } else {
            // TODO. This limits the number of multivalues to 2^32-1
            // This is assert during write, so this should never be a problem here.
            _idxFile->SetPosition(_idxFileSize - 4);
            size_t numValues = _idxReader.readHostOrder();
            rewind();
            return numValues;
        }
    }
}


uint32_t
 AttributeVector::ReaderBase::getNextValueCount()
{
    uint32_t nextIdx = _idxReader.readHostOrder();
    uint32_t numValues = nextIdx - _currIdx;
    _currIdx = nextIdx;
    return numValues;
}


FileUtil::LoadedBuffer::UP
AttributeVector::loadDAT()
{
    return loadFile(".dat");
}


FileUtil::LoadedBuffer::UP
AttributeVector::loadIDX()
{
    return loadFile(".idx");
}


FileUtil::LoadedBuffer::UP
AttributeVector::loadWeight()
{
    return loadFile(".weight");
}


FileUtil::LoadedBuffer::UP
AttributeVector::loadUDAT()
{
    return loadFile(".udat");
}


FileUtil::LoadedBuffer::UP
AttributeVector::loadFile(const char *suffix)
{
    BaseName::string fileName(getBaseFileName());
    fileName += suffix;
    return FileUtil::loadFile(fileName);
}


bool
AttributeVector::saveAs(const vespalib::stringref &baseFileName)
{
    _baseFileName = baseFileName;
    return save();
}

bool
AttributeVector::saveAs(const vespalib::stringref &baseFileName,
                        IAttributeSaveTarget & saveTarget)
{
    _baseFileName = baseFileName;
    return save(saveTarget);
}


bool
AttributeVector::save()
{
    TuneFileAttributes tune;
    DummyFileHeaderContext fileHeaderContext;
    AttributeFileSaveTarget saveTarget(tune, fileHeaderContext);
    return save(saveTarget);
}


bool
AttributeVector::save(IAttributeSaveTarget &saveTarget)
{
    commit();
    // First check if new style save is available.
    std::unique_ptr<AttributeSaver> saver(onInitSave());
    if (saver) {
        // Normally, new style save happens in background, but here it
        // will occur in the foreground.
        return saver->save(saveTarget);
    }
    // New style save not available, use old style save
    saveTarget.setConfig(createSaveTargetConfig());
    if (!saveTarget.setup()) {
        return false;
    }
    onSave(saveTarget);
    saveTarget.close();
    return true;
}


IAttributeSaveTarget::Config
AttributeVector::createSaveTargetConfig() const
{
    return IAttributeSaveTarget::Config(getBaseFileName(),
                                   getConfig().basicType().asString(),
                                   getConfig().collectionType().asString(),
                                   getConfig().basicType().type() ==
                                   BasicType::Type::TENSOR ?
                                   getConfig().tensorType().to_spec() :
                                   "",
                                   hasMultiValue(),
                                   hasWeightedSetType(),
                                   getEnumeratedSave(),
                                   getCommittedDocIdLimit(),
                                   getFixedWidth(),
                                   getUniqueValueCount(),
                                   getTotalValueCount(),
                                   getCreateSerialNum(),
                                   getVersion());
}


void
AttributeVector::onSave(IAttributeSaveTarget & saveTarget)
{
    (void) saveTarget;
    assert(false);
}


bool
AttributeVector::hasLoadData() const
{
    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(vespalib::make_string("%s.dat",
                                   getBaseFileName().c_str()).c_str(),
                           &statInfo)) {
        return false;
    }
    if (hasMultiValue() &&
        !FastOS_File::Stat(vespalib::make_string("%s.idx",
                                   getBaseFileName().c_str()).c_str(),
                           &statInfo))
    {
        return false;
    }
    if (hasWeightedSetType() &&
        !FastOS_File::Stat(vespalib::make_string("%s.weight",
                                   getBaseFileName().c_str()).c_str(),
                           &statInfo))
    {
        return false;
    }
    if (isEnumeratedSaveFormat() &&
        !FastOS_File::Stat(vespalib::make_string("%s.udat",
                                   getBaseFileName().c_str()).c_str(),
                           &statInfo))
    {
        return false;
    }
    return true;
}


bool
AttributeVector::isEnumeratedSaveFormat(void) const
{
    vespalib::string datName(vespalib::make_string("%s.dat",
                                                   getBaseFileName().c_str()));
    Fast_BufferedFile   datFile;
    vespalib::FileHeader datHeader(DIRECTIO_ALIGNMENT);
    if ( ! datFile.OpenReadOnly(datName.c_str()) ) {
        LOG(error, "could not open %s: %s",
            datFile.GetFileName(), getLastErrorString().c_str());
        throw IllegalStateException(
                vespalib::make_string(
                        "Failed opening attribute data file '%s' for reading",
                        datFile.GetFileName()));
    }
    datHeader.readFile(datFile);
    
    return isEnumerated(datHeader);
}


bool
AttributeVector::load() {
    bool loaded = onLoad();
    if (loaded) {
        commit();
    }
    _loaded = loaded;
    return _loaded;
}


bool
AttributeVector::onLoad()
{
    return false;
}


int32_t
AttributeVector::getWeight(DocId doc, uint32_t idx) const
{
    (void) doc;
    (void) idx;
    return 1;
}

AttributeVector::SearchContext::Params::Params() :
    _diversityAttribute(nullptr),
    _diversityCutoffGroups(std::numeric_limits<uint32_t>::max()),
    _useBitVector(false),
    _diversityCutoffStrict(false)
{
}

AttributeVector::SearchContext::SearchContext(const AttributeVector &attr) :
    _attr(attr),
    _plsc(NULL)
{
}

AttributeVector::SearchContext::UP
AttributeVector::getSearch(const QueryPacketT & searchSpec,
                           const SearchContext::Params & params) const
{
    return getSearch(SearchContext::decodeQuery(searchSpec), params);
}

AttributeVector::SearchContext::~SearchContext()
{
}


unsigned int
AttributeVector::SearchContext::approximateHits() const
{
    if (_plsc != NULL) {
        return _plsc->approximateHits();
    }
    return std::max(uint64_t(_attr.getNumDocs()),
                    _attr.getStatus().getNumValues());
}


QueryTermSimple::UP
AttributeVector::SearchContext::decodeQuery(const QueryPacketT &searchSpec)
{
    QueryTermSimple::UP qt;
    EmptyQueryNodeResult qnb;
    Query q(qnb, searchSpec);
    if (q.valid() && (dynamic_cast<QueryTerm *>(q.getRoot().get()))) {
        qt.reset(static_cast<QueryTerm *>(q.getRoot().release()));
    } else {
        throw IllegalStateException("Failed decoding query");
    }
    return qt;
}


SearchIterator::UP
AttributeVector::SearchContext::
createIterator(fef::TermFieldMatchData *matchData, bool strict)
{
    if (_plsc != NULL) {
        SearchIterator::UP res = 
            _plsc->createPostingIterator(matchData, strict);
        if (res.get() != NULL)
            return res;
    }
    return createFilterIterator(matchData, strict);
}


SearchIterator::UP
AttributeVector::SearchContext::
createFilterIterator(fef::TermFieldMatchData *matchData, bool strict)
{
    if (!valid())
        return SearchIterator::UP(new queryeval::EmptySearch());
    if (getIsFilter()) {
        return SearchIterator::UP(strict ?
            new FilterAttributeIteratorStrict<AttributeVector::SearchContext>
            (*this, matchData) :
            new FilterAttributeIteratorT<AttributeVector::SearchContext>
            (*this, matchData));
    }
    return SearchIterator::UP(strict ?
            new AttributeIteratorStrict<AttributeVector::SearchContext>
            (*this, matchData) :
            new AttributeIteratorT<AttributeVector::SearchContext>
            (*this, matchData));
}


void
AttributeVector::SearchContext::fetchPostings(bool strict)
{
    if (_plsc != NULL)
        _plsc->fetchPostings(strict);
}


bool
AttributeVector::apply(DocId doc, const MapValueUpdate &map)
{
    bool retval(doc < getNumDocs());
    if (retval) {
        const ValueUpdate & vu(map.getUpdate());
        if (vu.inherits(ArithmeticValueUpdate::classId)) {
            const ArithmeticValueUpdate &
                au(static_cast<const ArithmeticValueUpdate &>(vu));
            retval = applyWeight(doc, map.getKey(), au);
        } else {
            retval = false;
        }
    }
    return retval;
}


bool
AttributeVector::applyWeight(DocId, const FieldValue &,
                             const ArithmeticValueUpdate &)
{
    return false;
}


void
AttributeVector::removeAllOldGenerations()
{
    _genHandler.updateFirstUsedGeneration();
    removeOldGenerations(_genHandler.getFirstUsedGeneration());
}


void
AttributeVector::divideByZeroWarning()
{
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
AttributeVector::addReservedDoc(void)
{
    uint32_t docId = 42;
    addDoc(docId);		// Reserved
    assert(docId == 0u);
    assert(docId < getNumDocs());
    clearDoc(docId);
    commit();
    const vespalib::Identifiable::RuntimeClass &info = getClass();
    if (info.inherits(search::FloatingPointAttribute::classId)) {
        FloatingPointAttribute &vec =
            static_cast<FloatingPointAttribute &>(*this);
        if (hasMultiValue()) {
            bool appendedUndefined = vec.append(0, attribute::getUndefined<double>(), 1);
            assert(appendedUndefined);
            (void) appendedUndefined;
        } else {
            bool updatedUndefined = vec.update(0, attribute::getUndefined<double>());
            assert(updatedUndefined);
            (void) updatedUndefined;
        }
        commit();
    }
}


void
AttributeVector::enableEnumeratedSave(bool enable)
{
    if (hasEnum() || !enable)
        _enableEnumeratedSave = enable;
}


void
AttributeVector::enableEnumeratedLoad(void)
{
    allowEnumeratedLoad = true;
}


attribute::IPostingListAttributeBase *
AttributeVector::getIPostingListAttributeBase(void)
{
    return NULL;
}


bool
AttributeVector::hasPostings(void)
{
    return getIPostingListAttributeBase() != NULL;
}


uint64_t
AttributeVector::getUniqueValueCount(void) const
{
    return getTotalValueCount();
}


uint64_t
AttributeVector::getTotalValueCount(void) const
{
    return getNumDocs();
}


void
AttributeVector::setCreateSerialNum(uint64_t createSerialNum)
{
    _createSerialNum = createSerialNum;
}


uint64_t
AttributeVector::getCreateSerialNum(void) const
{
    return _createSerialNum;
}

uint32_t
AttributeVector::getVersion() const {
    return 0;
}

void
AttributeVector::compactLidSpace(uint32_t wantedLidLimit)
{
    commit();
    assert(_uncommittedDocIdLimit <= wantedLidLimit);
    if (wantedLidLimit < _committedDocIdLimit) {
        clearDocs(wantedLidLimit, _committedDocIdLimit);
    }
    commit();
    _committedDocIdLimit = wantedLidLimit;
    _compactLidSpaceGeneration = _genHandler.getCurrentGeneration();
    incGeneration();
}


bool
AttributeVector::canShrinkLidSpace(void) const
{
    return wantShrinkLidSpace() &&
        _compactLidSpaceGeneration < getFirstUsedGeneration();
}


void
AttributeVector::shrinkLidSpace(void)
{
    commit();
    assert(canShrinkLidSpace());
    uint32_t committedDocIdLimit = _committedDocIdLimit;
    clearDocs(committedDocIdLimit, getNumDocs());
    commit();
    _committedDocIdLimit = committedDocIdLimit;
    onShrinkLidSpace();
    attribute::IPostingListAttributeBase *pab = getIPostingListAttributeBase();
    if (pab != NULL) {
        pab->forwardedShrinkLidSpace(_committedDocIdLimit);
    }
    incGeneration();
    updateStat(true);
}


void
AttributeVector::onShrinkLidSpace(void)
{
}


void
AttributeVector::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        clearDoc(lid);
    }
}

AttributeVector::EnumModifier
AttributeVector::getEnumModifier()
{
    attribute::InterlockGuard interlockGuard(*_interlock);
    return EnumModifier(_enumLock, interlockGuard);
}


void
AttributeVector::setInterlock(const std::shared_ptr<attribute::Interlock> &
                              interlock)
{
    _interlock = interlock;
}


std::unique_ptr<AttributeSaver>
AttributeVector::initSave()
{
    commit();
    return onInitSave();
}

std::unique_ptr<AttributeSaver>
AttributeVector::onInitSave()
{
    return std::unique_ptr<AttributeSaver>();
}


IExtendAttribute *
AttributeVector::getExtendInterface()
{
    return nullptr;
}

uint64_t
AttributeVector::getEstimatedSaveByteSize() const
{
    uint64_t headerSize = 4096;
    uint64_t totalValueCount = getTotalValueCount();
    uint64_t uniqueValueCount = getUniqueValueCount();
    uint64_t docIdLimit = getCommittedDocIdLimit();
    uint64_t datFileSize = 0;
    uint64_t weightFileSize = 0;
    uint64_t idxFileSize = 0;
    uint64_t udatFileSize = 0;
    AddressSpace enumAddressSpace(getEnumStoreAddressSpaceUsage());

    if (hasMultiValue()) {
        idxFileSize = headerSize + sizeof(uint32_t) * (docIdLimit + 1);
    }
    if (hasWeightedSetType()) {
        weightFileSize = headerSize + sizeof(int32_t) * totalValueCount;
    }
    if (hasEnum() && getEnumeratedSave()) {
        datFileSize =  headerSize + 4 * totalValueCount;
        udatFileSize = headerSize + enumAddressSpace.used()
                       - 8 * uniqueValueCount;
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
            assert(hasEnum());
            datFileSize = headerSize;
            if (uniqueValueCount > 0) {
                double avgEntrySize = (static_cast<double>(enumAddressSpace.used()) / uniqueValueCount) - 8;
                datFileSize += avgEntrySize * totalValueCount;
            }
            break;
        default:
            datFileSize = headerSize + getFixedWidth() * totalValueCount;
            break;
        }
    }
    return datFileSize + weightFileSize + idxFileSize + udatFileSize;
}


template bool AttributeVector::append<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &, int32_t, bool);
template bool AttributeVector::update<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &);
template bool AttributeVector::remove<StringChangeData>(ChangeVectorT< ChangeTemplate<StringChangeData> > &changes, uint32_t , const StringChangeData &, int32_t);

}
