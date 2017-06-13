// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "options.h"
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.slotfile.options");

namespace storage::memfile {

Options::Options(const vespa::config::storage::StorMemfilepersistenceConfig& newConfig,
                 const vespa::config::content::PersistenceConfig& newPersistenceConfig)
    : _minimumFileMetaSlots(newConfig.minimumFileMetaSlots),
      _maximumFileMetaSlots(newConfig.maximumFileMetaSlots),
      _minimumFileHeaderBlockSize(newConfig.minimumFileHeaderBlockSize),
      _maximumFileHeaderBlockSize(newConfig.maximumFileHeaderBlockSize),
      _minimumFileSize(newConfig.minimumFileSize),
      _maximumFileSize(newConfig.maximumFileSize),
      _fileBlockSize(newConfig.fileBlockSize),
      _revertTimePeriod(newPersistenceConfig.revertTimePeriod * 1000000ll),
      _keepRemoveTimePeriod(
            newPersistenceConfig.keepRemoveTimePeriod * 1000000ll),
      _maxDocumentVersions(
            newPersistenceConfig.maximumVersionsOfSingleDocumentStored),
      _cacheSize(newConfig.cacheSize),
      _initialIndexRead(newConfig.initialIndexRead),
      _maximumGapToReadThrough(newConfig.maximumGapToReadThrough),
      _diskFullFactor(newConfig.diskFullFactor),
      _growFactor(newConfig.growFactor),
      _overrepresentMetaDataFactor(newConfig.overrepresentMetaDataFactor),
      _overrepresentHeaderBlockFactor(newConfig.overrepresentHeaderBlockFactor),
      _defaultRemoveDocType(
            newConfig.store50BackwardsCompatibleRemoveEntriesWithDoctype)
{
    validate();
}

namespace {
    template<typename Number>
    void verifyAligned(Number n, uint32_t alignSize, const char* name) {
        if (n % alignSize != 0) {
            std::ostringstream ost;
            ost << name << " " << n
                << " must be dividable by block alignment size " << alignSize;
            throw vespalib::IllegalStateException(
                    ost.str(), VESPA_STRLOC);
        }
    }
}

void Options::validate()
{
    uint32_t tmp32 = 0;

    // REVERT / KEEP REMOVE TIME PERIODS
    if (_revertTimePeriod > _keepRemoveTimePeriod) {
        LOG(warning, "Keep all time period (%" PRIu64 ") is set larger than keep "
                     "removes time period (%" PRIu64 ". Adjusting keep removes "
                     "period to match",
            _revertTimePeriod.getTime(), _keepRemoveTimePeriod.getTime());
        _keepRemoveTimePeriod = _revertTimePeriod;
    }
    if (_maxDocumentVersions < 1) {
        LOG(warning, "Max number of document versions attempted set to 0. "
                     "This is a bad idea for all the obvious reasons. Forcing "
                     "used value to be 1.");
        _maxDocumentVersions = 1;
    }
        // MINIMUM FILE SIZES
    if (_minimumFileMetaSlots < 1) {
        LOG(warning, "Minimum file meta slots is not allowed to be less than "
                     "1. Setting it to 1.");
        _minimumFileMetaSlots = 1;
    }
    if (_minimumFileMetaSlots > 1024*1024) {
        LOG(warning, "Minimum file meta slots is not allowed to be more than "
                     "%u. Setting it to %u.", 1024*1024, 1024*1024);
        _minimumFileMetaSlots = 1024*1024;
    }
    if (_minimumFileHeaderBlockSize > 2*1024*1024*1024u) {
        LOG(warning, "Minimum file header block size is not allowed to be above"
                     " 2 GB. Altering it from %u B to 2 GB.",
            _minimumFileHeaderBlockSize);
        _minimumFileHeaderBlockSize = 2*1024*1024*1024u;
    }
    if (_minimumFileSize % _fileBlockSize != 0) {
        tmp32 = _fileBlockSize
              * ((_minimumFileSize + _fileBlockSize - 1) / _fileBlockSize);
        LOG(warning, "Min file size %u not a multiplum of file block size %u. "
                     "Increasing minimum filesize to %u to match.",
            _minimumFileSize, _fileBlockSize, tmp32);
        _minimumFileSize = tmp32;
    }
        // MAXIMUM FILE SIZES
    if (_maximumFileMetaSlots != 0
        && _maximumFileMetaSlots < _minimumFileMetaSlots)
    {
        LOG(warning, "Maximum file meta slots cannot be less than the minimum. "
                     "Adjusting it from %u to %u.",
            _maximumFileMetaSlots, _minimumFileMetaSlots);
        _maximumFileMetaSlots = _minimumFileMetaSlots;
    }
    if (_maximumFileHeaderBlockSize != 0
        && _maximumFileHeaderBlockSize < _minimumFileHeaderBlockSize)
    {
        LOG(warning, "Maximum file header block size cannot be less than the "
                     "minimum. Adjusting it from %u to %u.",
            _maximumFileHeaderBlockSize, _minimumFileHeaderBlockSize);
        _maximumFileHeaderBlockSize = _minimumFileHeaderBlockSize;
    }
    if (_maximumFileSize != 0 && _maximumFileSize < _minimumFileSize) {
        LOG(warning, "Maximum file size cannot be less than the "
                     "minimum. Adjusting it from %u to %u.",
            _maximumFileSize, _minimumFileSize);
        _maximumFileSize = _minimumFileSize;
    }
    if (_maximumFileSize % _fileBlockSize != 0) {
        tmp32 = _fileBlockSize
                * ((_maximumFileSize + _fileBlockSize - 1) / _fileBlockSize);
        LOG(warning, "Max file size %u not a multiplum of file block size %u. "
                     "Increasing maximum to %u bytes to match.",
            _maximumFileSize, _fileBlockSize, tmp32);
        _maximumFileSize = tmp32;
    }

    if (_growFactor < 1.0 || _growFactor >= 100.0) {
        throw vespalib::IllegalStateException(
                "The grow factor needs to be in the range [1, 100].",
                VESPA_STRLOC);
    }

    if (!_defaultRemoveDocType.empty()) {
        // Log the usage of this option to make it visible, as it is not
        // something most people should use.
        LOG(info,
            "Will write remove entries in 5.0 backwards compatible mode. By "
            "default this will be done using the '%s' document type unless "
            "the document identifier specifies otherwise.",
            _defaultRemoveDocType.c_str());
    }
}

void Options::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    (void) verbose;
    std::string s("\n" + indent + "  ");

