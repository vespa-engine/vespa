// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/replaypacketdispatcher.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/fastos/app.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-transactionlog-inspect");

using namespace proton;
using namespace search;
using namespace search::common;
using namespace search::transactionlog;

using document::DocumenttypesConfig;
using document::DocumentTypeRepo;

typedef std::shared_ptr<DocumenttypesConfig> DocumenttypesConfigSP;
typedef std::unique_ptr<IReplayPacketHandler> IReplayPacketHandlerUP;

struct DummyFileHeaderContext : public FileHeaderContext
{
    typedef std::unique_ptr<DummyFileHeaderContext> UP;
    virtual void addTags(vespalib::GenericHeader &, const vespalib::string &) const override {}
};


namespace {

class ConfigFile
{
    typedef std::shared_ptr<ConfigFile> SP;

    vespalib::string _name;
    time_t _modTime;
    std::vector<char> _content;

public:
    ConfigFile();

    const vespalib::string &
    getName() const
    {
        return _name;
    }

    vespalib::nbostream &
    deserialize(vespalib::nbostream &stream);

    void
    print() const;
};


ConfigFile::ConfigFile()
    : _name(),
      _modTime(0),
      _content()
{
}


vespalib::nbostream &
ConfigFile::deserialize(vespalib::nbostream &stream)
{
    stream >> _name;
    assert(strchr(_name.c_str(), '/') == NULL);
    stream >> _modTime;
    uint32_t sz;
    stream >> sz;
    _content.resize(sz);
    assert(stream.size() >= sz);
    memcpy(&_content[0], stream.peek(), sz);
    stream.adjustReadPos(sz);
    return stream;
}

void
ConfigFile::print() const
{
    std::cout << "Name: " << _name << "\n" <<
        "ModTime: " << _modTime << "\n" <<
        "Content-Length: " << _content.size() << "\n\n";
    std::cout.write(&_content[0], _content.size()); 
    std::cout << "\n-----------------------------" << std::endl;
}

vespalib::nbostream &
operator>>(vespalib::nbostream &stream, ConfigFile &configFile)
{
    return configFile.deserialize(stream);
}


}

struct DummyStreamHandler : public NewConfigOperation::IStreamHandler {
    std::map<std::string, ConfigFile> _cfs;

    DummyStreamHandler()
        : NewConfigOperation::IStreamHandler(),
          _cfs()
    {
    }

    virtual void
    serializeConfig(SerialNum, vespalib::nbostream &) override
    {
    }

    virtual void
    deserializeConfig(SerialNum, vespalib::nbostream &is) override
    {
        _cfs.clear();
        uint32_t numConfigs;
        is >> numConfigs;
        for (uint32_t i = 0; i < numConfigs; ++i) {
            ConfigFile cf;
            is >> cf;
            _cfs[cf.getName()] = cf;
        }
        assert(is.size() == 0);
    }
};

struct DocTypeRepo {
    DocumenttypesConfigSP docTypeCfg;
    DocumentTypeRepo docTypeRepo;
    DocTypeRepo(const std::string &configDir);
    ~DocTypeRepo();
};

DocTypeRepo::DocTypeRepo(const std::string &configDir)
    : docTypeCfg(config::ConfigGetter<DocumenttypesConfig>::getConfig("", config::DirSpec(configDir)).release()),
      docTypeRepo(*docTypeCfg)
{
}

DocTypeRepo::~DocTypeRepo() {}


/**
 * Class the receives all concrete operations as part of a domain visit
 * and prints the content of them to standard out.
 */
