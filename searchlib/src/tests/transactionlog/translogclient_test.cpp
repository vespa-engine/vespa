// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/transactionlog/translogclient.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/fastos/file.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("translogclient_test");

using namespace search;
using namespace transactionlog;
using namespace document;
using namespace vespalib;
using namespace std::chrono_literals;
using search::index::DummyFileHeaderContext;
using search::transactionlog::client::TransLogClient;
using search::transactionlog::client::Session;
using search::transactionlog::client::Visitor;
using search::transactionlog::client::RPC;
using search::transactionlog::client::Callback;

namespace {

bool createDomainTest(TransLogClient & tls, const vespalib::string & name, size_t preExistingDomains=0);
std::unique_ptr<Session> openDomainTest(TransLogClient & tls, const vespalib::string & name);
bool fillDomainTest(Session * s1, const vespalib::string & name);
void fillDomainTest(Session * s1, size_t numPackets, size_t numEntries);
void fillDomainTest(Session * s1, size_t numPackets, size_t numEntries, size_t entrySize);
uint32_t countFiles(const vespalib::string &dir);
void checkFilledDomainTest(Session &s1, size_t numEntries);
bool visitDomainTest(TransLogClient & tls, Session * s1, const vespalib::string & name);
void createAndFillDomain(const vespalib::string & name, Encoding encoding, size_t preExistingDomains);
void verifyDomain(const vespalib::string & name);

vespalib::string
myhex(const void * b, size_t sz)
{
    static const char * hextab="0123456789ABCDEF";
    const auto * c = static_cast<const unsigned char *>(b);
    vespalib::string s;
    s.reserve(sz*2);
    for (size_t i=0; i < sz; i++) {
        s += hextab[c[i] >> 4];
        s += hextab[c[i] & 0x0f];
    }
    return s;
}

class CallBackTest : public Callback
{
private:
    RPC::Result receive(const Packet & packet) override;
    void eof()    override { _eof = true; }
    typedef std::map<SerialNum, std::unique_ptr<ByteBuffer>> PacketMap;
    PacketMap _packetMap;
public:
    CallBackTest() : _eof(false) { }
    size_t size() const { return _packetMap.size(); }
    bool hasSerial(SerialNum n) const { return (_packetMap.find(n) != _packetMap.end()); }
    void clear() {  _eof = false; _packetMap.clear(); }
    const ByteBuffer & packet(SerialNum n) { return *(_packetMap.find(n)->second); }

