// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "indexwriteutilities.h"
#include "indexdisklayout.h"
#include "indexreadutilities.h"
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <filesystem>
#include <sstream>
#include <system_error>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexwriteutilities");


using search::FixedSourceSelector;
using search::TuneFileAttributes;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using search::index::Schema;
using search::index::SchemaUtil;
using search::SerialNum;
using vespalib::IllegalStateException;
using vespalib::FileHeader;

namespace searchcorespi::index {

namespace {

SerialNum noSerialNumHigh = std::numeric_limits<SerialNum>::max();

}

void
IndexWriteUtilities::writeSerialNum(SerialNum serialNum,
                                    const vespalib::string &dir,
                                    const FileHeaderContext &fileHeaderContext)
{
    const vespalib::string fileName =
        IndexDiskLayout::getSerialNumFileName(dir);
    const vespalib::string tmpFileName = fileName + ".tmp";

    SerialNumFileHeaderContext snFileHeaderContext(fileHeaderContext, serialNum);
    Fast_BufferedFile file;
    file.WriteOpen(tmpFileName.c_str());
    FileHeader fileHeader;
    snFileHeaderContext.addTags(fileHeader, fileName);
    fileHeader.putTag(FileHeader::Tag(IndexDiskLayout::SerialNumTag, serialNum));
    bool ok = (fileHeader.writeFile(file) >= fileHeader.getSize());
    if ( ! ok) {
        LOG(error, "Unable to write file header '%s'", tmpFileName.c_str());
    }
    if ( ! file.Sync()) {
        ok = false;
        LOG(error, "Unable to fsync '%s'", tmpFileName.c_str());
    }
    if ( ! file.Close()) {
        ok = false;
        LOG(error, "Unable to close '%s'", tmpFileName.c_str());
    }
    vespalib::File::sync(dir);

    if (ok) {
        FastOS_File renameFile(tmpFileName.c_str());
        ok &= renameFile.Rename(fileName.c_str());
    }
    if (!ok) {
        std::ostringstream msg;
        msg << "Unable to write serial number to '" << dir << "'.";
        throw IllegalStateException(msg.str());
    }
    vespalib::File::sync(dir);
}

bool
IndexWriteUtilities::copySerialNumFile(const vespalib::string &sourceDir,
                                       const vespalib::string &destDir)
{
    vespalib::string source = IndexDiskLayout::getSerialNumFileName(sourceDir);
    vespalib::string dest = IndexDiskLayout::getSerialNumFileName(destDir);
    vespalib::string tmpDest = dest + ".tmp";
    std::error_code ec;

    std::filesystem::copy_file(std::filesystem::path(source), std::filesystem::path(tmpDest), ec);
    if (ec) {
        LOG(error, "Unable to copy file '%s'", source.c_str());
        return false;
    }
    FastOS_File file(tmpDest.c_str());
    if (!file.OpenReadWrite()) {
        LOG(error, "Unable to open '%s' for fsync", tmpDest.c_str());
        return false;
    }
    if (!file.Sync()) {
        LOG(error, "Unable to fsync '%s'", tmpDest.c_str());
        return false;
    }
    if (!file.Close()) {
        LOG(error, "Unable to close '%s'", tmpDest.c_str());
        return false;
    }
    vespalib::File::sync(destDir);
    if (!file.Rename(dest.c_str())) {
        LOG(error, "Unable to rename file '%s' to '%s'", tmpDest.c_str(), dest.c_str());
        return false;
    }
    vespalib::File::sync(destDir);
    return true;
}

void
IndexWriteUtilities::writeSourceSelector(FixedSourceSelector::SaveInfo &
                                         saveInfo,
                                         uint32_t sourceId,
                                         const TuneFileAttributes &
                                         tuneFileAttributes,
                                         const FileHeaderContext &
                                         fileHeaderContext,
                                         SerialNum serialNum)
{
    SerialNumFileHeaderContext snFileHeaderContext(fileHeaderContext,
                                                   serialNum);
    if (!saveInfo.save(tuneFileAttributes, snFileHeaderContext)) {
        std::ostringstream msg;
        msg << "Flush of sourceselector failed. Source id = " << sourceId;
        throw IllegalStateException(msg.str());
    }
}

void
IndexWriteUtilities::updateDiskIndexSchema(const vespalib::string &indexDir,
                                           const Schema &schema,
                                           SerialNum serialNum)
{
    vespalib::string schemaName = IndexDiskLayout::getSchemaFileName(indexDir);
    Schema oldSchema;
    if (!oldSchema.loadFromFile(schemaName)) {
        LOG(error, "Could not open schema '%s'",
            schemaName.c_str());
        return;
    }
    if (!SchemaUtil::validateSchema(oldSchema)) {
        LOG(error, "Could not validate schema loaded from '%s'",
            schemaName.c_str());
        return;
    }
    Schema::UP newSchema = Schema::intersect(oldSchema, schema);
    if (*newSchema == oldSchema) {
        return;
    }
    if (serialNum != noSerialNumHigh) {
        SerialNum oldSerial = IndexReadUtilities::readSerialNum(indexDir);
        if (oldSerial >= serialNum) {
            return;
        }
    }
    vespalib::string schemaTmpName = schemaName + ".tmp";
    vespalib::string schemaOrigName = schemaName + ".orig";
    vespalib::unlink(schemaTmpName);
    if (!newSchema->saveToFile(schemaTmpName)) {
        LOG(error, "Could not save schema to '%s'",
            schemaTmpName.c_str());
    }
    // XXX: FastOS layer violation
    FastOS_StatInfo statInfo;
    bool statres;
    statres = FastOS_File::Stat(schemaOrigName.c_str(), &statInfo);
    if (!statres) {
        if (statInfo._error != FastOS_StatInfo::FileNotFound) {
            LOG(error, "Failed to stat orig schema '%s': %s",
                schemaOrigName.c_str(),
                FastOS_File::getLastErrorString().c_str());
        }
        int linkres = ::link(schemaName.c_str(), schemaOrigName.c_str());
        if (linkres != 0) {
            LOG(error, "Could not link '%s' to '%s': %s",
                schemaOrigName.c_str(),
                schemaName.c_str(),
                FastOS_File::getLastErrorString().c_str());
        }
        vespalib::File::sync(indexDir);
    }
    // XXX: FastOS layer violation
    int renameres = ::rename(schemaTmpName.c_str(), schemaName.c_str());
    if (renameres != 0) {
        int error = errno;
        std::string errString = FastOS_File::getErrorString(error);
        LOG(error, "Could not rename '%s' to '%s': %s",
            schemaTmpName.c_str(),
            schemaName.c_str(),
            errString.c_str());
    }
    vespalib::File::sync(indexDir);
}

}
