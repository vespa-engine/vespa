// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/searchlib/util/runnable.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/fastos/app.h>
#include <iostream>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("translogstress");

using document::ByteBuffer;
using search::Runnable;
using vespalib::Monitor;
using vespalib::MonitorGuard;
using std::shared_ptr;
using vespalib::make_string;
using vespalib::ConstBufferRef;
using search::index::DummyFileHeaderContext;

namespace search::transactionlog {

using ClientSession = TransLogClient::Session;
using Visitor = TransLogClient::Visitor;

//-----------------------------------------------------------------------------
// BufferGenerator
//-----------------------------------------------------------------------------
class BufferGenerator
{
private:
    Rand48 _rnd;
    uint32_t _minStrLen;
    uint32_t _maxStrLen;

public:
    BufferGenerator() :
        _rnd(), _minStrLen(0), _maxStrLen(0) {}
    BufferGenerator(uint32_t minStrLen, uint32_t maxStrLen) :
        _rnd(), _minStrLen(minStrLen), _maxStrLen(maxStrLen) {}
    void setSeed(long seed) { _rnd.srand48(seed); }
    ByteBuffer getRandomBuffer();
};

ByteBuffer
BufferGenerator::getRandomBuffer()
{
    size_t len = _minStrLen + _rnd.lrand48() % (_maxStrLen - _minStrLen);
    std::string str;
    for (size_t i = 0; i < len; ++i) {
        char c = 'a' + _rnd.lrand48() % ('z' - 'a' + 1);
        str.push_back(c);
    }
    ByteBuffer buf(str.size() + 1);
    buf.putBytes(str.c_str(), str.size() + 1);
    buf.flip();
    return buf;
}


//-----------------------------------------------------------------------------
// EntryGenerator
//-----------------------------------------------------------------------------
class EntryGenerator
{
private:
    Rand48 _rnd;
    long _baseSeed;
    BufferGenerator _bufferGenerator;
    const std::vector<document::ByteBuffer> * _buffers;
    ByteBuffer _lastGeneratedBuffer;

public:
    EntryGenerator(long baseSeed, const BufferGenerator & bufferGenerator) :
        _rnd(), _baseSeed(baseSeed), _bufferGenerator(bufferGenerator), _buffers(NULL),
        _lastGeneratedBuffer() {}
    EntryGenerator(const EntryGenerator & rhs) :
        _rnd(), _baseSeed(rhs._baseSeed), _bufferGenerator(rhs._bufferGenerator),
        _buffers(rhs._buffers), _lastGeneratedBuffer(rhs._lastGeneratedBuffer) {}
    EntryGenerator & operator=(const EntryGenerator & rhs) {
        _rnd = rhs._rnd;
        _baseSeed = rhs._baseSeed;
        _bufferGenerator = rhs._bufferGenerator;
        _buffers = rhs._buffers;
        return *this;
    };
    SerialNum getRandomSerialNum(SerialNum begin, SerialNum end);
    Packet::Entry getRandomEntry(SerialNum num);
    Rand48 & getRnd() { return _rnd; }
    void setBuffers(const std::vector<ByteBuffer> & buffers) {
        _buffers = &buffers;
    }
};

SerialNum
EntryGenerator::getRandomSerialNum(SerialNum begin, SerialNum end)
{
    // return random number in range [begin, end]
    assert(begin <= end);
    if (begin == end) {
        return SerialNum(begin);
    } else {
        return SerialNum(begin + _rnd.lrand48() % (end - begin + 1));
    }
}

Packet::Entry
EntryGenerator::getRandomEntry(SerialNum num)
{
    _rnd.srand48(_baseSeed + num);
    if (_buffers != NULL) {
        size_t i = _rnd.lrand48() % _buffers->size();
        const ByteBuffer& buffer = (*_buffers)[i];
        return Packet::Entry(num, 1024, ConstBufferRef(buffer.getBuffer(), buffer.getLength()));
    } else {
        _bufferGenerator.setSeed(_baseSeed + num);
        _lastGeneratedBuffer = _bufferGenerator.getRandomBuffer();
        return Packet::Entry(num, 1024, ConstBufferRef(_lastGeneratedBuffer.getBuffer(), _lastGeneratedBuffer.getLength()));
    }
}


//-----------------------------------------------------------------------------
// EntryComparator
//-----------------------------------------------------------------------------
class EntryComparator
{
public:
    static bool cmp(const Packet::Entry & lhs, const Packet::Entry & rhs) {
        if (lhs.serial() != rhs.serial()) {
            return false;
        }
        if (lhs.type() != rhs.type()) {
            return false;
        }
        if (lhs.data().size() != rhs.data().size()) {
            return false;
        }
        if (memcmp(lhs.data().c_str(), rhs.data().c_str(), lhs.data().size()) != 0) {
            return false;
        }
        return true;
    }
};


//-----------------------------------------------------------------------------
// EntryPrinter
//-----------------------------------------------------------------------------
class EntryPrinter
{
public:
    static std::string toStr(const Packet::Entry & e) {
        std::stringstream ss;
        ss << "Entry(serial(" << e.serial() << "), type(" << e.type() << "), bufferSize(" <<
            e.data().size() << "), buffer(";
        for (size_t i = 0; i < e.data().size() - 1; ++i) {
            ss << e.data().c_str()[i];
        }
        ss << ")";
        return ss.str();
    }
};


//-----------------------------------------------------------------------------
// PacketPrinter
//-----------------------------------------------------------------------------
class PacketPrinter
{
public:
    static std::string toStr(const Packet & p) {
        std::stringstream ss;
        ss << "Packet(entries(" << p.size() << "), range([" << p.range().from() << ", " << p.range().to()
            << "]), bytes(" << p.getHandle().size() << "))";
        return ss.str();
    }
};


//-----------------------------------------------------------------------------
// FeederThread
//-----------------------------------------------------------------------------
class FeederThread : public Runnable
{
private:
    std::string _tlsSpec;
    std::string _domain;
    TransLogClient _client;
    std::unique_ptr<ClientSession> _session;
    EntryGenerator _generator;
    uint32_t _feedRate;
    Packet _packet;
    SerialNum _current;
    SerialNum _lastCommited;
    FastOS_Time _timer;

