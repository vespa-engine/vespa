// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/replaypacketdispatcher.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/xmlstream.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("vespa-transactionlog-inspect");

using namespace proton;
using namespace search;
using namespace search::common;
using namespace search::transactionlog;

using document::DocumentTypeRepo;

using DocumenttypesConfigSP = std::shared_ptr<DocumenttypesConfig>;
using IReplayPacketHandlerUP = std::unique_ptr<IReplayPacketHandler>;

struct DummyFileHeaderContext : public FileHeaderContext
{
    using UP = std::unique_ptr<DummyFileHeaderContext>;
    void addTags(vespalib::GenericHeader &, const vespalib::string &) const override {}
};


namespace {

class ConfigFile
{
    using SP = std::shared_ptr<ConfigFile>;

    vespalib::string _name;
    std::vector<char> _content;

public:
    ConfigFile();
    const vespalib::string & getName() const { return _name; }
    vespalib::nbostream & deserialize(vespalib::nbostream &stream);
    void print() const;
};


ConfigFile::ConfigFile()
    : _name(),
      _content()
{
}


vespalib::nbostream &
ConfigFile::deserialize(vespalib::nbostream &stream)
{
    stream >> _name;
    assert(strchr(_name.c_str(), '/') == nullptr);
    int64_t modTime;
    stream >> modTime;
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
    std::cout << "Name: " << _name << "\n"
              << "Content-Length: " << _content.size() << "\n\n";
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

    void serializeConfig(SerialNum, vespalib::nbostream &) override { }

    void
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

DocTypeRepo::~DocTypeRepo() = default;


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
    void replay(const PutOperation &op) override { print(op); }
    void replay(const RemoveOperation &op) override { print(op); }
    void replay(const UpdateOperation &op) override { print(op); }
    void replay(const NoopOperation &op) override { print(op); }
    void replay(const NewConfigOperation &op) override
    {
        print(op);
        for (const auto & entry : _streamHandler._cfs) {
            entry.second.print();
        }
    }

    void replay(const DeleteBucketOperation &op) override { print(op); }
    void replay(const SplitBucketOperation &op) override { print(op); }
    void replay(const JoinBucketsOperation &op) override { print(op); }
    void replay(const PruneRemovedDocumentsOperation &op) override { print(op); }
    void replay(const MoveOperation &op) override { print(op); }
    void replay(const CreateBucketOperation &op) override { print(op); }
    void replay(const CompactLidSpaceOperation &op) override { print(op); }
    NewConfigOperation::IStreamHandler &getNewConfigStreamHandler() override {
        return _streamHandler;
    }
    document::DocumentTypeRepo &getDeserializeRepo() override {
        return _repo;
    }
    void check_serial_num(search::SerialNum) override { }
    void optionalCommit(search::SerialNum) override { }
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
    void printXml(const document::DocumentUpdate &toPrint) {
        vespalib::xml::XmlOutputStream out(std::cout);
        toPrint.printXml(out);
        std::cout << std::endl;
    }

    void printText(const document::DocumentUpdate &toPrint) {
        toPrint.print(std::cout, _verbose, "");
        std::cout << std::endl;
    }

    void printText(const document::FieldValue &toPrint) {
        toPrint.print(std::cout, _verbose, "");
        std::cout << std::endl;
    }

public:
    DocumentPrinter(DocumentTypeRepo &repo, bool printXml_, bool verbose)
        : OperationPrinter(repo),
          _printXml(printXml_),
          _verbose(verbose)
    {
    }
    void replay(const PutOperation &op) override {
        print(op);
        if (op.getDocument()) {
            if (_printXml) {
                printXml(*op.getDocument());
            } else {
                printText(*op.getDocument());
            }
        }
    }
    void replay(const RemoveOperation &op) override {
        print(op);
    }
    void replay(const UpdateOperation &op) override {
        print(op);
        if (op.getUpdate()) {
            if (_printXml) {
                printXml(*op.getUpdate());
            } else {
                printText(*op.getUpdate());
            }
        }
    }
    void replay(const NoopOperation &) override { }
    void replay(const NewConfigOperation &) override { }
    void replay(const DeleteBucketOperation &) override { }
    void replay(const SplitBucketOperation &) override { }
    void replay(const JoinBucketsOperation &) override { }
    void replay(const PruneRemovedDocumentsOperation &) override { }
    void replay(const MoveOperation &) override { }
    void replay(const CreateBucketOperation &) override { }
};


/**
 * Class that receives packets from the tls as part of a domain visit
 * and dispatches each packet entry to the ReplayPacketDispatcher that
 * transforms them into concrete operations.
 */
class VisitorCallback : public client::Callback
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
    client::RPC::Result receive(const Packet &packet) override {
        vespalib::nbostream_longlivedbuf handle(packet.getHandle().data(), packet.getHandle().size());
        try {
            while (handle.size() > 0) {
                Packet::Entry entry;
                entry.deserialize(handle);
                _dispatcher.replayEntry(entry);
            }
        } catch (const std::exception &e) {
            std::cerr << "Error while handling transaction log packet: '"
                << std::string(e.what()) << "'" << std::endl;
            return client::RPC::ERROR;
        }
        return client::RPC::OK;
    }
    void eof() override { _eof = true; }
    bool isEof() const { return _eof; }
};


