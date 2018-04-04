// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dumpslotfile.h"
#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/document.h>
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovidermetrics.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/subscription/configuri.h>

using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using document::DocumentTypeRepo;

namespace storage::memfile {

namespace {
    std::ostream* cout;
    std::ostream* cerr;

    struct CmdOptions : public vespalib::ProgramOptions {
        bool showSyntaxPage;
        bool userFriendlyOutput;
        bool printHeader;
        bool printBody;
        bool toXml;
        bool toBinary;
        bool includeRemovedDocs;
        bool includeRemoveEntries;
//        std::string metaDataSort;
        std::string documentManConfigId;
        std::string filename;
        uint64_t timestampToShow;
        std::string docId;
//        bool useConstructor;

        CmdOptions(int argc, const char* const* argv);
        ~CmdOptions();

    };

    CmdOptions::CmdOptions(int argc, const char* const* argv)
        : vespalib::ProgramOptions(argc, argv),
          showSyntaxPage(false)
    {
        setSyntaxMessage(
                "Utility program for showing the contents of the slotfiles "
                        "used by Vespa Document Storage in a user readable format. "
                        "Intended for debugging purposes."
        );
        addOption("h help", showSyntaxPage, false,
                  "Shows this help page");
        addOption("n noheader", printHeader, true,
                  "If given, the header block content is not shown");
        addOption("N nobody", printBody, true,
                  "If given, the body block content is not shown");
        addOption("f friendly", userFriendlyOutput, false,
                  "Gives less compact, but more user friendly output");
        addOption("x toxml", toXml, false,
                  "Print document XML of contained documents");
        addOption("b tobinary", toBinary, false,
                  "Print binary representations of contained documents");
        addOption("includeremoveddocs", includeRemovedDocs, false,
                  "When showing XML, include documents that are still in "
                          "the file, but have been removed.");
        addOption("includeremoveentries", includeRemoveEntries, false,
                  "When showing XML, include remove entries.");
        addOption("c documentconfig", documentManConfigId,
                  std::string("client"),
                  "The document config to use, needed if deserializing "
                          "documents.");
//            addOption("s sort", metaDataSort, std::string("none"),
//                      "How to sort metadatalist. Valid arguments: "
//                      "bodypos, headerpos & none.");
        addOption("t time", timestampToShow, uint64_t(0),
                  "If set, only present data related to this timestamp, "
                          "when outputting XML or binary data.");
        addOption("docid", docId, std::string(""),
                  "Retrieve single document using get semantics");
//            addOption("useconstructor", useConstructor, false, "Debug option");
        addArgument("slotfile", filename, "The slotfile to dump.");
    }
    CmdOptions::~CmdOptions() { }

    void printDoc(document::Document& doc, CmdOptions& o) {
        if (o.toXml) {
            *cout << doc.toXml() << "\n";
        } else {
            document::ByteBuffer::UP bbuf(doc.serialize());
            *cout << std::string(bbuf->getBuffer(), bbuf->getLength());
        }
    }

    void printFailure(const std::string& failure) {
        *cerr << failure << "\n";
    }

    uint64_t extractBucketId(const std::string& path) {
        size_t slashPos = path.find_last_of('/');
        bool foundSlash = true;
        if (slashPos == std::string::npos) {
            foundSlash = false;
        }
        
        size_t dotPos = path.find_last_of('.');
        if (dotPos == std::string::npos 
            || (foundSlash && (slashPos > dotPos)))
        {
            dotPos = path.size();
        }

        std::string bucketIdAsHex;
        if (foundSlash) {
            bucketIdAsHex.assign(path.begin() + slashPos + 1,
                                 path.begin() + dotPos);
        } else {
            bucketIdAsHex.assign(path.begin(),
                                 path.begin() + dotPos);
        }

        char* endp;
        uint64_t bucketId = strtoull(bucketIdAsHex.c_str(), &endp, 16);
        if (*endp != '\0') {
            return 0;
        }
        return bucketId;
    }