    void commitPacket();
    bool addEntry(const Packet::Entry & e);

public:
    FeederThread(const std::string & tlsSpec, const std::string & domain,
                 const EntryGenerator & generator, uint32_t feedRate, size_t packetSize);
    ~FeederThread();
    virtual void doRun() override;
    SerialNumRange getRange() const { return SerialNumRange(1, _lastCommited); }
};

FeederThread::FeederThread(const std::string & tlsSpec, const std::string & domain,
                           const EntryGenerator & generator, uint32_t feedRate, size_t packetSize)
    : _tlsSpec(tlsSpec), _domain(domain), _client(tlsSpec), _session(),
      _generator(generator), _feedRate(feedRate), _packet(packetSize), _current(1), _lastCommited(1), _timer()
{}
FeederThread::~FeederThread() {}

void
FeederThread::commitPacket()
{
    const vespalib::nbostream& stream = _packet.getHandle();
    if (!_session->commit(ConstBufferRef(stream.c_str(), stream.size()))) {
        throw std::runtime_error(vespalib::make_string
                                 ("FeederThread: Failed commiting %s", PacketPrinter::toStr(_packet).c_str()));
    } else {
        LOG(info, "FeederThread: commited %s", PacketPrinter::toStr(_packet).c_str());
    }
    _packet.clear();
    _lastCommited = _current - 1;
}

bool
FeederThread::addEntry(const Packet::Entry & e)
{
    if (_packet.sizeBytes() > 0xf000) return false;
    _packet.add(e);
    return true;
}

void
FeederThread::doRun()
{
    _session = _client.open(_domain);
    if (_session.get() == NULL) {
        throw std::runtime_error(vespalib::make_string("FeederThread: Could not open session to %s", _tlsSpec.c_str()));
    }

    while (!_done) {
        if (_feedRate != 0) {
            _timer.SetNow();
            for (uint32_t i = 0; i < _feedRate; ++i) {
                Packet::Entry entry = _generator.getRandomEntry(_current++);
                if (!addEntry(entry)) {
                    commitPacket();
                    if (!addEntry(entry)) {
                        throw std::runtime_error(vespalib::make_string
                                                 ("FeederThread: Could not add %s", EntryPrinter::toStr(entry).c_str()));
                    }
                }
            }
            commitPacket();

            uint64_t milliSecsUsed = static_cast<uint64_t>(_timer.MilliSecsToNow());
            if (milliSecsUsed < 1000) {
                //LOG(info, "FeederThread: sleep %u ms", 1000 - milliSecsUsed);
                FastOS_Thread::Sleep(1000 - milliSecsUsed);
            } else {
                LOG(info, "FeederThread: max throughput");
            }
        } else {
            Packet::Entry entry = _generator.getRandomEntry(_current++);
            if (!addEntry(entry)) {
                commitPacket();
                if (!addEntry(entry)) {
                    throw std::runtime_error(vespalib::make_string
                                             ("FeederThread: Could not add %s", EntryPrinter::toStr(entry).c_str()));
                }
            }
        }
    }
}


//-----------------------------------------------------------------------------
// Agent
//-----------------------------------------------------------------------------
class Agent : public ClientSession::Callback
{
protected:
    std::string _tlsSpec;
    std::string _domain;
    TransLogClient _client;
    EntryGenerator _generator;
    std::string _name;
    uint32_t _id;
    bool _validate;

public:
    Agent(const std::string & tlsSpec, const std::string & domain,
          const EntryGenerator & generator, const std::string & name, uint32_t id, bool validate) :
        ClientSession::Callback(),
        _tlsSpec(tlsSpec), _domain(domain), _client(tlsSpec),
        _generator(generator), _name(name), _id(id), _validate(validate) {}
    virtual ~Agent() {}
    virtual RPC::Result receive(const Packet & packet) override = 0;
    virtual void eof() override {}
    virtual void failed() {}
};


//-----------------------------------------------------------------------------
// VisitorAgent
//-----------------------------------------------------------------------------
class VisitorAgent : public Agent
{
private:
    enum State {
        IDLE, RUNNING, FINISHED
    };
    std::unique_ptr<Visitor> _visitor;
    SerialNum _from;
    SerialNum _to;
    SerialNum _next;
    bool _running;
    State _state;
    Monitor _monitor;

