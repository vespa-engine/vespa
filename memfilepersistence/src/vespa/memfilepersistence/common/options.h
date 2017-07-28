// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::Options
 * @ingroup filestorage
 *
 * @brief Options used by slotfiles
 *
 * To avoid the need for static variables which cannot be altered while the
 * system is running, and which forces all slotfile instances to work with the
 * same options, this options class has been created to contain all the options
 * a slotfile will use.
 *
 * @author H�kon Humberset
 * @date 2005-10-26
 */

#pragma once

#include <vespa/config-stor-memfilepersistence.h>
#include <vespa/config-persistence.h>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/storageframework/generic/clock/time.h>

namespace storage::memfile {

struct Options : public vespalib::Printable
{
    // Parameters from def file. See config file for comments.

    // FILE SIZE PARAMETERS

    uint32_t _minimumFileMetaSlots;
    uint32_t _maximumFileMetaSlots;
    uint32_t _minimumFileHeaderBlockSize;
    uint32_t _maximumFileHeaderBlockSize;
    uint32_t _minimumFileSize;
    uint32_t _maximumFileSize;
    uint32_t _fileBlockSize;

    // CONSISTENCY PARAMETERS
    framework::MicroSecTime _revertTimePeriod;
    framework::MicroSecTime _keepRemoveTimePeriod;
    uint32_t _maxDocumentVersions;

    // PERFORMANCE PARAMETERS
    uint64_t _cacheSize;
    uint32_t _initialIndexRead;
    uint32_t _maximumGapToReadThrough;

    double _diskFullFactor;
    double _growFactor;
    double _overrepresentMetaDataFactor;
    double _overrepresentHeaderBlockFactor;

    // COMPATIBILITY PARAMETERS
    // If non-empty, will cause remove entries to be written with a blank
    // document containing only the document type and identifier rather than
    // just writing a document id with no document at all. Note that if a
    // document identifier contains a type string it will override this default
    // value.
    // This is a feature for backwards compatibility with 5.0, as it chokes
    // when trying to read remove entries without a document.
    vespalib::string _defaultRemoveDocType;

    /**
     * Creates a new slotfile options instance. Implemented in header file,
     * such that the current defaults can be easily viewed.
     */
    Options();
    Options(const Options &) noexcept;
    Options & operator=(const Options &);
    ~Options();

    Options(const vespa::config::storage::StorMemfilepersistenceConfig& newConfig,
            const vespa::config::content::PersistenceConfig& newPersistenceConfig);

    void validate() const { const_cast<Options&>(*this).validate(); }
    void validate();

    /** Printable implementation */
    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

    bool operator==(const Options& options) const;
};

}