class OperationPrinter : public IReplayPacketHandler
{
private:
    DocumentTypeRepo &_repo;
    DummyStreamHandler _streamHandler;
    size_t _counter;

protected:
    void print(const FeedOperation &op) {
        std::cout << "OP[" << (_counter++) << "]: " << op.toString() << std::endl;
    }

public:
    OperationPrinter(DocumentTypeRepo &repo)
        : _repo(repo),
          _streamHandler(),
          _counter(0)
    {
    }
    virtual void replay(const PutOperation &op) override { print(op); }
    virtual void replay(const RemoveOperation &op) override { print(op); }
    virtual void replay(const UpdateOperation &op) override { print(op); }
    virtual void replay(const NoopOperation &op) override { print(op); }
    virtual void replay(const NewConfigOperation &op) override
    {
        print(op);
        typedef std::map<std::string, ConfigFile>::const_iterator I;
        for (I i(_streamHandler._cfs.begin()), ie(_streamHandler._cfs.end());
             i != ie; ++i) {
            i->second.print();
        }
    }

    virtual void replay(const WipeHistoryOperation &op) override { print(op); }
    virtual void replay(const DeleteBucketOperation &op) override { print(op); }
    virtual void replay(const SplitBucketOperation &op) override { print(op); }
    virtual void replay(const JoinBucketsOperation &op) override { print(op); }
    virtual void replay(const PruneRemovedDocumentsOperation &op) override { print(op); }
    virtual void replay(const SpoolerReplayStartOperation &op) override { print(op); }
    virtual void replay(const SpoolerReplayCompleteOperation &op) override { print(op); }
    virtual void replay(const MoveOperation &op) override { print(op); }
    virtual void replay(const CreateBucketOperation &op) override { print(op); }
    virtual void replay(const CompactLidSpaceOperation &op) override { print(op); }
    virtual NewConfigOperation::IStreamHandler &getNewConfigStreamHandler() override {
        return _streamHandler;
    }
    virtual document::DocumentTypeRepo &getDeserializeRepo() override {
        return _repo;
    }
};


/**
 * Class the receives all concrete operations as part of a domain visit
 * and prints all document operations to standard out.
 */
class DocumentPrinter : public OperationPrinter
{
private:
    bool _printXml;
    bool _verbose;

    void printXml(const vespalib::xml::XmlSerializable &toPrint) {
        vespalib::xml::XmlOutputStream out(std::cout);
        toPrint.printXml(out);
        std::cout << std::endl;
    }

    void printXml(const document::FieldValue &toPrint) {
        vespalib::xml::XmlOutputStream out(std::cout);
        toPrint.printXml(out);
        std::cout << std::endl;
    }

    void printText(const document::Printable &toPrint) {
        toPrint.print(std::cout, _verbose);
        std::cout << std::endl;
    }

    void printText(const document::FieldValue &toPrint) {
        toPrint.print(std::cout, _verbose);
        std::cout << std::endl;
    }

public:
    DocumentPrinter(DocumentTypeRepo &repo, bool printXml_, bool verbose)
        : OperationPrinter(repo),
          _printXml(printXml_),
          _verbose(verbose)
    {
    }
    virtual void replay(const PutOperation &op) override {
        print(op);
        if (op.getDocument().get() != NULL) {
            if (_printXml) {
                printXml(*op.getDocument());
            } else {
                printText(*op.getDocument());
            }
        }
    }
    virtual void replay(const RemoveOperation &op) override {
        print(op);
    }
    virtual void replay(const UpdateOperation &op) override {
        print(op);
        if (op.getUpdate().get() != NULL) {
            if (_printXml) {
                printXml(*op.getUpdate());
            } else {
                printText(*op.getUpdate());
            }
        }
    }
    virtual void replay(const NoopOperation &) override { }
    virtual void replay(const NewConfigOperation &) override { }
    virtual void replay(const WipeHistoryOperation &) override { }
    virtual void replay(const DeleteBucketOperation &) override { }
    virtual void replay(const SplitBucketOperation &) override { }
    virtual void replay(const JoinBucketsOperation &) override { }
    virtual void replay(const PruneRemovedDocumentsOperation &) override { }
    virtual void replay(const SpoolerReplayStartOperation &) override { }
    virtual void replay(const SpoolerReplayCompleteOperation &) override { }
    virtual void replay(const MoveOperation &) override { }
    virtual void replay(const CreateBucketOperation &) override { }
};


/**
 * Class that receives packets from the tls as part of a domain visit
 * and dispatches each packet entry to the ReplayPacketDispatcher that
 * transforms them into concrete operations.
 */