/**
 * Interface for a utility.
 */
struct Utility
{
    virtual ~Utility() = default;
    using UP = std::unique_ptr<Utility>;
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
    using UP = std::unique_ptr<BaseOptions>;
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
BaseOptions::~BaseOptions() = default;

/**
 * Base class for a utility with tls server and tls client.
 */
class BaseUtility : public Utility
{
protected:
    const BaseOptions     &_bopts;
    DummyFileHeaderContext _fileHeader;
    FNET_Transport         _transport;
    TransLogServer         _server;
    client::TransLogClient _client;

public:
    BaseUtility(const BaseOptions &bopts)
        : _bopts(bopts),
          _fileHeader(),
          _transport(),
          _server(_transport, _bopts.tlsName, _bopts.listenPort, _bopts.tlsDir, _fileHeader),
          _client(_transport, vespalib::make_string("tcp/localhost:%d", _bopts.listenPort))
    {
        _transport.Start();
    }
    ~BaseUtility() override {
        _transport.ShutDown(true);
    }
    int run() override = 0;
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
    Utility::UP createUtility() const override;
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
    int run() override {
        std::cout << ListDomainsOptions::command() << ": " << _bopts.toString() << std::endl;

        std::vector<vespalib::string> domains;
        _client.listDomains(domains);
        std::cout << "Listing status for " << domains.size() << " domain(s):" << std::endl;
        for (size_t i = 0; i < domains.size(); ++i) {
            std::unique_ptr<client::Session> session = _client.open(domains[i]);
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
    return std::make_unique<ListDomainsUtility>(*this);
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
DumpOperationsOptions::~DumpOperationsOptions() = default;


/**
 * Utility to dump a range of operations in a tls domain.
 */
class DumpOperationsUtility : public BaseUtility
{
protected:
    const DumpOperationsOptions &_oopts;

    virtual IReplayPacketHandlerUP createHandler(DocumentTypeRepo &repo) {
        return std::make_unique<OperationPrinter>(repo);
    }

    int doRun() {
        DocTypeRepo repo(_oopts.configDir);
        IReplayPacketHandlerUP handler = createHandler(repo.docTypeRepo);
        VisitorCallback callback(*handler);
        std::unique_ptr<client::Visitor> visitor = _client.createVisitor(_oopts.domainName, callback);
        bool visitOk = visitor->visit(_oopts.firstSerialNum-1, _oopts.lastSerialNum);
        if (!visitOk) {
            std::cerr << "Visiting domain '" << _oopts.domainName << "' [" << _oopts.firstSerialNum << ","
                << _oopts.lastSerialNum << "] failed" << std::endl;
            return 1;
        }
        for (size_t i = 0; !callback.isEof() && (i < 60 * 60); i++ ) {
            std::this_thread::sleep_for(1s);
        }
        return 0;
    }

public:
    DumpOperationsUtility(const DumpOperationsOptions &oopts)
        : BaseUtility(oopts),
        _oopts(oopts)
    {
    }
    int run() override {
        std::cout << DumpOperationsOptions::command() << ": " << _oopts.toString() << std::endl;
        return doRun();
    }
};

Utility::UP
DumpOperationsOptions::createUtility() const
{
    return std::make_unique<DumpOperationsUtility>(*this);
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
    void parse() override {
        DumpOperationsOptions::parse();
        if (format != "xml" && format != "text") {
            throw vespalib::InvalidCommandLineArgumentsException("Expected 'format' to be 'xml' or 'text'");
        }
    }
    std::string toString() const override {
        return vespalib::make_string("%s, format=%s, verbose=%s",
                                     DumpOperationsOptions::toString().c_str(),
                                     format.c_str(), (verbose ? "true" : "false"));
    }
    Utility::UP createUtility() const override;
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
    IReplayPacketHandlerUP createHandler(DocumentTypeRepo &repo) override {
        return IReplayPacketHandlerUP(new DocumentPrinter(repo, _dopts.format == "xml", _dopts.verbose));
    }

public:
    DumpDocumentsUtility(const DumpDocumentsOptions &dopts)
        : DumpOperationsUtility(dopts),
          _dopts(dopts)
    {
    }
    int run() override {
        std::cout << DumpDocumentsOptions::command() << ": " << _oopts.toString() << std::endl;
        return doRun();
    }
};

Utility::UP
DumpDocumentsOptions::createUtility() const
{
    return std::make_unique<DumpDocumentsUtility>(*this);
}


/**
 * Main application.
 */
class App
{
private:
    std::string _programName;
    std::string _tmpArg;

    void combineFirstArgs(char **argv) {
        _tmpArg = vespalib::make_string("%s %s", argv[0], argv[1]).c_str();
        argv[1] = &_tmpArg[0];
    }
    void replaceFirstArg(char **argv, const std::string &replace) {
        _tmpArg = vespalib::make_string("%s %s", _programName.c_str(), replace.c_str()).c_str();
        argv[0] = &_tmpArg[0];
    }
    void usageHeader() {
        std::cout << _programName << " version 0.0\n";
    }
    void usage(int argc, char **argv) {
        usageHeader();
        replaceFirstArg(argv, ListDomainsOptions::command());
        ListDomainsOptions(argc, argv).usage();
        replaceFirstArg(argv, DumpOperationsOptions::command());
        DumpOperationsOptions(argc, argv).usage();
        replaceFirstArg(argv, DumpDocumentsOptions::command());
        DumpDocumentsOptions(argc, argv).usage();
    }

public:
    App();
    ~App();
    int main(int argc, char **argv);
};

App::App() = default;
App::~App() = default;

int
App::main(int argc, char **argv) {
    _programName = argv[0];
    if (argc < 2) {
        usage(argc, argv);
        return 1;
    }
    BaseOptions::UP opts;
    if (strcmp(argv[1], ListDomainsOptions::command().c_str()) == 0) {
        combineFirstArgs(argv);
        opts.reset(new ListDomainsOptions(argc-1, argv+1));
    } else if (strcmp(argv[1], DumpOperationsOptions::command().c_str()) == 0) {
        combineFirstArgs(argv);
        opts.reset(new DumpOperationsOptions(argc-1, argv+1));
    } else if (strcmp(argv[1], DumpDocumentsOptions::command().c_str()) == 0) {
        combineFirstArgs(argv);
        opts.reset(new DumpDocumentsOptions(argc-1, argv+1));
    }
    if (opts) {
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
    usage(argc, argv);
    return 1;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