    void setState(State newState) {
        MonitorGuard guard(_monitor);
        //LOG(info, "VisitorAgent[%u]: setState(%s)", _id, newState == IDLE ? "idle" :
        //    (newState == RUNNING ? "running" : "finished"));
        _state = newState;
    }
    SerialNum getNext();

public:
    VisitorAgent(const std::string & tlsSpec, const std::string & domain,
                 const EntryGenerator & generator, uint32_t id, bool validate) :
        Agent(tlsSpec, domain, generator, "VisitorAgent", id, validate),
        _visitor(), _from(0), _to(0), _next(0), _state(IDLE) {}
    virtual ~VisitorAgent() {}
    void start(SerialNum from, SerialNum to);
    void setIdle();
    bool idle() {
        MonitorGuard guard(_monitor);
        return _state == IDLE;
    }
    bool running() {
        MonitorGuard guard(_monitor);
        return _state == RUNNING;
    }
    bool finished() {
        MonitorGuard guard(_monitor);
        return _state == FINISHED;
    }
    std::string getState() {
        MonitorGuard guard(_monitor);
        if (_state == IDLE) {
            return std::string("idle");
        } else if (_state == FINISHED) {
            return std::string("finished");
        } else {
            return std::string("running");
        }
    }
    SerialNum getFrom() { return _from; }
    virtual RPC::Result receive(const Packet & packet) override;
    virtual void eof() override {
        LOG(info, "VisitorAgent[%u]: eof", _id);
        setState(FINISHED);
    }
};

SerialNum
VisitorAgent::getNext()
{
    SerialNum retval = _next++;
    if (retval > _to) {
        throw std::runtime_error(make_string("VisitorAgent[%u]: SerialNum (%" PRIu64 ") outside "
                                             "expected range <%" PRIu64 ", %" PRIu64 "]", _id,
                                             retval, _from, _to));
    }
    return retval;
}

void
VisitorAgent::start(SerialNum from, SerialNum to)
{
    assert(idle());
    LOG(info, "VisitorAgent[%u]: start<%" PRIu64 ", %" PRIu64 "]", _id, from, to);
    _from = from;
    _to = to;
    _next = from + 1;
    _visitor = _client.createVisitor(_domain, *this);
    if (_visitor.get() == NULL) {
        throw std::runtime_error(vespalib::make_string
                                 ("VisitorAgent[%u]: Could not open visitor to %s", _id, _tlsSpec.c_str()));
    }
    setState(RUNNING);
    if (!_visitor->visit(_from, _to)) {
        throw std::runtime_error(vespalib::make_string
                                 ("VisitorAgent[%u]: Could not visit from %s with range <%" PRIu64 ", %" PRIu64 "]",
                                  _id, _tlsSpec.c_str(), _from, _to));
    }
}

void
VisitorAgent::setIdle()
{
    assert(finished());
    _visitor.reset();
    setState(IDLE);
}

RPC::Result
VisitorAgent::receive(const Packet & packet)
{
    auto handle = packet.getHandle();
    while (handle.size() > 0) {
        Packet::Entry entry;
        entry.deserialize(handle);
        Packet::Entry expected = _generator.getRandomEntry(getNext());
        if (_validate) {
            if (!EntryComparator::cmp(entry, expected)) {
                throw std::runtime_error(vespalib::make_string
                                         ("VisitorAgent[%u]: Got %s, expected %s", _id,
                                          EntryPrinter::toStr(entry).c_str(),
                                          EntryPrinter::toStr(expected).c_str()));
            }
        }
    }

    if (_next > _to + 1) {
        throw std::runtime_error(vespalib::make_string
                                 ("VisitorAgent[%u]: Visited range <%" PRIu64 ", %" PRIu64 "], expected "
                                  "range <%" PRIu64 "u, %" PRIu64 "]", _id,
                                  _from, _next - 1, _from, _to));
    }

    return RPC::OK;
}


//-----------------------------------------------------------------------------
// ControllerThread
//-----------------------------------------------------------------------------
class ControllerThread : public Runnable
{
private:
    std::string _tlsSpec;
    std::string _domain;
    TransLogClient _client;
    std::unique_ptr<ClientSession> _session;
    EntryGenerator _generator;
    std::vector<std::shared_ptr<VisitorAgent> > _visitors;
    std::vector<std::shared_ptr<VisitorAgent> > _rndVisitors;
    uint64_t _visitorInterval; // in milliseconds
    uint64_t _pruneInterval;   // in milliseconds
    FastOS_Time _pruneTimer;
    SerialNum _begin;
    SerialNum _end;
    size_t _count;