    bool      _eof;
};

RPC::Result
CallBackTest::receive(const Packet & p)
{
    nbostream_longlivedbuf  h(p.getHandle().data(), p.getHandle().size());
    LOG(info,"CallBackTest::receive (%zu, %zu, %zu)(%s)", h.rp(), h.size(), h.capacity(), myhex(h.peek(), h.size()).c_str());
    while( ! h.empty()) {
        Packet::Entry e;
        e.deserialize(h);
        LOG(info,"CallBackTest::receive (%zu, %zu, %zu)(%s)", h.rp(), h.size(), h.capacity(), myhex(e.data().c_str(), e.data().size()).c_str());
        _packetMap[e.serial()] = std::make_unique<ByteBuffer>(e.data().c_str(), e.data().size());
    }
    return RPC::OK;
}

class CallBackManyTest : public Callback
{
private:
    RPC::Result receive(const Packet & packet) override;
    void eof()    override { _eof = true; }
public:
    explicit CallBackManyTest(size_t start) : _eof(false), _count(start), _value(start) { }
    void clear() { _eof = false; _count = 0; _value = 0; }
    bool      _eof;
    size_t    _count;
    size_t    _value;
};

RPC::Result
CallBackManyTest::receive(const Packet & p)
{
    nbostream_longlivedbuf h(p.getHandle().data(), p.getHandle().size());
    for(; ! h.empty(); _count++, _value++) {
        Packet::Entry e;
        e.deserialize(h);
        assert(e.data().size() == 8);
        size_t v = *(const size_t*) (const void *)e.data().c_str();
        assert(_count+1 == e.serial());
        assert(v == _value);
        (void) v;
    }
    return RPC::OK;
}

class CallBackUpdate : public Callback
{
public:
    typedef std::map<SerialNum, Identifiable *> PacketMap;
private:
    RPC::Result receive(const Packet & packet) override;
    void eof()    override { _eof = true; }
    PacketMap _packetMap;
public:
    CallBackUpdate() : _eof(false) { }
    ~CallBackUpdate() override {
        while (_packetMap.begin() != _packetMap.end()) {
            delete _packetMap.begin()->second;
            _packetMap.erase(_packetMap.begin());
        }
    }
    bool hasSerial(SerialNum n) const { return (_packetMap.find(n) != _packetMap.end()); }
    const PacketMap & map() const { return _packetMap; }
    bool      _eof;
};


RPC::Result
CallBackUpdate::receive(const Packet & packet)
{
    nbostream_longlivedbuf h(packet.getHandle().data(), packet.getHandle().size());
    while ( ! h.empty() ) {
        Packet::Entry e;
        e.deserialize(h);
        const vespalib::Identifiable::RuntimeClass * cl(vespalib::Identifiable::classFromId(e.type()));
        if (cl) {
            vespalib::Identifiable * obj(cl->create());
            if (obj->inherits(Identifiable::classId)) {
                auto * ser = static_cast<Identifiable *>(obj);
                nbostream is(e.data().c_str(), e.data().size());
                try {
                    is >> *ser;
                } catch (std::exception & ex) {
                    LOG(warning, "Failed deserializing (%" PRId64 ", %s) bb(%zu, %zu, %zu)=%s what=%s", e.serial(), cl->name(), is.rp(), is.size(), is.capacity(), myhex(is.peek(), is.size()).c_str(), ex.what());
                    assert(false);
                    return RPC::ERROR;
                }
                ASSERT_TRUE(is.state() == nbostream::ok);
                ASSERT_TRUE(is.empty());
                _packetMap[e.serial()] = ser;
            } else {
                LOG(warning, "Packet::Entry(%" PRId64 ", %s) is not a Identifiable", e.serial(), cl->name());
            }
        } else {
            LOG(warning, "Packet::Entry(%" PRId64 ", %d) is not recognized by vespalib::Identifiable", e.serial(), e.type());
        }
    }
    return RPC::OK;
}

class CallBackStatsTest : public Callback
{
private:
    RPC::Result receive(const Packet & packet) override;
    void eof()    override { _eof = true; }
public:
    CallBackStatsTest() : _eof(false),
                          _count(0), _inOrder(0),
                          _firstSerial(0), _lastSerial(0),
                          _prevSerial(0) { }
    void clear() { _eof = false; _count = 0; _inOrder = 0;
        _firstSerial = 0; _lastSerial = 0; _inOrder = 0; }
    bool      _eof;
    uint64_t  _count;
    uint64_t  _inOrder; // increase when next entry is one above previous
    SerialNum _firstSerial;
    SerialNum _lastSerial;
    SerialNum _prevSerial;
};

RPC::Result
CallBackStatsTest::receive(const Packet & p)
{
    nbostream_longlivedbuf h(p.getHandle().data(), p.getHandle().size());
    for(; ! h.empty(); ++_count) {
        Packet::Entry e;
        e.deserialize(h);
        SerialNum s = e.serial();
        if (_count == 0) {
            _firstSerial = s;
            _lastSerial = s;
        }
        if (s == _prevSerial + 1) {
            ++_inOrder;
        }
        _prevSerial = s;
        if (_firstSerial > s) {
            _firstSerial = s;
        }
        if (_lastSerial < s) {
            _lastSerial = s;
        }
    }
    return RPC::OK;
}

#define CID_TestIdentifiable 0x5762314

class TestIdentifiable : public Identifiable
{
public:
    DECLARE_IDENTIFIABLE(TestIdentifiable);
    TestIdentifiable() { }
};

IMPLEMENT_IDENTIFIABLE(TestIdentifiable, Identifiable);

constexpr size_t DEFAULT_PACKET_SIZE = 0xf000;

bool
createDomainTest(TransLogClient & tls, const vespalib::string & name, size_t preExistingDomains)
{
    bool retval(true);
    std::vector<vespalib::string> dir;
    tls.listDomains(dir);
    EXPECT_EQUAL (dir.size(), preExistingDomains);
    auto s1 = tls.open(name);
    ASSERT_FALSE (s1);
    retval = tls.create(name);
    ASSERT_TRUE (retval);
    dir.clear();
    tls.listDomains(dir);
    EXPECT_EQUAL (dir.size(), preExistingDomains+1);
//    ASSERT_TRUE (dir[0] == name);
    return retval;
}

std::unique_ptr<Session>
openDomainTest(TransLogClient & tls, const vespalib::string & name)
{
    auto s1 = tls.open(name);
    ASSERT_TRUE (s1);
    return s1;
}

bool
fillDomainTest(Session * s1, const vespalib::string & name)
{
    bool retval(true);
    Packet::Entry e1(1, 1, vespalib::ConstBufferRef("Content in buffer A", 20));
    Packet::Entry e2(2, 2, vespalib::ConstBufferRef("Content in buffer B", 20));
    Packet::Entry e3(3, 1, vespalib::ConstBufferRef("Content in buffer C", 20));

    Packet a(DEFAULT_PACKET_SIZE);
    a.add(e1);
    Packet b(DEFAULT_PACKET_SIZE);
    b.add(e2);
    b.add(e3);
    EXPECT_EXCEPTION(b.add(e1), std::runtime_error, "");
    ASSERT_TRUE (s1->commit(vespalib::ConstBufferRef(a.getHandle().data(), a.getHandle().size())));
    ASSERT_TRUE (s1->commit(vespalib::ConstBufferRef(b.getHandle().data(), b.getHandle().size())));
    EXPECT_EXCEPTION(s1->commit(vespalib::ConstBufferRef(a.getHandle().data(), a.getHandle().size())),
                     std::runtime_error,
                     "commit failed with code -2. server says: Exception during commit on " + name + " : Incoming serial number(1) must be bigger than the last one (3).");
    EXPECT_EQUAL(a.size(), 1u);
    EXPECT_EQUAL(a.range().from(), 1u);
    EXPECT_EQUAL(a.range().to(), 1u);
    EXPECT_EQUAL(b.size(), 2u);
    EXPECT_EQUAL(b.range().from(), 2u);
    EXPECT_EQUAL(b.range().to(), 3u);
    a.merge(b);
    EXPECT_EQUAL(a.size(), 3u);
    EXPECT_EQUAL(a.range().from(), 1u);
    EXPECT_EQUAL(a.range().to(), 3u);

    Packet::Entry e;
    vespalib::nbostream h(a.getHandle().data(), a.getHandle().size());
    e.deserialize(h);
    e.deserialize(h);
    e.deserialize(h);
    EXPECT_EQUAL(h.size(), 0u);

    return retval;
}

void
fillDomainTest(Session * s1, size_t numPackets, size_t numEntries)
{
    size_t value(0);
    for(size_t i=0; i < numPackets; i++) {
        std::unique_ptr<Packet> p(new Packet(DEFAULT_PACKET_SIZE));
        for(size_t j=0; j < numEntries; j++, value++) {
            Packet::Entry e(value+1, j+1, vespalib::ConstBufferRef((const char *)&value, sizeof(value)));
            p->add(e);
            if (p->sizeBytes() > DEFAULT_PACKET_SIZE){
                ASSERT_TRUE(s1->commit(vespalib::ConstBufferRef(p->getHandle().data(), p->getHandle().size())));
                p = std::make_unique<Packet>(DEFAULT_PACKET_SIZE);
            }
        }
        ASSERT_TRUE(s1->commit(vespalib::ConstBufferRef(p->getHandle().data(), p->getHandle().size())));
    }
}

using Counter = std::atomic<size_t>;

class CountDone : public IDestructorCallback {
public:
    explicit CountDone(Counter & inFlight) noexcept : _inFlight(inFlight) { ++_inFlight; }
    ~CountDone() override { --_inFlight; }
private:
    Counter & _inFlight;
};

void
fillDomainTest(TransLogServer & s1, const vespalib::string & domain, size_t numPackets, size_t numEntries)
{
    size_t value(0);
    Counter inFlight(0);
    auto domainWriter = s1.getWriter(domain);
    for(size_t i=0; i < numPackets; i++) {
        std::unique_ptr<Packet> p(new Packet(DEFAULT_PACKET_SIZE));
        for(size_t j=0; j < numEntries; j++, value++) {
            Packet::Entry e(value+1, j+1, vespalib::ConstBufferRef((const char *)&value, sizeof(value)));
            p->add(e);
            if ( p->sizeBytes() > DEFAULT_PACKET_SIZE ) {
                domainWriter->append(*p, std::make_shared<CountDone>(inFlight));
                p = std::make_unique<Packet>(DEFAULT_PACKET_SIZE);
            }
        }
        domainWriter->append(*p, std::make_shared<CountDone>(inFlight));
        auto keep = domainWriter->startCommit(Writer::DoneCallback());
        LOG(info, "Inflight %ld", inFlight.load());
    }
    while (inFlight.load() != 0) {
        std::this_thread::sleep_for(10ms);
        LOG(info, "Waiting for inflight %ld to reach zero", inFlight.load());
    }

}


void
fillDomainTest(Session * s1, size_t numPackets, size_t numEntries, size_t entrySize)
{
    size_t value(0);
    std::vector<char> entryBuffer(entrySize); 
    for(size_t i=0; i < numPackets; i++) {
        std::unique_ptr<Packet> p(new Packet(DEFAULT_PACKET_SIZE));
        for(size_t j=0; j < numEntries; j++, value++) {
            Packet::Entry e(value+1, j+1, vespalib::ConstBufferRef((const char *)&entryBuffer[0], entryBuffer.size()));
            p->add(e);
            if (p->sizeBytes() > DEFAULT_PACKET_SIZE){
                ASSERT_TRUE(s1->commit(vespalib::ConstBufferRef(p->getHandle().data(), p->getHandle().size())));
                p = std::make_unique<Packet>(DEFAULT_PACKET_SIZE);
            }
        }
        ASSERT_TRUE(s1->commit(vespalib::ConstBufferRef(p->getHandle().data(), p->getHandle().size())));
    }
}


uint32_t
countFiles(const vespalib::string &dir)
{
    uint32_t res = 0;
    FastOS_DirectoryScan dirScan(dir.c_str());
    while (dirScan.ReadNext()) {
        const char *ename = dirScan.GetName();
        if (strcmp(ename, ".") == 0 ||
            strcmp(ename, "..") == 0)
            continue;
        ++res;
    }
    return res;
}

void
checkFilledDomainTest(Session &s1, size_t numEntries)
{
    SerialNum b(0), e(0);
    size_t c(0);
    EXPECT_TRUE(s1.status(b, e, c));
    EXPECT_EQUAL(b, 1u);
    EXPECT_EQUAL(e, numEntries);
    EXPECT_EQUAL(c, numEntries);
}

bool
visitDomainTest(TransLogClient & tls, Session * s1, const vespalib::string & name)
{
    bool retval(true);

    SerialNum b(0), e(0);
    size_t c(0);
    EXPECT_TRUE(s1->status(b, e, c));
    EXPECT_EQUAL(b, 1u);
    EXPECT_EQUAL(e, 3u);
    EXPECT_EQUAL(c, 3u);

    CallBackTest ca;
    auto visitor = tls.createVisitor(name, ca);
    ASSERT_TRUE(visitor);
    EXPECT_TRUE( visitor->visit(0, 1) );
    for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
    EXPECT_TRUE( ca._eof );
    EXPECT_TRUE( ! ca.hasSerial(0) );
    EXPECT_TRUE( ca.hasSerial(1) );
    EXPECT_TRUE( ! ca.hasSerial(2) );
    ca.clear();

    visitor = tls.createVisitor(name, ca);
    ASSERT_TRUE(visitor.get());
    EXPECT_TRUE( visitor->visit(1, 2) );
    for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
    EXPECT_TRUE( ca._eof );
    EXPECT_TRUE( ! ca.hasSerial(0) );
    EXPECT_TRUE( ! ca.hasSerial(1) );
    EXPECT_TRUE( ca.hasSerial(2) );
    EXPECT_TRUE( ! ca.hasSerial(3) );
    ca.clear();

    visitor = tls.createVisitor(name, ca);
    EXPECT_TRUE(visitor.get());
    EXPECT_TRUE( visitor->visit(0, 3) );
    for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
    EXPECT_TRUE( ca._eof );
    EXPECT_TRUE( ! ca.hasSerial(0) );
    EXPECT_TRUE( ca.hasSerial(1) );
    EXPECT_TRUE( ca.hasSerial(2) );
    EXPECT_TRUE( ca.hasSerial(3) );
    ca.clear();

    visitor = tls.createVisitor(name, ca);
    ASSERT_TRUE(visitor.get());
    EXPECT_TRUE( visitor->visit(2, 3) );
    for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
    EXPECT_TRUE( ca._eof );
    EXPECT_TRUE( ! ca.hasSerial(0) );
    EXPECT_TRUE( !ca.hasSerial(1) );
    EXPECT_TRUE( !ca.hasSerial(2) );
    EXPECT_TRUE( ca.hasSerial(3) );
    ca.clear();

    return retval;
}

double
getMaxSessionRunTime(TransLogServer &tls, const vespalib::string &domain)
{
    return tls.getDomainStats()[domain].maxSessionRunTime.count();
}

void createAndFillDomain(const vespalib::string & name, Encoding encoding, size_t preExistingDomains)
{
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test13", 18377, ".", fileHeaderContext,
                        DomainConfig().setPartSizeLimit(0x1000000).setEncoding(encoding), 4);
    TransLogClient tls("tcp/localhost:18377");