    out << "SlotFile options:"
        << s << "Minimum file meta slots: " << _minimumFileMetaSlots
        << s << "Maximum file meta slots: " << _maximumFileMetaSlots
        << s << "Minimum file header block size: "
             << _minimumFileHeaderBlockSize << " b"
        << s << "Maximum file header block size: "
             << _maximumFileHeaderBlockSize << " b"
        << s << "Minimum file size: " << _minimumFileSize << " b"
        << s << "Maximum file size: " << _maximumFileSize << " b"
        << s << "Filesystem block size: " << _fileBlockSize << " b"
        << s << "Revert time period: " << _revertTimePeriod << " microsecs"
        << s << "Keep remove time period: "
             << _keepRemoveTimePeriod << "microsecs"
        << s << "Max document versions: " << _maxDocumentVersions
        << s << "Cache size: " << _cacheSize
        << s << "Initial index read: " << _initialIndexRead << " b"
        << s << "Maximum gap to read through: "
             << _maximumGapToReadThrough << " b"
        << s << "Disk full factor: " << _diskFullFactor
        << s << "Grow factor: " << _growFactor
        << s << "Overrepresent meta data factor: "
             << _overrepresentMetaDataFactor
        << s << "Overrepresent header block factor: "
             << _overrepresentHeaderBlockFactor
        << s << "Write removes with blank documents of default type: "
             << _defaultRemoveDocType
        << "";
}

}