    void getStatus();
    void makeRandomVisitorVector();

public:
    ControllerThread(const std::string & tlsSpec, const std::string & domain, const EntryGenerator & generator,
                     uint32_t numVisitors, uint64_t visitorInterval, uint64_t pruneInterval);
    ~ControllerThread();
    uint32_t runningVisitors();
    std::vector<std::shared_ptr<VisitorAgent> > & getVisitors() { return _visitors; }
    virtual void doRun() override;

};

ControllerThread::ControllerThread(const std::string & tlsSpec, const std::string & domain,
                                   const EntryGenerator & generator, uint32_t numVisitors,
                                   uint64_t visitorInterval, uint64_t pruneInterval)
    : _tlsSpec(tlsSpec), _domain(domain), _client(tlsSpec.c_str()), _session(),
      _generator(generator), _visitors(), _rndVisitors(), _visitorInterval(visitorInterval),
      _pruneInterval(pruneInterval), _pruneTimer(), _begin(0), _end(0), _count(0)
{
    for (uint32_t i = 0; i < numVisitors; ++i) {
        _visitors.push_back(std::make_shared<VisitorAgent>(tlsSpec, domain, generator, i, true));
    }
}
ControllerThread::~ControllerThread() {}

void
ControllerThread::getStatus()
{
    if (!_session->status(_begin, _end, _count)) {
        throw std::runtime_error(vespalib::make_string("ControllerThread: Could not get status from %s", _tlsSpec.c_str()));
    }
}

void
ControllerThread::makeRandomVisitorVector()
{
    std::vector<std::shared_ptr<VisitorAgent> > tmp(_visitors);
    _rndVisitors.clear();
    while (tmp.size() > 0) {
        size_t i = _generator.getRnd().lrand48() % tmp.size();
        _rndVisitors.push_back(tmp[i]);
        tmp.erase(tmp.begin() + i);
    }
}

void
ControllerThread::doRun()
{
    _session = _client.open(_domain);
    if (_session.get() == NULL) {
        throw std::runtime_error(vespalib::make_string("ControllerThread: Could not open session to %s", _tlsSpec.c_str()));
    }

    _pruneTimer.SetNow();
    while (!_done) {
        // set finished visitors as idle
        for (size_t i = 0; i < _visitors.size(); ++i) {
            if (_visitors[i]->finished()) {
                _visitors[i]->setIdle();
            }
        }
        // find idle visitor
        makeRandomVisitorVector();
        for (size_t i = 0; i < _rndVisitors.size(); ++i) {
            if (_rndVisitors[i]->idle()) {
                getStatus();
                SerialNum from = _generator.getRandomSerialNum(_begin, _end) - 1;
                SerialNum to = _generator.getRandomSerialNum(from + 1, _end);
                _rndVisitors[i]->start(from, to);
                break;
            }
        }
        // prune transaction log server
        if (_pruneTimer.MilliSecsToNow() > _pruneInterval) {
            getStatus();
            SerialNum safePrune = _end;
            for (size_t i = 0; i < _visitors.size(); ++i) {
                if (_visitors[i]->running() && _visitors[i]->getFrom() < safePrune) {
                    safePrune = _visitors[i]->getFrom();
                }
            }
            LOG(info, "ControllerThread: status: begin(%" PRIu64 "), end(%" PRIu64 "), count(%zu)", _begin, _end, _count);
            LOG(info, "ControllerThread: prune [%" PRIu64 ", %" PRIu64 ">", _begin, safePrune);
            if (!_session->erase(safePrune)) {
                throw std::runtime_error(vespalib::make_string("ControllerThread: Could not erase up to %" PRIu64, safePrune));
            }
            _pruneTimer.SetNow();
        }
        FastOS_Thread::Sleep(_visitorInterval);
    }
}


//-----------------------------------------------------------------------------
// TransLogStress
//-----------------------------------------------------------------------------
class TransLogStress : public FastOS_Application
{
private:
    class Config {
    public:
    uint64_t domainPartSize;
    size_t packetSize;