class VisitorCallback : public TransLogClient::Session::Callback
{
private:
    ReplayPacketDispatcher _dispatcher;
    bool _eof;

public:
    VisitorCallback(IReplayPacketHandler &handler)
        : _dispatcher(handler),
          _eof(false)
    {
    }
    virtual RPC::Result receive(const Packet &packet) override {
        vespalib::nbostream_longlivedbuf handle(packet.getHandle().c_str(), packet.getHandle().size());
        try {
            while (handle.size() > 0) {
                Packet::Entry entry;
                entry.deserialize(handle);
                _dispatcher.replayEntry(entry);
            }
        } catch (const std::exception &e) {
            std::cerr << "Error while handling transaction log packet: '"
                << std::string(e.what()) << "'" << std::endl;
            return RPC::ERROR;
        }
        return RPC::OK;
    }
    virtual void eof() override { _eof = true; }
    bool isEof() const { return _eof; }
};


/**
 * Interface for a utility.
 */
struct Utility
{
    virtual ~Utility() {}
    typedef std::unique_ptr<Utility> UP;
    virtual int run() = 0;
};


/**
 * Base options used by a utility class.
 */
class BaseOptions
{
protected:
    vespalib::ProgramOptions _opts;

public:
    std::string tlsDir;
    std::string tlsName;
    int         listenPort;
    typedef std::unique_ptr<BaseOptions> UP;
    BaseOptions(int argc, const char* const* argv);
    virtual ~BaseOptions();
    void usage() { _opts.writeSyntaxPage(std::cout); }
    virtual void parse() { _opts.parse(); }
    virtual std::string toString() const {
        return vespalib::make_string("tlsdir=%s, tlsname=%s, listenport=%d",
                                     tlsDir.c_str(), tlsName.c_str(), listenPort);
    }
    virtual Utility::UP createUtility() const = 0;
};

BaseOptions::BaseOptions(int argc, const char* const* argv)
    : _opts(argc, argv)
{
    _opts.addOption("tlsdir", tlsDir, "Tls directory");
    _opts.addOption("tlsname", tlsName, std::string("tls"), "Name of the tls");
    _opts.addOption("listenport", listenPort, 13701, "Tcp listen port");
}
BaseOptions::~BaseOptions() {}

/**
 * Base class for a utility with tls server and tls client.
 */
class BaseUtility : public Utility
{
protected:
    const BaseOptions     &_bopts;
    DummyFileHeaderContext _fileHeader;
    TransLogServer         _server;
    TransLogClient         _client;

public:
    BaseUtility(const BaseOptions &bopts)
        : _bopts(bopts),
          _fileHeader(),
          _server(_bopts.tlsName, _bopts.listenPort, _bopts.tlsDir, _fileHeader),
          _client(vespalib::make_string("tcp/localhost:%d", _bopts.listenPort))
    {
    }
    virtual int run() override = 0;
};


/**
 * Program options used by ListDomainsUtility.
 */
struct ListDomainsOptions : public BaseOptions
{
    ListDomainsOptions(int argc, const char* const* argv)
        : BaseOptions(argc, argv)
    {
        _opts.setSyntaxMessage("Utility to list all domains in a tls");
    }
    static std::string command() { return "listdomains"; }
    virtual Utility::UP createUtility() const override;
};

/**
 * Utility to list all domains in a tls.
 */
class ListDomainsUtility : public BaseUtility
{
public:
    ListDomainsUtility(const ListDomainsOptions &opts)
        : BaseUtility(opts)
    {
    }
    virtual int run() override {
        std::cout << ListDomainsOptions::command() << ": " << _bopts.toString() << std::endl;

        std::vector<vespalib::string> domains;
        _client.listDomains(domains);
        std::cout << "Listing status for " << domains.size() << " domain(s):" << std::endl;
        for (size_t i = 0; i < domains.size(); ++i) {
            TransLogClient::Session::UP session = _client.open(domains[i]);
            SerialNum first;
            SerialNum last;
            size_t count;
            session->status(first, last, count);
            std::cout << "Domain '" << domains[i] << "': first=" << first << ", last=" << last;
            std::cout << ", count=" << count << std::endl;
        }
        return 0;
    }
};

