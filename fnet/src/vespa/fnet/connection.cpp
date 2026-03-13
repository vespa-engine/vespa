// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connection.h"

#include "channel.h"
#include "config.h"
#include "controlpacket.h"
#include "dummypacket.h"
#include "ipacketstreamer.h"
#include "iserveradapter.h"
#include "transport.h"
#include "transport_thread.h"

#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/net/socket_spec.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

std::atomic<uint64_t> FNET_Connection::_num_connections = 0;

namespace {
class SyncPacket : public FNET_DummyPacket {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _done;
    bool                    _waiting;

public:
    SyncPacket() : _lock(), _cond(), _done(false), _waiting(false) {}

    ~SyncPacket() override;

    void WaitFree() {
        std::unique_lock<std::mutex> guard(_lock);
        _waiting = true;
        while (!_done)
            _cond.wait(guard);
        _waiting = false;
    }

    void Free() override;
};

SyncPacket::~SyncPacket() = default;

void SyncPacket::Free() {
    std::lock_guard<std::mutex> guard(_lock);
    _done = true;
    if (_waiting) {
        _cond.notify_one();
    }
}

struct DoHandshakeWork : vespalib::Executor::Task {
    FNET_Connection*        conn;
    vespalib::CryptoSocket* socket;
    DoHandshakeWork(FNET_Connection* conn_in, vespalib::CryptoSocket* socket_in) : conn(conn_in), socket(socket_in) {
        conn->internal_addref();
    }
    void run() override {
        socket->do_handshake_work();
        conn->Owner()->handshake_act(conn, false);
        conn = nullptr; // ref given away above
    }
    ~DoHandshakeWork() override;
};

DoHandshakeWork::~DoHandshakeWork() { assert(conn == nullptr); }

} // namespace

FNET_Connection::ResolveHandler::ResolveHandler(FNET_Connection* conn) noexcept : connection(conn), address() {
    connection->internal_addref();
}

void FNET_Connection::ResolveHandler::handle_result(vespalib::SocketAddress result) {
    address = result;
    connection->Owner()->Add(connection);
}

FNET_Connection::ResolveHandler::~ResolveHandler() { connection->internal_subref(); }

///////////////////////
// PROTECTED METHODS //
///////////////////////

const char* FNET_Connection::GetStateString(State state) {
    switch (state) {
    case FNET_CONNECTING:
        return "CONNECTING";
    case FNET_CONNECTED:
        return "CONNECTED";
    case FNET_CLOSING:
        return "CLOSING";
    case FNET_CLOSED:
        return "CLOSED";
    default:
        return "ILLEGAL";
    }
}

void FNET_Connection::SetState(State state) {
    State oldstate;

    std::vector<FNET_Channel::UP> toDelete;
    std::unique_lock<std::mutex>  guard(_ioc_lock);
    oldstate = GetState();
    _state.store(state, std::memory_order_relaxed);
    if (LOG_WOULD_LOG(debug) && state != oldstate) {
        LOG(debug, "Connection(%s): State transition: %s -> %s", GetSpec(), GetStateString(oldstate),
            GetStateString(state));
    }
    if (oldstate < FNET_CLOSING && state >= FNET_CLOSING) {

        while (!_queue.IsEmpty_NoLock() || !_myQueue.IsEmpty_NoLock()) {
            _flags._discarding = true;
            _queue.FlushPackets_NoLock(&_myQueue);
            guard.unlock();
            _myQueue.DiscardPackets_NoLock();
            guard.lock();
            _flags._discarding = false;
        }

        BeforeCallback(guard, nullptr);
        toDelete = _channels.Broadcast(&FNET_ControlPacket::ChannelLost);
        AfterCallback(guard);
    }

    if (!toDelete.empty()) {
        const uint32_t cnt = toDelete.size();
        const uint32_t reserve = 1;
        internal_subref(cnt, reserve);
    }
}