    uint64_t stressTime;
    uint32_t feedRate;
    uint32_t numVisitors;
    uint64_t visitorInterval;
    uint64_t pruneInterval;

    uint32_t numPreGeneratedBuffers;
    uint32_t minStrLen;
    uint32_t maxStrLen;
    long baseSeed;

    Config() :
        domainPartSize(0), packetSize(0), stressTime(0), feedRate(0),
        numVisitors(0), visitorInterval(0), pruneInterval(0), minStrLen(0), maxStrLen(0), baseSeed(0) {}
    };

    Config _cfg;

    void printConfig();
    void usage();

public:
    int Main() override;
};

void
TransLogStress::printConfig()
{
    std::cout << "######## Config ########" << std::endl;
    std::cout << "stressTime:             " << _cfg.stressTime / 1000 << " s" << std::endl;
    std::cout << "feedRate:               " << _cfg.feedRate << " per/sec" << std::endl;
    std::cout << "numVisitors:            " << _cfg.numVisitors << std::endl;
    std::cout << "visitorInterval:        " << _cfg.visitorInterval << " ms" << std::endl;
    std::cout << "pruneInterval:          " << _cfg.pruneInterval / 1000 << " s" << std::endl;
    std::cout << "numPreGeneratedBuffers: " << _cfg.numPreGeneratedBuffers << std::endl;
    std::cout << "minStrLen:              " << _cfg.minStrLen << std::endl;
    std::cout << "maxStrLen:              " << _cfg.maxStrLen << std::endl;
    std::cout << "baseSeed:               " << _cfg.baseSeed << std::endl;
    std::cout << "domainPartSize:         " << _cfg.domainPartSize << " bytes" << std::endl;
    std::cout << "packetSize:             " << _cfg.packetSize << " bytes" << std::endl;
}

void
TransLogStress::usage()
{
    std::cout << "usage: translogstress [-t stressTime(s)] [-f feedRate] [-s numSubscribers]" << std::endl;
    std::cout << "                      [-v numVisitors] [-c visitorInterval(ms)] [-e pruneInterval(s)]" << std::endl;
    std::cout << "                      [-g numPreGeneratedBuffers] [-i minStrLen] [-a maxStrLen] [-b baseSeed]" << std::endl;
    std::cout << "                      [-d domainPartSize] [-p packetSize]" << std::endl;
}

int
TransLogStress::Main()
{
    std::string tlsSpec("tcp/localhost:17897");
    std::string domain("translogstress");
    _cfg.domainPartSize = 8000000; // ~8MB
    _cfg.packetSize = 0x10000;

    _cfg.stressTime = 1000 * 60;
    _cfg.feedRate = 10000;
    _cfg.numVisitors = 1;
    _cfg.visitorInterval = 1000 * 1;
    _cfg.pruneInterval = 1000 * 12;

    _cfg.numPreGeneratedBuffers = 0;
    _cfg.minStrLen = 40;
    _cfg.maxStrLen = 80;
    _cfg.baseSeed = 100;

    uint64_t sleepTime = 4000;

    int idx = 1;
    char opt;
    const char * arg;
    bool optError = false;
    while ((opt = GetOpt("d:p:t:f:s:v:c:e:g:i:a:b:h", arg, idx)) != -1) {
        switch (opt) {
        case 'd':
            _cfg.domainPartSize = atol(arg);
            break;
        case 'p':
            _cfg.packetSize = atol(arg);
            break;
        case 't':
            _cfg.stressTime = 1000 * atol(arg);
            break;
        case 'f':
            _cfg.feedRate = atoi(arg);
            break;
        case 'v':
            _cfg.numVisitors = atoi(arg);
            break;
        case 'c':
            _cfg.visitorInterval = atol(arg);
            break;
        case 'e':
            _cfg.pruneInterval = 1000 * atol(arg);
            break;
        case 'g':
            _cfg.numPreGeneratedBuffers = atoi(arg);
            break;
        case 'i':
            _cfg.minStrLen = atoi(arg);
            break;
        case 'a':
            _cfg.maxStrLen = atoi(arg);
            break;
        case 'b':
            _cfg.baseSeed = atol(arg);
            break;
        case 'h':
            usage();
            return -1;
        default:
            optError = true;
            break;
        }
    }

    printConfig();
    FastOS_Thread::Sleep(sleepTime);

    if (_argc != idx || optError) {
        usage();
        return -1;
    }

    // start transaction log server
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tls("server", 17897, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(_cfg.domainPartSize));
    TransLogClient client(tlsSpec);
    client.create(domain);

    FastOS_ThreadPool threadPool(256000);

    BufferGenerator bufferGenerator(_cfg.minStrLen, _cfg.maxStrLen);
    bufferGenerator.setSeed(_cfg.baseSeed);
    std::vector<ByteBuffer> buffers;
    for (uint32_t i = 0; i < _cfg.numPreGeneratedBuffers; ++i) {
        buffers.push_back(bufferGenerator.getRandomBuffer());
    }
    EntryGenerator generator(_cfg.baseSeed, bufferGenerator);
    if (buffers.size() > 0) {
        generator.setBuffers(buffers);
    }


    // start feeder and controller
    FeederThread feeder(tlsSpec, domain, generator, _cfg.feedRate, _cfg.packetSize);
    threadPool.NewThread(&feeder);

    FastOS_Thread::Sleep(sleepTime);

    ControllerThread controller(tlsSpec, domain, generator, _cfg.numVisitors, _cfg.visitorInterval, _cfg.pruneInterval);
    threadPool.NewThread(&controller);

    // stop feeder and controller
    FastOS_Thread::Sleep(_cfg.stressTime);
    printConfig();
    LOG(info, "Stop feeder...");
    feeder.stop();
    feeder.join();
    std::cout << "<feeder>" << std::endl;
    std::cout << "  <from>" << feeder.getRange().from() << "</from>" << std::endl;
    std::cout << "  <to>" << feeder.getRange().to() << "</to>" << std::endl;
    std::cout << "  <rate>" << 1000 * (feeder.getRange().to() - feeder.getRange().from()) / (sleepTime + _cfg.stressTime)
        << "</rate>" << std::endl;
    std::cout << "</feeder>" << std::endl;

    LOG(info, "Stop controller...");
    controller.stop();
    controller.join();

    FastOS_Thread::Sleep(sleepTime);
    std::vector<std::shared_ptr<VisitorAgent> > & visitors = controller.getVisitors();
    for (size_t i = 0; i < visitors.size(); ++i) {
        std::cout << "<visitor id='" << i << "'>" << std::endl;
        std::cout << "<state>" << visitors[i]->getState() << "</state>" << std::endl;
        std::cout << "</visitor>" << std::endl;
    }

    threadPool.Close();

    return 0;
}

}

int main(int argc, char ** argv)
{
    search::transactionlog::TransLogStress myApp;
    return myApp.Entry(argc, argv);
}