    createDomainTest(tls, name, preExistingDomains);
    auto s1 = openDomainTest(tls, name);
    fillDomainTest(s1.get(), name);
}

void verifyDomain(const vespalib::string & name) {
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test13", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
    TransLogClient tls("tcp/localhost:18377");
    auto s1 = openDomainTest(tls, name);
    visitDomainTest(tls, s1.get(), name);
}

}

TEST("testVisitOverGeneratedDomain") {
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test7", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
    TransLogClient tls("tcp/localhost:18377");

    vespalib::string name("test1");
    createDomainTest(tls, name);
    auto s1 = openDomainTest(tls, name);
    fillDomainTest(s1.get(), name);
    EXPECT_EQUAL(0, getMaxSessionRunTime(tlss, "test1"));
    visitDomainTest(tls, s1.get(), name);
    double maxSessionRunTime = getMaxSessionRunTime(tlss, "test1");
    LOG(info, "testVisitOverGeneratedDomain(): maxSessionRunTime=%f", maxSessionRunTime);
    EXPECT_GREATER(maxSessionRunTime, 0);
}

TEST("testVisitOverPreExistingDomain") {
    // Depends on Test::testVisitOverGeneratedDomain()
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test7", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
    TransLogClient tls("tcp/localhost:18377");

    vespalib::string name("test1");
    auto s1 = openDomainTest(tls, name);
    visitDomainTest(tls, s1.get(), name);
}