Utility::UP
ListDomainsOptions::createUtility() const
{
    return Utility::UP(new ListDomainsUtility(*this));
}


/**
 * Program options used by DumpOperationsUtility.
 */
struct DumpOperationsOptions : public BaseOptions
{
    std::string domainName;
    SerialNum   firstSerialNum;
    SerialNum   lastSerialNum;
    std::string configDir;
    DumpOperationsOptions(int argc, const char* const* argv);
    ~DumpOperationsOptions();
    static std::string command() { return "dumpoperations"; }
    virtual std::string toString() const override {
        return vespalib::make_string("%s, domain=%s, first=%" PRIu64 ", last=%" PRIu64 ", configdir=%s",
                                     BaseOptions::toString().c_str(), domainName.c_str(),
                                     firstSerialNum, lastSerialNum,
                                     configDir.c_str());
    }
    virtual Utility::UP createUtility() const override;
};

DumpOperationsOptions::DumpOperationsOptions(int argc, const char* const* argv)
    : BaseOptions(argc, argv)
{
    _opts.addOption("domain", domainName, "Name of the domain");
    _opts.addOption("first", firstSerialNum, "Serial number of first operation");
    _opts.addOption("last", lastSerialNum, "Serial number of last operation");
    _opts.addOption("configdir", configDir, "Config directory (with documenttypes.cfg)");
    _opts.setSyntaxMessage("Utility to dump a range of operations ([first,last]) in a tls domain");
}
DumpOperationsOptions::~DumpOperationsOptions() {}


/**
 * Utility to dump a range of operations in a tls domain.
 */
class DumpOperationsUtility : public BaseUtility
{
protected:
    const DumpOperationsOptions &_oopts;

    virtual IReplayPacketHandlerUP createHandler(DocumentTypeRepo &repo) {
        return IReplayPacketHandlerUP(new OperationPrinter(repo));
    }

    int doRun() {
        DocTypeRepo repo(_oopts.configDir);
        IReplayPacketHandlerUP handler = createHandler(repo.docTypeRepo);
        VisitorCallback callback(*handler);
        TransLogClient::Visitor::UP visitor = _client.createVisitor(_oopts.domainName, callback);
        bool visitOk = visitor->visit(_oopts.firstSerialNum-1, _oopts.lastSerialNum);
        if (!visitOk) {
            std::cerr << "Visiting domain '" << _oopts.domainName << "' [" << _oopts.firstSerialNum << ","
                << _oopts.lastSerialNum << "] failed" << std::endl;
            return 1;
        }
        for (size_t i = 0; !callback.isEof() && (i < 60 * 60); i++ ) {
            FastOS_Thread::Sleep(1000);
        }
        return 0;
    }

public:
    DumpOperationsUtility(const DumpOperationsOptions &oopts)
        : BaseUtility(oopts),
        _oopts(oopts)
    {
    }
    virtual int run() override {
        std::cout << DumpOperationsOptions::command() << ": " << _oopts.toString() << std::endl;
        return doRun();
    }
};

Utility::UP
DumpOperationsOptions::createUtility() const
{
    return Utility::UP(new DumpOperationsUtility(*this));
}


/**
 * Program options used by DumpDocumentsUtility.
 */
struct DumpDocumentsOptions : public DumpOperationsOptions
{
    std::string format;
    bool verbose;
    DumpDocumentsOptions(int argc, const char* const* argv);
    ~DumpDocumentsOptions();
    static std::string command() { return "dumpdocuments"; }
    virtual void parse() override {
        DumpOperationsOptions::parse();
        if (format != "xml" && format != "text") {
            throw vespalib::InvalidCommandLineArgumentsException("Expected 'format' to be 'xml' or 'text'");
        }
    }
    virtual std::string toString() const override {
        return vespalib::make_string("%s, format=%s, verbose=%s",
                                     DumpOperationsOptions::toString().c_str(),
                                     format.c_str(), (verbose ? "true" : "false"));
    }
    virtual Utility::UP createUtility() const override;
};