void FNET_Connection::HandlePacket(uint32_t plen, uint32_t pcode, uint32_t chid) {
    FNET_Packet*                    packet;
    FNET_Channel*                   channel;
    FNET_IPacketHandler::HP_RetCode hp_rc;

    std::unique_lock<std::mutex> guard(_ioc_lock);
    channel = _channels.Lookup(chid);

    if (channel != nullptr) { // deliver packet on open channel
        channel->prefetch();  // Prefetch in the shadow of the lock operation in BeforeCallback.
        __builtin_prefetch(&_streamer);
        __builtin_prefetch(&_input);

        BeforeCallback(guard, channel);
        __builtin_prefetch(channel->GetHandler(), 0); // Prefetch the handler while packet is being decoded.
        packet = _streamer->Decode(&_input, plen, pcode, channel->GetContext());
        hp_rc = (packet != nullptr) ? channel->Receive(packet) : channel->Receive(&FNET_ControlPacket::BadPacket);
        AfterCallback(guard);

        FNET_Channel::UP toDelete;
        if (hp_rc > FNET_IPacketHandler::FNET_KEEP_CHANNEL) {
            _channels.Unregister(channel);

            if (hp_rc == FNET_IPacketHandler::FNET_FREE_CHANNEL) {
                internal_subref(1, 1);
                toDelete.reset(channel);
            }
        }
    } else if (CanAcceptChannels() && IsFromPeer(chid)) { // open new channel
        FNET_Channel::UP newChannel(new FNET_Channel(chid, this));
        channel = newChannel.get();
        internal_addref();
        BeforeCallback(guard, channel);

        if (_serverAdapter->InitChannel(channel, pcode)) {

            packet = _streamer->Decode(&_input, plen, pcode, channel->GetContext());
            hp_rc = (packet != nullptr) ? channel->Receive(packet) : channel->Receive(&FNET_ControlPacket::BadPacket);
            AfterCallback(guard);

            if (hp_rc == FNET_IPacketHandler::FNET_FREE_CHANNEL) {
                internal_subref(1, 1);
            } else if (hp_rc == FNET_IPacketHandler::FNET_KEEP_CHANNEL) {
                _channels.Register(newChannel.release());
            } else {
                newChannel.release(); // It has already been taken care of, so we should not free it here.
            }
        } else {

            AfterCallback(guard);
            internal_subref(1, 1);
            guard.unlock();

            LOG(debug, "Connection(%s): channel init failed", GetSpec());
            _input.DataToDead(plen);
        }

    } else { // skip unhandled packet

        guard.unlock();
        LOG(spam, "Connection(%s): skipping unhandled packet", GetSpec());
        _input.DataToDead(plen);
    }
}

bool FNET_Connection::handshake() {
    bool broken = false;
    if (_flags._handshake_work_pending) {
        return !broken;
    }
    switch (_socket->handshake()) {
    case vespalib::CryptoSocket::HandshakeResult::FAIL:
        LOG(debug, "Connection(%s): handshake failed with peer %s", GetSpec(), GetPeerSpec().c_str());
        SetState(FNET_CLOSED);
        broken = true;
        break;
    case vespalib::CryptoSocket::HandshakeResult::DONE: {
        LOG(debug, "Connection(%s): handshake done with peer %s", GetSpec(), GetPeerSpec().c_str());
        _auth_context = _socket->make_auth_context();
        assert(_auth_context);
        EnableReadEvent(true);
        EnableWriteEvent(writePendingAfterConnect());
        _flags._framed = (_socket->min_read_buffer_size() > 1);
        size_t  chunk_size = std::max(size_t(FNET_READ_SIZE), _socket->min_read_buffer_size());
        ssize_t res = 0;
        do { // drain input pipeline
            _input.EnsureFree(chunk_size);
            res = _socket->drain(_input.GetFree(), _input.GetFreeLen());
            if (res > 0) {
                _input.FreeToData((uint32_t)res);
                broken = !handle_packets();
                _input.resetIfEmpty();
            }
        } while ((res > 0) && !broken);
    } break;
    case vespalib::CryptoSocket::HandshakeResult::NEED_READ:
        EnableReadEvent(true);
        EnableWriteEvent(false);
        break;
    case vespalib::CryptoSocket::HandshakeResult::NEED_WRITE:
        EnableReadEvent(false);
        EnableWriteEvent(true);
        break;
    case vespalib::CryptoSocket::HandshakeResult::NEED_WORK:
        EnableReadEvent(false);
        EnableWriteEvent(false);
        assert(!_flags._handshake_work_pending);
        _flags._handshake_work_pending = true;
        Owner()->owner().post_or_perform(std::make_unique<DoHandshakeWork>(this, _socket.get()));
    }
    return !broken;
}