    struct EnvironmentImpl : ThreadMetricProvider {
        framework::defaultimplementation::ComponentRegisterImpl _compReg;
        framework::Component _component;
        framework::defaultimplementation::RealClock _clock;
        MemFilePersistenceMetrics _metrics;
        MemFilePersistenceThreadMetrics* _threadMetrics;
        std::unique_ptr<MemFileCache> _cache;
        MemFileMapper _mapper;
        DeviceManager _deviceManager;
        document::DocumentType _docType;
        std::shared_ptr<const DocumentTypeRepo> _repo;
        vespa::config::storage::StorMemfilepersistenceConfigBuilder _memFileConfig;
        vespa::config::content::PersistenceConfigBuilder _persistenceConfig;
        vespa::config::storage::StorDevicesConfigBuilder _deviceConfig;
        config::ConfigSet _configSet;
        config::IConfigContext::SP _configContext;
        std::unique_ptr<config::ConfigUri> _internalConfig;
        std::unique_ptr<Environment> _env;

        EnvironmentImpl(config::ConfigUri& externalConfig,
                        const char* documentConfigId);
        ~EnvironmentImpl();

        MemFilePersistenceThreadMetrics& getMetrics() const override {
            return *_threadMetrics;
        }

    };

    EnvironmentImpl::EnvironmentImpl(config::ConfigUri& externalConfig, const char* documentConfigId)
        : _compReg(),
        _component(_compReg, "dumpslotfile"),
        _clock(),
        _metrics(_component),
        _threadMetrics(_metrics.addThreadMetrics()),
        _cache(),
        _mapper(*this),
        _deviceManager(DeviceMapper::UP(new SimpleDeviceMapper), _clock),
        _docType("foo", 1)
    {
        _compReg.setClock(_clock);
        _cache.reset(new MemFileCache(_compReg, _metrics._cache));
        if (documentConfigId == 0) {
            _repo.reset(new DocumentTypeRepo(_docType));
        } else {
            config::ConfigUri uri(
                    externalConfig.createWithNewId(documentConfigId));
            std::unique_ptr<document::DocumenttypesConfig> config(
                    ConfigGetter<DocumenttypesConfig>::getConfig(
                            uri.getConfigId(), uri.getContext()));
            _repo.reset(new DocumentTypeRepo(*config));
        }
        _deviceConfig.rootFolder = ".";
        std::string configId("defaultId");
        _configSet.addBuilder(configId, &_memFileConfig);
        _configSet.addBuilder(configId, &_persistenceConfig);
        _configSet.addBuilder(configId, &_deviceConfig);
        _configContext.reset(new config::ConfigContext(_configSet));
        _internalConfig.reset(
                new config::ConfigUri(configId, _configContext));
        _env.reset(new Environment(
                *_internalConfig, *_cache, _mapper, *_repo, _clock, true));
    }
    EnvironmentImpl::~EnvironmentImpl() {}

}

int SlotFileDumper::dump(int argc, const char * const * argv,
                         config::ConfigUri& config,
                         std::ostream& out, std::ostream& err)
{
    cout = &out;
    cerr = &err;
    CmdOptions o(argc, argv);
    try{
        o.parse();
    } catch (vespalib::InvalidCommandLineArgumentsException& e) {
        if (!o.showSyntaxPage) {
            err << e.getMessage() << "\n\n";
            o.writeSyntaxPage(err);
            err << "\n";
            return 1;
        }
    }
    if (o.showSyntaxPage) {
        o.writeSyntaxPage(err);
        err << "\n";
        return 0;
    }
    if (!o.toXml && (o.includeRemovedDocs || o.includeRemoveEntries)) {
        err << "Options for what to include in XML makes no sense when "
               "not printing XML content.\n\n";
        o.writeSyntaxPage(err);
        err << "\n";
        return 1;
    }
    if (o.toBinary && o.timestampToShow == 0 && o.docId == "") {
        err << "To binary option only works for a single document. "
                     "Use --time or --docid options.\n\n";
        o.writeSyntaxPage(err);
        err << "\n";
        return 1;
    }
//    if (o.metaDataSort != "none" && o.metaDataSort != "bodypos") {
//        err << "Illegal value for metadata sorting: '" << o.metaDataSort
//                  << "'. Legal values are:\n"
//                  << "  none      - Keep order on disk (currently timestamp)\n"
//                  << "  bodypos   - Reorder metadata by position of body\n"
//                  << "  headerpos - Reorder metadata by position of header\n\n";
//        o.writeSyntaxPage(err);
//        err << "\n";
//        return 1;
//    }

    EnvironmentImpl env(config, o.toXml ? o.documentManConfigId.c_str() : "");

    document::BucketId bucket(extractBucketId(o.filename));
    Directory::SP dir(env._deviceManager.getDirectory(o.filename, 0));
    FileSpecification fileSpec(bucket, *dir, o.filename);

    MemFile::LoadOptions opts;
    opts.autoRepair = false;
    MemFile memFile(fileSpec, *env._env, opts);

    if (!o.toXml && !o.toBinary) {
        spi::BucketInfo info;
        info = memFile.getBucketInfo();
        if (bucket.getRawId() == 0) {
            out << "Failed to extract bucket id from filename\n";
        } else {
            out << bucket << " (extracted from filename)\n";
        }
        out << "Unique document count: " << info.getDocumentCount()
            << "\nTotal document size: " 
            << info.getDocumentSize() << "\n";
        out << "Used size: " << info.getUsedSize() << "\n";
        out << "Entry count: " << info.getEntryCount() << "\n";

/*
        SlotFile::MetaDataOrder order = SlotFile::DEFAULT;
        if (o.metaDataSort == "bodypos") {
            order = SlotFile::BODYPOS;
        } else if (o.metaDataSort == "headerpos") {
            order = SlotFile::HEADERPOS;
        }
*/
        memFile.printState(out, o.userFriendlyOutput, o.printBody,
                           o.printHeader/*, order*/);
        out << "\n";
        std::ostringstream ost;
        uint16_t verifyFlags = 0; // May verify only header/body
        if (env._mapper.verify(memFile, *env._env, ost, verifyFlags)) {
            out << "Slotfile verified.\n";
        } else {
            out << "Slotfile failed verification.\n";
            out << ost.str() << "\n";
        }
    } else {
        std::ostringstream ost;
        uint16_t verifyFlags = 0; // May verify only header/body
        if (!env._mapper.verify(memFile, *env._env, ost, verifyFlags)) {
            out << "Slotfile failed verification.\n";
            out << ost.str() << "\n";
            return 1;
        }

        if (o.toXml) {
            out << "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
            out << "<vespafeed>\n";
        }
        if (o.docId != "") {
            const MemSlot* slot(
                    memFile.getSlotWithId(document::DocumentId(o.docId)));
            if (slot != 0 && !slot->deleted()) {
                document::Document::UP doc(memFile.getDocument(*slot,
                                o.printBody ? 
                                Types::ALL : Types::HEADER_ONLY));
                if (doc.get()) {
                    printDoc(*doc, o);
                } else {
                    printFailure("No document with id " + o.docId + 
                            " found.");
                }
            } else {
                printFailure("No document with id " + o.docId + " found.");
            }
        } else {
            uint32_t iteratorFlags = o.includeRemoveEntries ?
                                     Types::ITERATE_REMOVED : 0;
            if (!o.includeRemovedDocs) {
                iteratorFlags |= Types::ITERATE_GID_UNIQUE;
            }
            for (MemFile::const_iterator it = memFile.begin(iteratorFlags);
                 it != memFile.end(); ++it)
            {
                if (o.timestampToShow == 0
                    || (Types::Timestamp)o.timestampToShow 
                        == it->getTimestamp())
                {
                    if (it->deleted() || it->deletedInPlace()) {
                        printFailure("Found remove entry");
                    } else {
                        document::Document::UP doc(memFile.getDocument(*it,
                                        o.printBody ? 
                                        Types::ALL : Types::HEADER_ONLY));
                        if (doc.get()) {
                            printDoc(*doc, o);
                        } else {
                            printFailure("Unable to get document in " + it->toString(true));
                        }
                    }
                }
            }
        }
        if (o.toXml) {
            out << "</vespafeed>\n";
        }
    }
    return 0;
}

}