TEST("partialUpdateTest") {
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test7", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
    TransLogClient tls("tcp/localhost:18377");

    auto s1 = openDomainTest(tls, "test1");
    Session & session = *s1;

    TestIdentifiable du;

    nbostream os;
    os << du;

    vespalib::ConstBufferRef bb(os.data(), os.size());
    LOG(info, "DU : %s", myhex(bb.c_str(), bb.size()).c_str());
    Packet::Entry e(7, du.getClass().id(), bb);
    Packet pa(DEFAULT_PACKET_SIZE);
    pa.add(e);
    ASSERT_TRUE(session.commit(vespalib::ConstBufferRef(pa.getHandle().data(), pa.getHandle().size())));

    CallBackUpdate ca;
    auto visitor = tls.createVisitor("test1", ca);
    ASSERT_TRUE(visitor);
    ASSERT_TRUE( visitor->visit(5, 7) );
    for (size_t i(0); ! ca._eof && (i < 1000); i++ ) { std::this_thread::sleep_for(10ms); }
    ASSERT_TRUE( ca._eof );
    ASSERT_TRUE( ca.map().size() == 1);
    ASSERT_TRUE( ca.hasSerial(7) );

    CallBackUpdate ca1;
    auto visitor1 = tls.createVisitor("test1", ca1);
    ASSERT_TRUE(visitor1);
    ASSERT_TRUE( visitor1->visit(4, 5) );
    for (size_t i(0); ! ca1._eof && (i < 1000); i++ ) { std::this_thread::sleep_for(10ms); }
    ASSERT_TRUE( ca1._eof );
    ASSERT_TRUE( ca1.map().empty());

    CallBackUpdate ca2;
    auto visitor2 = tls.createVisitor("test1", ca2);
    ASSERT_TRUE(visitor2);
    ASSERT_TRUE( visitor2->visit(5, 6) );
    for (size_t i(0); ! ca2._eof && (i < 1000); i++ ) { std::this_thread::sleep_for(10ms); }
    ASSERT_TRUE( ca2._eof );
    ASSERT_TRUE( ca2.map().empty());

    CallBackUpdate ca3;
    auto visitor3 = tls.createVisitor("test1", ca3);
    ASSERT_TRUE(visitor3);
    ASSERT_TRUE( visitor3->visit(5, 1000) );
    for (size_t i(0); ! ca3._eof && (i < 1000); i++ ) { std::this_thread::sleep_for(10ms); }
    ASSERT_TRUE( ca3._eof );
    ASSERT_TRUE( ca3.map().size() == 1);
    ASSERT_TRUE( ca3.hasSerial(7) );
}