bool FNET_Connection::handle_packets() {
    bool broken = false;
    for (bool done = false; !done;) { // handle each complete packet in the buffer.
        if (!_flags._gotheader) {
            _flags._gotheader =
                _streamer->GetPacketInfo(&_input, &_packetLength, &_packetCode, &_packetCHID, &broken);
        }
        if (_flags._gotheader && (_input.GetDataLen() >= _packetLength)) {
            HandlePacket(_packetLength, _packetCode, _packetCHID);
            _flags._gotheader = false; // reset header flag.
        } else {
            done = true;
        }
    }
    return !broken;
}

bool FNET_Connection::Read() {
    size_t  chunk_size = std::max(size_t(FNET_READ_SIZE), _socket->min_read_buffer_size());
    int     readCnt = 0;    // read count
    bool    broken = false; // is this conn broken ?
    int     my_errno = 0;   // sample and preserve errno
    ssize_t res;            // single read result

    _input.EnsureFree(chunk_size);
    res = _socket->read(_input.GetFree(), _input.GetFreeLen());
    my_errno = errno;
    readCnt++;

    while (res > 0) {
        _input.FreeToData((uint32_t)res);
        broken = !handle_packets();
        _input.resetIfEmpty();
        if (broken || ((_input.GetFreeLen() > 0) && !_flags._framed) || (readCnt >= FNET_READ_REDO)) {
            goto done_read;
        }
        _input.EnsureFree(chunk_size);
        res = _socket->read(_input.GetFree(), _input.GetFreeLen());
        my_errno = errno;
        readCnt++;
    }

done_read:

    while ((res > 0) && !broken) { // drain input pipeline
        _input.EnsureFree(chunk_size);
        res = _socket->drain(_input.GetFree(), _input.GetFreeLen());
        my_errno = errno;
        if (res > 0) {
            _input.FreeToData((uint32_t)res);
            broken = !handle_packets();
            _input.resetIfEmpty();
        } else if (res == 0) { // fully drained -> EWOULDBLOCK
            my_errno = EWOULDBLOCK;
            res = -1;
        }
    }

    UpdateTimeOut();
    if (_flags._drop_empty_buffers) {
        _socket->drop_empty_buffers();
        _input.Shrink(0);
    }
    uint32_t maxSize = getConfig()._maxInputBufferSize;
    if (maxSize > 0 && _input.GetBufSize() > maxSize) {
        if (!_flags._gotheader || _packetLength < maxSize) {
            _input.Shrink(maxSize);
        }
    }

    if (res <= 0) {
        if (res == 0) {
            broken = true; // handle EOF
        } else {           // res < 0
            broken = ((my_errno != EWOULDBLOCK) && (my_errno != EAGAIN));
            if (broken && (my_errno != ECONNRESET)) {
                LOG(debug, "Connection(%s): read error: %d", GetSpec(), my_errno);
            }
        }
    }

    return !broken;
}

bool FNET_Connection::Write() {
    size_t   chunk_size = std::max(size_t(FNET_WRITE_SIZE), _socket->min_read_buffer_size());
    uint32_t my_write_work = 0;
    int      writeCnt = 0;   // write count
    bool     broken = false; // is this conn broken ?
    int      my_errno = 0;   // sample and preserve errno
    ssize_t  res;            // single write result

    FNET_Packet* packet;
    FNET_Context context;

    do {

        // fill output buffer

        while (_output.GetDataLen() < chunk_size) {
            if (_myQueue.IsEmpty_NoLock())
                break;

            packet = _myQueue.DequeuePacket_NoLock(&context);
            if (packet->IsRegularPacket()) { // ignore non-regular packets
                _streamer->Encode(packet, context._value.INT, &_output);
            }
            packet->Free();
        }

        if (_output.GetDataLen() == 0) {
            res = 0;
            break;
        }

        // write data

        res = _socket->write(_output.GetData(), _output.GetDataLen());
        my_errno = errno;
        writeCnt++;
        if (res > 0) {
            _output.DataToDead((uint32_t)res);
            _output.resetIfEmpty();
        }
    } while (res > 0 && _output.GetDataLen() == 0 && !_myQueue.IsEmpty_NoLock() && writeCnt < FNET_WRITE_REDO);

    if ((_output.GetDataLen() > 0)) {
        ++my_write_work;
    }

    if (res >= 0) { // flush output pipeline
        res = _socket->flush();
        my_errno = errno;
        while (res > 0) {
            res = _socket->flush();
            my_errno = errno;
        }
    }

    if (_flags._drop_empty_buffers) {
        _socket->drop_empty_buffers();
        _output.Shrink(0);
    }
    uint32_t maxSize = getConfig()._maxOutputBufferSize;
    if (maxSize > 0 && _output.GetBufSize() > maxSize) {
        _output.Shrink(maxSize);
    }

    if (res < 0) {
        if ((my_errno == EWOULDBLOCK) || (my_errno == EAGAIN)) {
            ++my_write_work; // incomplete write/flush
        } else {
            broken = true;
        }
        if (broken && (my_errno != ECONNRESET)) {
            LOG(debug, "Connection(%s): write error: %d", GetSpec(), my_errno);
        }
    }

    std::unique_lock<std::mutex> guard(_ioc_lock);
    _writeWork = _queue.GetPacketCnt_NoLock() + _myQueue.GetPacketCnt_NoLock() + my_write_work;
    bool writePending = (_writeWork > 0);

    guard.unlock();
    if (!writePending)
        EnableWriteEvent(false);

    return !broken;
}