DumpDocumentsOptions::DumpDocumentsOptions(int argc, const char* const* argv)
    : DumpOperationsOptions(argc, argv)
{
    _opts.addOption("format", format, std::string("xml"), "Format in which the document operations should be dumped ('xml' or 'text')");
    _opts.addOption("verbose", verbose, false, "Whether the document operations should be dumped verbosely");
    _opts.setSyntaxMessage("Utility to dump a range of document operations ([first,last]) in a tls domain");
}
DumpDocumentsOptions::~DumpDocumentsOptions() {}


/**
 * Utility to dump a range of document operations in a tls domain.
 */
class DumpDocumentsUtility : public DumpOperationsUtility
{
protected:
    const DumpDocumentsOptions &_dopts;
    virtual IReplayPacketHandlerUP createHandler(DocumentTypeRepo &repo) override {
        return IReplayPacketHandlerUP(new DocumentPrinter(repo, _dopts.format == "xml", _dopts.verbose));
    }

public:
    DumpDocumentsUtility(const DumpDocumentsOptions &dopts)
        : DumpOperationsUtility(dopts),
          _dopts(dopts)
    {
    }
    virtual int run() override {
        std::cout << DumpDocumentsOptions::command() << ": " << _oopts.toString() << std::endl;
        return doRun();
    }
};

Utility::UP
DumpDocumentsOptions::createUtility() const
{
    return Utility::UP(new DumpDocumentsUtility(*this));
}


/**
 * Main application.
 */
class App : public FastOS_Application
{
private:
    std::string _programName;
    std::string _tmpArg;

    void combineFirstArgs() {
        _tmpArg = vespalib::make_string("%s %s", _argv[0], _argv[1]).c_str();
        _argv[1] = &_tmpArg[0];
    }
    void replaceFirstArg(const std::string &replace) {
        _tmpArg = vespalib::make_string("%s %s", _programName.c_str(), replace.c_str()).c_str();
        _argv[0] = &_tmpArg[0];
    }
    void usageHeader() {
        std::cout << _programName << " version 0.0\n";
    }
    void usage() {
        usageHeader();
        replaceFirstArg(ListDomainsOptions::command());
        ListDomainsOptions(_argc, _argv).usage();
        replaceFirstArg(DumpOperationsOptions::command());
        DumpOperationsOptions(_argc, _argv).usage();
        replaceFirstArg(DumpDocumentsOptions::command());
        DumpDocumentsOptions(_argc, _argv).usage();
    }

public:
    App();
    ~App();
    int Main() override;
};

App::App() {}
App::~App() {}

int
App::Main() {
    _programName = _argv[0];
    if (_argc < 2) {
        usage();
        return 1;
    }
    BaseOptions::UP opts;
    if (strcmp(_argv[1], ListDomainsOptions::command().c_str()) == 0) {
        combineFirstArgs();
        opts.reset(new ListDomainsOptions(_argc-1, _argv+1));
    } else if (strcmp(_argv[1], DumpOperationsOptions::command().c_str()) == 0) {
        combineFirstArgs();
        opts.reset(new DumpOperationsOptions(_argc-1, _argv+1));
    } else if (strcmp(_argv[1], DumpDocumentsOptions::command().c_str()) == 0) {
        combineFirstArgs();
        opts.reset(new DumpDocumentsOptions(_argc-1, _argv+1));
    }
    if (opts.get() != NULL) {
        try {
            opts->parse();
        } catch (const vespalib::InvalidCommandLineArgumentsException &e) {
            std::cerr << "Error parsing program options: " << e.getMessage() << std::endl;
            usageHeader();
            opts->usage();
            return 1;
        }
        return opts->createUtility()->run();
    }
    usage();
    return 1;
}

int
main(int argc, char **argv)
{
    App app;
    return app.Entry(argc, argv);
}