TEST("testCrcVersions") {
    createAndFillDomain("ccitt_crc32", Encoding(Encoding::Crc::ccitt_crc32, Encoding::Compression::none), 0);
    createAndFillDomain("xxh64", Encoding(Encoding::Crc::xxh64, Encoding::Compression::none), 1);

    verifyDomain("ccitt_crc32");
    verifyDomain("xxh64");
}

TEST("testRemove") {
    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("testremove", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
    TransLogClient tls("tcp/localhost:18377");

    vespalib::string name("test-delete");
    createDomainTest(tls, name);
    auto s1 = openDomainTest(tls, name);
    fillDomainTest(s1.get(), name);
    visitDomainTest(tls, s1.get(), name);
    ASSERT_TRUE(tls.remove(name));
}

namespace {

void
assertVisitStats(TransLogClient &tls, const vespalib::string &domain,
                 SerialNum visitStart, SerialNum visitEnd,
                 SerialNum expFirstSerial, SerialNum expLastSerial,
                 uint64_t expCount, uint64_t expInOrder)
{
    CallBackStatsTest ca;
    auto visitor = tls.createVisitor(domain, ca);
    ASSERT_TRUE(visitor);
    ASSERT_TRUE( visitor->visit(visitStart, visitEnd) );
    for (size_t i(0); ! ca._eof && (i < 60000); i++ ) {
        std::this_thread::sleep_for(10ms);
    }
    ASSERT_TRUE(ca._eof);
    EXPECT_EQUAL(expFirstSerial, ca._firstSerial);
    EXPECT_EQUAL(expLastSerial, ca._lastSerial);
    EXPECT_EQUAL(expCount, ca._count);
    EXPECT_EQUAL(expInOrder, ca._inOrder);
}

void
assertStatus(Session &s, SerialNum expFirstSerial, SerialNum expLastSerial, uint64_t expCount)
{
    SerialNum b(0), e(0);
    size_t c(0);
    EXPECT_TRUE(s.status(b, e, c));
    EXPECT_EQUAL(expFirstSerial, b);
    EXPECT_EQUAL(expLastSerial, e);
    EXPECT_EQUAL(expCount, c);
}

}


TEST("test sending a lot of data") {
    const unsigned int NUM_PACKETS = 1000;
    const unsigned int NUM_ENTRIES = 100;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;
    const vespalib::string MANY("many");
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test8", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x80000));
        TransLogClient tls("tcp/localhost:18377");

        createDomainTest(tls, MANY, 0);
        auto s1 = openDomainTest(tls, MANY);
        fillDomainTest(s1.get(), NUM_PACKETS, NUM_ENTRIES);
        SerialNum b(0), e(0);
        size_t c(0);
        EXPECT_TRUE(s1->status(b, e, c));
        EXPECT_EQUAL(b, 1u);
        EXPECT_EQUAL(e, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(c, TOTAL_NUM_ENTRIES);
        CallBackManyTest ca(2);
        auto visitor = tls.createVisitor("many", ca);
        ASSERT_TRUE(visitor);
        ASSERT_TRUE( visitor->visit(2, TOTAL_NUM_ENTRIES) );
        for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
        ASSERT_TRUE( ca._eof );
        EXPECT_EQUAL(ca._count, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(ca._value, TOTAL_NUM_ENTRIES);
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test8", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
        TransLogClient tls("tcp/localhost:18377");

        auto s1 = openDomainTest(tls, "many");
        SerialNum b(0), e(0);
        size_t c(0);
        EXPECT_TRUE(s1->status(b, e, c));
        EXPECT_EQUAL(b, 1u);
        EXPECT_EQUAL(e, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(c, TOTAL_NUM_ENTRIES);
        CallBackManyTest ca(2);
        auto visitor = tls.createVisitor(MANY, ca);
        ASSERT_TRUE(visitor);
        ASSERT_TRUE( visitor->visit(2, TOTAL_NUM_ENTRIES) );
        for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
        ASSERT_TRUE( ca._eof );
        EXPECT_EQUAL(ca._count, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(ca._value, TOTAL_NUM_ENTRIES);
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test8", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
        TransLogClient tls("tcp/localhost:18377");

        auto s1 = openDomainTest(tls, MANY);
        SerialNum b(0), e(0);
        size_t c(0);
        EXPECT_TRUE(s1->status(b, e, c));
        EXPECT_EQUAL(b, 1u);
        EXPECT_EQUAL(e, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(c, TOTAL_NUM_ENTRIES);
        CallBackManyTest ca(2);
        auto visitor = tls.createVisitor(MANY, ca);
        ASSERT_TRUE(visitor);
        ASSERT_TRUE( visitor->visit(2, TOTAL_NUM_ENTRIES) );
        for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
        ASSERT_TRUE( ca._eof );
        EXPECT_EQUAL(ca._count, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(ca._value, TOTAL_NUM_ENTRIES);
    }
}

TEST("test sending a lot of data async") {
    const unsigned int NUM_PACKETS = 1000;
    const unsigned int NUM_ENTRIES = 100;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;
    const vespalib::string MANY("many-async");
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test8", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x80000));
        TransLogClient tls("tcp/localhost:18377");
        createDomainTest(tls, MANY, 1);
        auto s1 = openDomainTest(tls, MANY);
        fillDomainTest(tlss, MANY, NUM_PACKETS, NUM_ENTRIES);
        SerialNum b(0), e(0);
        size_t c(0);
        EXPECT_TRUE(s1->status(b, e, c));

        EXPECT_EQUAL(e, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(c, TOTAL_NUM_ENTRIES);
        CallBackManyTest ca(2);
        auto visitor = tls.createVisitor(MANY, ca);
        ASSERT_TRUE(visitor);
        ASSERT_TRUE( visitor->visit(2, TOTAL_NUM_ENTRIES) );
        for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
        ASSERT_TRUE( ca._eof );
        EXPECT_EQUAL(ca._count, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(ca._value, TOTAL_NUM_ENTRIES);
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test8", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
        TransLogClient tls("tcp/localhost:18377");

        auto s1 = openDomainTest(tls, MANY);
        SerialNum b(0), e(0);
        size_t c(0);
        EXPECT_TRUE(s1->status(b, e, c));
        EXPECT_EQUAL(b, 1u);
        EXPECT_EQUAL(e, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(c, TOTAL_NUM_ENTRIES);
        CallBackManyTest ca(2);
        auto visitor = tls.createVisitor(MANY, ca);
        ASSERT_TRUE(visitor);
        ASSERT_TRUE( visitor->visit(2, TOTAL_NUM_ENTRIES) );
        for (size_t i(0); ! ca._eof && (i < 60000); i++ ) { std::this_thread::sleep_for(10ms); }
        ASSERT_TRUE( ca._eof );
        EXPECT_EQUAL(ca._count, TOTAL_NUM_ENTRIES);
        EXPECT_EQUAL(ca._value, TOTAL_NUM_ENTRIES);
    }
}




TEST("testErase") {
    const unsigned int NUM_PACKETS = 1000;
    const unsigned int NUM_ENTRIES = 100;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test12", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x80000));
        TransLogClient tls("tcp/localhost:18377");

        createDomainTest(tls, "erase", 0);
        auto s1 = openDomainTest(tls, "erase");
        fillDomainTest(s1.get(), NUM_PACKETS, NUM_ENTRIES);
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        TransLogServer tlss("test12", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
        TransLogClient tls("tcp/localhost:18377");

        auto s1 = openDomainTest(tls, "erase");

        // Before erase
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 3, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES -2, TOTAL_NUM_ENTRIES - 3));
        DomainStats domainStats = tlss.getDomainStats();
        DomainInfo domainInfo = domainStats["erase"];
        size_t numParts = domainInfo.parts.size();
        LOG(info, "%zu parts", numParts);
        for (uint32_t partId = 0; partId < numParts; ++partId) {
            const PartInfo &part = domainInfo.parts[partId];
            LOG(info,
                "part %u from %" PRIu64 " to %" PRIu64 ", "
                "count %zu, numBytes %zu",
                partId,
                (uint64_t) part.range.from(), (uint64_t) part.range.to(),
                part.numEntries, part.byteSize);
        }
        ASSERT_LESS_EQUAL(2u, numParts);
        // Erase everything before second to last domainpart file
        SerialNum eraseSerial = domainInfo.parts[numParts - 2].range.from();
        s1->erase(eraseSerial);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 2].numEntries +
                             domainInfo.parts[numParts - 1].numEntries));
        // No apparent effect of erasing just first entry in 2nd to last part
        s1->erase(eraseSerial + 1);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial + 1, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 2].numEntries +
                             domainInfo.parts[numParts - 1].numEntries));
        // No apparent effect of erasing almost all of 2nd to last part
        SerialNum eraseSerial2 = domainInfo.parts[numParts - 2].range.to();
        s1->erase(eraseSerial2);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial2, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 2].numEntries +
                             domainInfo.parts[numParts - 1].numEntries));
        // Erase everything before last domainpart file
        eraseSerial = domainInfo.parts[numParts - 1].range.from();
        s1->erase(eraseSerial);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 1].numEntries));
        // No apparent effect of erasing just first entry in last part
        s1->erase(eraseSerial + 1);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial + 1, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 1].numEntries));
        // No apparent effect of erasing almost all of last part
        eraseSerial2 = domainInfo.parts[numParts - 1].range.to();
        s1->erase(eraseSerial2);
        TEST_DO(assertVisitStats(tls, "erase", 2, TOTAL_NUM_ENTRIES,
                                 eraseSerial, TOTAL_NUM_ENTRIES,
                                 TOTAL_NUM_ENTRIES + 1 - eraseSerial,
                                 TOTAL_NUM_ENTRIES - eraseSerial));
        TEST_DO(assertStatus(*s1, eraseSerial2, TOTAL_NUM_ENTRIES,
                             domainInfo.parts[numParts - 1].numEntries));
    }
}