////////////////////
// PUBLIC METHODS //
////////////////////

FNET_Connection::FNET_Connection(FNET_TransportThread* owner, FNET_IPacketStreamer* streamer,
                                 FNET_IServerAdapter* serverAdapter, vespalib::SocketHandle socket, const char* spec)
    : FNET_IOComponent(owner, socket.get(), spec, /* time-out = */ true),
      _streamer(streamer),
      _serverAdapter(serverAdapter),
      _socket(owner->owner().create_server_crypto_socket(std::move(socket))),
      _resolve_handler(),
      _context(),
      _state(FNET_CONNECTING),
      _flags(owner->owner().getConfig()),
      _packetLength(0),
      _packetCode(0),
      _packetCHID(0),
      _writeWork(0),
      _currentID(1), // <-- NB
      _input(0),
      _queue(256),
      _myQueue(256),
      _output(0),
      _channels(),
      _callbackTarget(nullptr) {
    assert(_socket && (_socket->get_fd() >= 0));
    _num_connections.fetch_add(1, std::memory_order_relaxed);
}

FNET_Connection::FNET_Connection(FNET_TransportThread* owner, FNET_IPacketStreamer* streamer,
                                 FNET_IServerAdapter* serverAdapter, FNET_Context context, const char* spec)
    : FNET_IOComponent(owner, -1, spec, /* time-out = */ true),
      _streamer(streamer),
      _serverAdapter(serverAdapter),
      _socket(),
      _resolve_handler(),
      _context(context),
      _state(FNET_CONNECTING),
      _flags(owner->owner().getConfig()),
      _packetLength(0),
      _packetCode(0),
      _packetCHID(0),
      _writeWork(0),
      _currentID(0),
      _input(0),
      _queue(256),
      _myQueue(256),
      _output(0),
      _channels(),
      _callbackTarget(nullptr) {
    _num_connections.fetch_add(1, std::memory_order_relaxed);
}

FNET_Connection::~FNET_Connection() {
    assert(!_resolve_handler);
    _num_connections.fetch_sub(1, std::memory_order_relaxed);
}

bool FNET_Connection::Init() {
    // set up relevant events
    EnableReadEvent(true);
    EnableWriteEvent(true);

    // initiate async resolve
    if (IsClient()) {
        _resolve_handler = std::make_shared<ResolveHandler>(this);
        Owner()->owner().resolve_async(GetSpec(), _resolve_handler);
    }
    return true;
}

FNET_IServerAdapter* FNET_Connection::server_adapter() { return _serverAdapter; }

bool FNET_Connection::handle_add_event() {
    if (_resolve_handler) {
        auto tweak = [this](vespalib::SocketHandle& handle) { return Owner()->tune(handle); };
        _socket = Owner()->owner().create_client_crypto_socket(
            _resolve_handler->address.connect(tweak), vespalib::SocketSpec(GetSpec()));
        _ioc_socket_fd = _socket->get_fd();
        _resolve_handler.reset();
    }
    return (_socket && (_socket->get_fd() >= 0));
}

bool FNET_Connection::handle_handshake_act() {
    assert(_flags._handshake_work_pending);
    _flags._handshake_work_pending = false;
    return ((GetState() == FNET_CONNECTING) && handshake());
}

