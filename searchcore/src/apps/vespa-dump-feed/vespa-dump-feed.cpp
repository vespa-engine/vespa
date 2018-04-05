// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/config.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/protocolset.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/slaveproc.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/helper/configgetter.hpp>

#include <iostream>

typedef vespalib::SignalHandler SIG;

//-----------------------------------------------------------------------------

class OutputFile
{
private:
    FILE *file;

public:
    OutputFile(const std::string &name)
        : file(fopen(name.c_str(), "w")) {}
    bool valid() const { return (file != 0); }
    void write(const char *data, size_t length) {
        size_t res = fwrite(data, 1, length, file);
        assert(res == length);
        (void) res;
    }
    ~OutputFile() { fclose(file); }
};

//-----------------------------------------------------------------------------

class FeedHandler : public mbus::IMessageHandler
{
private:
    documentapi::LoadTypeSet     _loadTypes;
    mbus::RPCMessageBus          _mbus;
    mbus::DestinationSession::UP _session;
    OutputFile                  &_idx;
    OutputFile                  &_dat;
    size_t                       _numDocs;

    void handleDocumentPut(const document::Document::SP & doc);
    virtual void handleMessage(mbus::Message::UP message) override;

public:
    FeedHandler(std::shared_ptr<const document::DocumentTypeRepo> repo, OutputFile &idx, OutputFile &dat);
    std::string getRoute() { return _session->getConnectionSpec(); }
    virtual ~FeedHandler();
};

void
FeedHandler::handleDocumentPut(const document::Document::SP & doc)
{
    if (doc) {
        vespalib::nbostream datStream(12345);
        vespalib::nbostream idxStream(12);
        doc->serialize(datStream);
        idxStream << uint64_t(datStream.size());
        _dat.write(datStream.peek(), datStream.size());
        _idx.write(idxStream.peek(), idxStream.size());
        ++_numDocs;
    }
}

void
FeedHandler::handleMessage(mbus::Message::UP message)
{
    mbus::Reply::UP reply;
    documentapi::DocumentMessage::UP msg((documentapi::DocumentMessage*)message.release());
    switch (msg->getType()) {
    case documentapi::DocumentProtocol::MESSAGE_PUTDOCUMENT:
    handleDocumentPut(((documentapi::PutDocumentMessage&)(*msg)).getDocumentSP());
    break;
    default:
    break;
    }
    reply = msg->createReply(); // use default reply for all messages
    msg->swapState(*reply);
    _session->reply(std::move(reply)); // handle all messages synchronously
}

FeedHandler::FeedHandler(std::shared_ptr<const document::DocumentTypeRepo> repo, OutputFile &idx, OutputFile &dat)
    : _loadTypes(),
      _mbus(mbus::MessageBusParams().addProtocol(mbus::IProtocol::SP(new documentapi::DocumentProtocol(_loadTypes, repo))),
            mbus::RPCNetworkParams()),
      _session(_mbus.getMessageBus()
               .createDestinationSession(mbus::DestinationSessionParams()
                                         .setBroadcastName(false)
                                         .setMessageHandler(*this)
                                         .setName("dump-feed"))),
      _idx(idx),
      _dat(dat),
      _numDocs()
{
}

FeedHandler::~FeedHandler()
{
    _session.reset();
    fprintf(stderr, "%zu document puts dumped to disk\n", _numDocs);
}

//-----------------------------------------------------------------------------

class App : public FastOS_Application
{
public:
    virtual bool useProcessStarter() const override { return true; }
    virtual int Main() override;
};

template <typename CFG>
bool writeConfig(std::unique_ptr<CFG> cfg, const std::string &dirName) {
    if (cfg.get() == 0) {
        return false;
    }
    std::string fileName = dirName + "/" + CFG::CONFIG_DEF_NAME + ".cfg";
    try {
        config::FileConfigWriter w(fileName);
        return w.write(*cfg);
    } catch (config::ConfigWriteException & e) {
        fprintf(stderr, "Unable to write config to disk: %s\n", e.what());
    }
    return false;
}

template <typename CFG>
std::unique_ptr<CFG> getConfig() {
    std::unique_ptr<CFG> ret(config::ConfigGetter<CFG>::getConfig("client"));
    if (ret.get() == 0) {
        fprintf(stderr, "error: could not obtain config (%s)\n", CFG::CONFIG_DEF_NAME.c_str());
    }
    return ret;
}

std::shared_ptr<const document::DocumentTypeRepo> getRepo() {
    typedef document::DocumenttypesConfig DCFG;
    std::unique_ptr<DCFG> dcfg = getConfig<DCFG>();
    std::shared_ptr<const document::DocumentTypeRepo> ret;
    if (dcfg.get() != 0) {
        ret.reset(new document::DocumentTypeRepo(*dcfg));
    }
    return ret;
}

void setupSignals() {
    SIG::PIPE.ignore();
}

int usage() {
    fprintf(stderr, "Usage: vespa-dump-feed <input-feed> <output-directory>\n\n");
    fprintf(stderr, "  Takes an XML vespa feed as input and dumps its contents as serialized documents.\n");
    fprintf(stderr, "  In addition to the actual documents, an index file containing document sizes\n");
    fprintf(stderr, "  and the appropriate config file(s) needed for deserialization are also stored.\n");
    fprintf(stderr, "  This utility can be run anywhere vespa-feeder can be run with default config id.\n");
    return 1;
}

int
App::Main()
{
    setupSignals();
    if (_argc != 3) {
        return usage();
    }
    std::string feedFile = _argv[1];
    std::string dirName = _argv[2];
    fprintf(stderr, "input feed: %s\n", feedFile.c_str());
    fprintf(stderr, "output directory: %s\n", dirName.c_str());
    vespalib::mkdir(dirName);
    typedef document::DocumenttypesConfig DCFG;
    if (!writeConfig(getConfig<DCFG>(), dirName)) {
        fprintf(stderr, "error: could not save config to disk\n");
        return 1;
    }
    std::shared_ptr<const document::DocumentTypeRepo> repo = getRepo();
    if (repo.get() == 0) {
        fprintf(stderr, "error: could not create document type repo\n");
        return 1;
    }
    {
        OutputFile idxFile(dirName + "/doc.idx");
        OutputFile datFile(dirName + "/doc.dat");
        if (!idxFile.valid() || !datFile.valid()) {
            fprintf(stderr, "error: could not open output document files\n");
            return 1;
        }
        FeedHandler feedHooks(repo, idxFile, datFile);
        std::string route = feedHooks.getRoute();
        fprintf(stderr, "route to self: %s\n", route.c_str());
        std::string feedCmd(vespalib::make_string("vespa-feeder --route \"%s\" %s",
                                                  route.c_str(), feedFile.c_str()));
        fprintf(stderr, "running feed command: %s\n", feedCmd.c_str());
        std::string feederOutput;
        bool feedingOk = vespalib::SlaveProc::run(feedCmd.c_str(), feederOutput);
        if (!feedingOk) {
            fprintf(stderr, "error: feed command failed\n");
            fprintf(stderr, "feed command output:\n-----\n%s\n-----\n", feederOutput.c_str());
            return 1;
        }
    }
    return 0;
}

//-----------------------------------------------------------------------------

int main(int argc, char **argv) {
    App app;
    return app.Entry(argc, argv);
}