TEST("testSync") {
    const unsigned int NUM_PACKETS = 3;
    const unsigned int NUM_ENTRIES = 4;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;

    DummyFileHeaderContext fileHeaderContext;
    TransLogServer tlss("test9", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
    TransLogClient tls("tcp/localhost:18377");

    createDomainTest(tls, "sync", 0);
    auto s1 = openDomainTest(tls, "sync");
    fillDomainTest(s1.get(), NUM_PACKETS, NUM_ENTRIES);

    SerialNum syncedTo(0);

    EXPECT_TRUE(s1->sync(2, syncedTo));
    EXPECT_EQUAL(syncedTo, TOTAL_NUM_ENTRIES);
}

TEST("test truncate on version mismatch") {
    const unsigned int NUM_PACKETS = 3;
    const unsigned int NUM_ENTRIES = 4;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;

    uint64_t fromOld(0), toOld(0);
    size_t countOld(0);
    DummyFileHeaderContext fileHeaderContext;
    {
        TransLogServer tlss("test11", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x1000000));
        TransLogClient tls("tcp/localhost:18377");

        createDomainTest(tls, "sync", 0);
        auto s1 = openDomainTest(tls, "sync");
        fillDomainTest(s1.get(), NUM_PACKETS, NUM_ENTRIES);
        EXPECT_TRUE(s1->status(fromOld, toOld, countOld));
        SerialNum syncedTo(0);

        EXPECT_TRUE(s1->sync(2, syncedTo));
        EXPECT_EQUAL(syncedTo, TOTAL_NUM_ENTRIES);
    }
    FastOS_File f("test11/sync/sync-0000000000000000");
    EXPECT_TRUE(f.OpenWriteOnlyExisting());
    EXPECT_TRUE(f.SetPosition(f.GetSize()));
   
    char tmp[100];
    memset(tmp, 0, sizeof(tmp));
    EXPECT_EQUAL(static_cast<ssize_t>(sizeof(tmp)), f.Write2(tmp, sizeof(tmp)));
    EXPECT_TRUE(f.Close());
    {
        TransLogServer tlss("test11", 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
        TransLogClient tls("tcp/localhost:18377");
        auto s1 = openDomainTest(tls, "sync");
        uint64_t from(0), to(0);
        size_t count(0);
        EXPECT_TRUE(s1->status(from, to, count));
        ASSERT_EQUAL(fromOld, from);
        ASSERT_EQUAL(toOld, to);
        ASSERT_EQUAL(countOld, count);
    }
}

TEST("test truncation after short read") {
    const unsigned int NUM_PACKETS = 17;
    const unsigned int NUM_ENTRIES = 1;
    const unsigned int TOTAL_NUM_ENTRIES = NUM_PACKETS * NUM_ENTRIES;
    const unsigned int ENTRYSIZE = 4080;
    vespalib::string topdir("test10");
    vespalib::string domain("truncate");
    vespalib::string dir(topdir + "/" + domain);
    vespalib::string tlsspec("tcp/localhost:18377");

    DummyFileHeaderContext fileHeaderContext;
    {
        TransLogServer tlss(topdir, 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
        TransLogClient tls(tlsspec);
        
        createDomainTest(tls, domain, 0);
        auto s1 = openDomainTest(tls, domain);
        fillDomainTest(s1.get(), NUM_PACKETS, NUM_ENTRIES, ENTRYSIZE);
        
        SerialNum syncedTo(0);
        
        EXPECT_TRUE(s1->sync(TOTAL_NUM_ENTRIES, syncedTo));
        EXPECT_EQUAL(syncedTo, TOTAL_NUM_ENTRIES);
    }
    {
        EXPECT_EQUAL(2u, countFiles(dir));
    }
    {
        TransLogServer tlss(topdir, 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
        TransLogClient tls(tlsspec);
        auto s1 = openDomainTest(tls, domain);
        checkFilledDomainTest(*s1, TOTAL_NUM_ENTRIES);
    }
    {
        EXPECT_EQUAL(2u, countFiles(dir));
    }
    {
        vespalib::string filename(dir + "/truncate-0000000000000017");
        FastOS_File trfile(filename.c_str());
        EXPECT_TRUE(trfile.OpenReadWrite(nullptr));
        trfile.SetSize(trfile.getSize() - 1);
        trfile.Close();
    }
    {
        TransLogServer tlss(topdir, 18377, ".", fileHeaderContext, DomainConfig().setPartSizeLimit(0x10000));
        TransLogClient tls(tlsspec);
        auto s1 = openDomainTest(tls, domain);
        checkFilledDomainTest(*s1, TOTAL_NUM_ENTRIES - 1);
    }
    {
        EXPECT_EQUAL(2u, countFiles(dir));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