FNET_Channel* FNET_Connection::OpenChannel(FNET_IPacketHandler* handler, FNET_Context context, uint32_t* chid) {
    FNET_Channel::UP newChannel(new FNET_Channel(FNET_NOID, this, handler, context));
    FNET_Channel*    ret = nullptr;

    std::unique_lock<std::mutex> guard(_ioc_lock);
    if (__builtin_expect(GetState() < FNET_CLOSING, true)) {
        newChannel->SetID(GetNextID());
        if (chid != nullptr) {
            *chid = newChannel->GetID();
        }
        WaitCallback(guard, nullptr);
        internal_addref();
        ret = newChannel.release();
        _channels.Register(ret);
    }
    return ret;
}

FNET_Channel* FNET_Connection::OpenChannel() {

    uint32_t chid;
    {
        std::lock_guard<std::mutex> guard(_ioc_lock);
        chid = GetNextID();
        internal_addref();
    }
    return new FNET_Channel(chid, this);
}

bool FNET_Connection::CloseChannel(FNET_Channel* channel) {
    std::unique_lock<std::mutex> guard(_ioc_lock);
    WaitCallback(guard, channel);
    return _channels.Unregister(channel);
}

void FNET_Connection::FreeChannel(FNET_Channel* channel) {
    delete channel;
    internal_subref();
}

void FNET_Connection::CloseAndFreeChannel(FNET_Channel* channel) {
    {
        std::unique_lock<std::mutex> guard(_ioc_lock);
        WaitCallback(guard, channel);
        _channels.Unregister(channel);
        delete channel;
    }
    internal_subref();
}

bool FNET_Connection::PostPacket(FNET_Packet* packet, uint32_t chid) {
    uint32_t writeWork;

    assert(packet != nullptr);
    std::unique_lock<std::mutex> guard(_ioc_lock);
    if (GetState() >= FNET_CLOSING) {
        if (_flags._discarding) {
            _queue.QueuePacket_NoLock(packet, FNET_Context(chid));
        } else {
            guard.unlock();
            packet->Free(); // discard packet
        }
        return false; // connection is down
    }
    writeWork = _writeWork;
    _writeWork++;
    _queue.QueuePacket_NoLock(packet, FNET_Context(chid));
    if ((writeWork == 0) && (GetState() == FNET_CONNECTED)) {
        internal_addref();
        guard.unlock();
        Owner()->EnableWrite(this, /* needRef = */ false);
    }
    return true;
}

void FNET_Connection::Sync() {
    SyncPacket sp;
    PostPacket(&sp, FNET_NOID);
    sp.WaitFree();
}

void FNET_Connection::Close() {
    _resolve_handler.reset();
    detach_selector();
    SetState(FNET_CLOSED);
    _ioc_socket_fd = -1;
    if (!_flags._handshake_work_pending) {
        _socket.reset();
    }
}

bool FNET_Connection::HandleReadEvent() {
    bool broken = false; // is connection broken ?

    switch (GetState()) {
    case FNET_CONNECTING:
        broken = !handshake();
        break;
    case FNET_CONNECTED:
        broken = !Read();
        break;
    case FNET_CLOSING:
    case FNET_CLOSED:
    default:
        broken = true;
    }
    return !broken;
}

bool FNET_Connection::writePendingAfterConnect() {
    std::lock_guard<std::mutex> guard(_ioc_lock);
    _state.store(FNET_CONNECTED, std::memory_order_relaxed); // SetState(FNET_CONNECTED)
    LOG(debug, "Connection(%s): State transition: %s -> %s", GetSpec(), GetStateString(FNET_CONNECTING),
        GetStateString(FNET_CONNECTED));
    return (_writeWork > 0);
}

bool FNET_Connection::HandleWriteEvent() {
    bool broken = false; // is connection broken ?

    switch (GetState()) {
    case FNET_CONNECTING:
        broken = !handshake();
        break;
    case FNET_CONNECTED: {
        std::unique_lock<std::mutex> guard(_ioc_lock);
        _queue.FlushPackets_NoLock(&_myQueue);
    }
        broken = !Write();
        break;
    case FNET_CLOSING:
    case FNET_CLOSED:
    default:
        broken = true;
    }
    return !broken;
}

std::string FNET_Connection::GetPeerSpec() const {
    return vespalib::SocketAddress::peer_address(_socket->get_fd()).spec();
}

const vespalib::net::ConnectionAuthContext& FNET_Connection::auth_context() const noexcept {
    assert(_auth_context);
    return *_auth_context;
}
