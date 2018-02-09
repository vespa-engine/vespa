// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapnames.h"
#include "packets.h"
#include "sortdata.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/util/compressor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/databuffer.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.common.fs4packets");

using vespalib::ConstBufferRef;
using vespalib::make_string;
using vespalib::stringref;

namespace search::fs4transport {

/**
 * Persistent packet streamer.
 **/
FS4PersistentPacketStreamer FS4PersistentPacketStreamer::
Instance(FS4PacketFactory::CreateFS4Packet);

//============================================================

bool
FS4PersistentPacketStreamer::HasChannelID(uint32_t pcode)
{
    switch(pcode & PCODE_MASK) {
    case PCODE_EOL:
    case PCODE_ERROR:
    case PCODE_DOCSUM:
    case PCODE_QUERYRESULTX:
    case PCODE_QUERYX:
    case PCODE_GETDOCSUMSX:
    case PCODE_TRACEREPLY:
        return true;
    default:
        return false;
    }
}

FS4PersistentPacketStreamer::
FS4PersistentPacketStreamer(FS4PacketFactory::CreatePacket_t cp)
    : _compressionLimit(0),
      _compressionLevel(9),
      _compressionType(CompressionConfig::LZ4),
      _conservative(false),
      _createPacket(cp)
{ }

bool
FS4PersistentPacketStreamer::GetPacketInfo(FNET_DataBuffer *src,
                                           uint32_t *plen, uint32_t *pcode,
                                           uint32_t *chid, bool *broken)
{
    uint32_t tmpVal;
    bool hasCHID;

    if (src->GetDataLen() < 2 * sizeof(uint32_t) ||
        ((hasCHID = HasChannelID(src->PeekInt32(sizeof(uint32_t)))) &&
         src->GetDataLen() < 3 * sizeof(uint32_t)))
        return false;

    if (hasCHID) {
        tmpVal = src->ReadInt32();
        if (tmpVal < 2 * sizeof(uint32_t)) {
            // This is not a valid packet length. We might
            // be out of sync.
            *broken = _conservative;
            if (*broken) {
                LOG(warning, "Out of sync! Invalid packet length %u\n", tmpVal);
            }
            return false;
        } else {
            *plen  = tmpVal - 2 * sizeof(uint32_t);
        }
        tmpVal = src->ReadInt32();
        if (!ValidPCode(tmpVal)) {
            // Out of sync?
            *broken = _conservative;
            if (*broken) {
                LOG(warning, "Out of sync! Invalid pcode %u (%u)\n", tmpVal, *plen);
            }
            return false;
        } else {
            *pcode = tmpVal;
        }
        *chid  = src->ReadInt32();
    } else {
        tmpVal = src->ReadInt32();
        if (tmpVal < sizeof(uint32_t)) {
            // This is not a valid packet length. We might
            // be out of sync.
            *broken = _conservative;
            if (*broken) {
                LOG(warning, "Out of sync! Invalid length (noch) %u\n", tmpVal);
            }
            return false;
        } else {
            *plen  = tmpVal - sizeof(uint32_t);
        }
        tmpVal = src->ReadInt32();
        if (!ValidPCode(tmpVal)) {
            // Out of sync?
            *broken = _conservative;
            if (*broken) {
                LOG(warning, "Out of sync! Invalid pcode (noch) %u (%u)\n", tmpVal, *plen);
            }
            return false;
        } else {
            *pcode = tmpVal;
        }
        *chid  = FNET_NOID;
    }
    return true;
}

namespace {

void decodePacket(FNET_Packet *&packet, FNET_DataBuffer &buf, uint32_t size, uint32_t pcode) {
    try {
        if (!packet->Decode(&buf, size)) {
            LOG(error, "could not decode packet (pcode=%u); "
                "this could be caused by a protocol and/or "
                "version incompatibility\n", pcode);
            packet->Free();
            packet = NULL;
        }
    } catch (const vespalib::Exception & e) {
        packet->Free();
        packet = NULL;
        LOG(error, "%s", e.toString().c_str());
    }
}

}  // namespace

FNET_Packet*
FS4PersistentPacketStreamer::Decode(FNET_DataBuffer *src, uint32_t plen, uint32_t pcode, FNET_Context)
{
    FNET_Packet *packet(_createPacket(pcode & PCODE_MASK));
    if (packet != NULL) {
        uint32_t compressionByte = (pcode & ~PCODE_MASK) >> 24;
        CompressionConfig::Type compressionType(CompressionConfig::toType(compressionByte));
        if (compressionType != 0) {
            uint32_t uncompressed_size = src->ReadInt32();
            ConstBufferRef org(src->GetData(), plen - sizeof(uint32_t));
            vespalib::DataBuffer uncompressed(uncompressed_size);
            vespalib::compression::decompress(compressionType, uncompressed_size, org, uncompressed, false);
            FNET_DataBuffer buf(uncompressed.getData(), uncompressed.getDataLen());
            decodePacket(packet, buf, uncompressed_size, pcode);
            src->DataToDead(plen - sizeof(uint32_t));
        } else {
            decodePacket(packet, *src, plen, pcode);
        }
    } else {
        src->DataToDead(plen);
    }
    return packet;
}


void
FS4PersistentPacketStreamer::Encode(FNET_Packet *packet, uint32_t chid, FNET_DataBuffer *dst)
{
    uint32_t len   = packet->GetLength();
    uint32_t pcode = packet->GetPCODE();

    uint32_t packet_start = dst->GetDataLen();
    if (HasChannelID(pcode)) {
        dst->EnsureFree(len + 3 * sizeof(uint32_t));
        dst->WriteInt32Fast(len + 2 * sizeof(uint32_t));
        dst->WriteInt32Fast(pcode);
        dst->WriteInt32Fast(chid);
    } else {
        dst->EnsureFree(len + 2 * sizeof(uint32_t));
        dst->WriteInt32Fast(len + sizeof(uint32_t));
        dst->WriteInt32Fast(pcode);
    }
    uint32_t header_len = dst->GetDataLen() - packet_start;
    packet->Encode(dst);
    dst->AssertValid();
    uint32_t body_len = dst->GetDataLen() - packet_start - header_len;
    bool isCompressable((pcode & ~PCODE_MASK) == 0);

    if (isCompressable && _compressionLimit && (body_len > _compressionLimit)) {
        CompressionConfig config(_compressionType, _compressionLevel, 90);
        ConstBufferRef org(dst->GetData() + packet_start + header_len, body_len);
        vespalib::DataBuffer compressed(org.size());
        CompressionConfig::Type r = vespalib::compression::compress(config, org, compressed, false);
        if (r != CompressionConfig::NONE) {
            dst->DataToFree(body_len + header_len);
            // sizeof(data + header + uncompressed_size) - sizeof(uint32_t)
            dst->WriteInt32Fast(compressed.getDataLen() + header_len);
            dst->WriteInt32Fast(pcode | (_compressionType << 24));
            if (HasChannelID(pcode)) {
                dst->FreeToData(sizeof(uint32_t));  // channel
            }
            dst->WriteInt32Fast(body_len);
            dst->WriteBytes(compressed.getData(), compressed.getDataLen());
            dst->AssertValid();
        }
    }
}

//============================================================

FS4Properties::FS4Properties()
    : _entries(),
      _name(),
      _backing()
{ }

FS4Properties::FS4Properties(FS4Properties && rhs)
    : _entries(std::move(rhs._entries)),
      _name(std::move(rhs._name)),
      _backing(std::move(rhs._backing))
{ }

FS4Properties &
FS4Properties::operator=(FS4Properties && rhs)
{
    _entries = std::move(rhs._entries);
    _name = std::move(rhs._name);
    _backing = std::move(rhs._backing);
    return *this;
}

FS4Properties::~FS4Properties() { }

void
FS4Properties::allocEntries(uint32_t cnt)
{
    _entries.resize(cnt);
    _backing.reserve(cnt*2*40); // Assume strings are average 40 bytes
}

void FS4Properties::set(StringRef & e, const vespalib::stringref & s)
{
    e.first = _backing.size();
    e.second = s.size();
    _backing.append(s.c_str(), s.size());
}

void
FS4Properties::setKey(uint32_t entry, const char *key, uint32_t keySize)
{
    set(_entries[entry].first, vespalib::stringref(key, keySize));
}

void
FS4Properties::setValue(uint32_t entry, const char *value, uint32_t valueSize)
{
    set(_entries[entry].second, vespalib::stringref(value, valueSize));
}

uint32_t
FS4Properties::getLength()
{
    uint32_t len = sizeof(uint32_t) * 2 + getNameLen();
    len += _backing.size();
    len += _entries.size() * sizeof(uint32_t) * 2;
    return len;
}

void
FS4Properties::encode(FNET_DataBuffer &dst)
{
    dst.WriteInt32Fast(_name.size());
    dst.WriteBytesFast(_name.c_str(), _name.size());
    dst.WriteInt32Fast(size());
    for (uint32_t i = 0; i < size(); ++i) {
        dst.WriteInt32Fast(getKeyLen(i));
        dst.WriteBytesFast(getKey(i), getKeyLen(i));
        dst.WriteInt32Fast(getValueLen(i));
        dst.WriteBytesFast(getValue(i), getValueLen(i));
    }
}

bool
FS4Properties::decode(FNET_DataBuffer &src, uint32_t &len)
{
    uint32_t strLen;
    if (len < sizeof(uint32_t)) return false;
    strLen = src.ReadInt32();
    len -= sizeof(uint32_t);
    if (len < strLen) return false;
    setName(src.GetData(), strLen);
    src.DataToDead(strLen);
    len -= strLen;
    if (len < sizeof(uint32_t)) return false;
    uint32_t cnt = src.ReadInt32();
    len -= sizeof(uint32_t);
    allocEntries(cnt);
    for (uint32_t i = 0; i < cnt; ++i) {
        if (len < sizeof(uint32_t)) return false;
        strLen = src.ReadInt32();
        len -= sizeof(uint32_t);
        if (len < strLen) return false;
        setKey(i, src.GetData(), strLen);
        src.DataToDead(strLen);
        len -= strLen;
        if (len < sizeof(uint32_t)) return false;
        strLen = src.ReadInt32();
        len -= sizeof(uint32_t);
        if (len < strLen) return false;
        setValue(i, src.GetData(), strLen);
        src.DataToDead(strLen);
        len -= strLen;
    }
    return true;
}

vespalib::string
FS4Properties::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sProperties {\n", indent, "");
    s += make_string("%*s  name: ", indent, "");
    s += _name;
    s += "\n";
    for (uint32_t i = 0; i < size(); ++i) {
        s += make_string("%*s  Entry[%d] {\n", indent, "", i);
        s += make_string("%*s    key  : %s\n", indent, "", vespalib::string(getKey(i), getKeyLen(i)).c_str());
        s += make_string("%*s    value: %s\n", indent, "", vespalib::string(getValue(i), getValueLen(i)).c_str());
        s += make_string("%*s  }\n", indent, "");
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

/**
 * Write a string in usual format to a buffer.  Usual format is first
 * a 32-bit integer holding the string length, then the bytes that the
 * string contained.  Skip checking for free space.
 *
 * @param buf buffer to write to
 * @param str string to write, of any type that has c_str() and size()
 **/
template<typename STR>
void
writeLenString(FNET_DataBuffer *buf, const STR &str)
{
    buf->WriteInt32Fast(str.size());
    buf->WriteBytesFast(str.c_str(), str.size());
}

//============================================================

FS4Packet::FS4Packet()
    : FNET_Packet()
{ }

FS4Packet::~FS4Packet() { }

void
FS4Packet::Free() {
    delete this;
}

vespalib::string
FS4Packet::Print(uint32_t indent) {
    return toString(indent);
}

//============================================================

FS4Packet_EOL::FS4Packet_EOL()
    : FS4Packet()
{ }

FS4Packet_EOL::~FS4Packet_EOL() { }

uint32_t
FS4Packet_EOL::GetLength() {
    return 0;
}

void
FS4Packet_EOL::Encode(FNET_DataBuffer *dst) {
    (void) dst;
}

bool
FS4Packet_EOL::Decode(FNET_DataBuffer *src, uint32_t len)
{
    src->DataToDead(len);
    return (len == 0);
}

vespalib::string
FS4Packet_EOL::toString(uint32_t indent) const
{
    return make_string("%*sFS4Packet_EOL {}\n", indent, "");
}

//============================================================

FS4Packet_Shared::FS4Packet_Shared(FNET_Packet::SP packet)
    : FS4Packet(),
      _packet(std::move(packet))
{ }

FS4Packet_Shared::~FS4Packet_Shared() { }

uint32_t
FS4Packet_Shared::GetPCODE() {
    return _packet->GetPCODE();
}

uint32_t
FS4Packet_Shared::GetLength() {
    return _packet->GetLength();
}

void
FS4Packet_Shared::Encode(FNET_DataBuffer *dst) {
    _packet->Encode(dst);
}

bool
FS4Packet_Shared::Decode(FNET_DataBuffer *, uint32_t ) {
    abort();
}

vespalib::string
FS4Packet_Shared::toString(uint32_t indent) const
{
    return _packet->Print(indent);
}

//============================================================

FS4Packet_PreSerialized::FS4Packet_PreSerialized(FNET_Packet & packet)
    : FS4Packet(),
      _pcode(packet.GetPCODE()),
      _compressionType(CompressionConfig::NONE),
      _data(packet.GetLength() + 1*sizeof(uint32_t))
{
    const uint32_t body_len(packet.GetLength());
    const uint32_t compressionLimit=FS4PersistentPacketStreamer::Instance.getCompressionLimit();
    if (compressionLimit && (body_len > compressionLimit)) {
        FNET_DataBuffer tmp(packet.GetLength());
        packet.Encode(&tmp);
        tmp.AssertValid();
        CompressionConfig config(FS4PersistentPacketStreamer::Instance.getCompressionType(),
                                 FS4PersistentPacketStreamer::Instance.getCompressionLevel(),
                                 90);
        ConstBufferRef org(tmp.GetData(), tmp.GetDataLen());
        vespalib::DataBuffer compressed(org.size());
        _compressionType = vespalib::compression::compress(config, org, compressed, false);
        if (_compressionType != CompressionConfig::NONE) {
            _data.WriteInt32Fast(body_len);
            _data.WriteBytes(compressed.getData(), compressed.getDataLen());
            _data.AssertValid();
        } else {
            packet.Encode(&_data);
        }
    } else {
        packet.Encode(&_data);
    }
}

FS4Packet_PreSerialized::~FS4Packet_PreSerialized() { }

uint32_t
FS4Packet_PreSerialized::GetPCODE()
{
    return ((_compressionType == CompressionConfig::NONE)
               ? _pcode
               : (_pcode | (_compressionType << 24)));
}

uint32_t
FS4Packet_PreSerialized::GetLength()
{
    return _data.GetDataLen();
}

void
FS4Packet_PreSerialized::Encode(FNET_DataBuffer *dst)
{
    dst->WriteBytes(_data.GetData(), _data.GetDataLen());
}

bool
FS4Packet_PreSerialized::Decode(FNET_DataBuffer *, uint32_t)
{
    abort();
}

vespalib::string
FS4Packet_PreSerialized::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_PreSerialized {\n", indent, "");
    s += make_string("%*s  length : %d\n", indent, "", _data.GetDataLen());
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

FS4Packet_ERROR::FS4Packet_ERROR()
    : FS4Packet(),
      _errorCode(0),
      _message()
{ }

FS4Packet_ERROR::~FS4Packet_ERROR() { }

uint32_t
FS4Packet_ERROR::GetLength()
{
    return 2 * sizeof(uint32_t) + _message.size();
}

void
FS4Packet_ERROR::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_errorCode);
    writeLenString(dst, _message);
}

bool
FS4Packet_ERROR::Decode(FNET_DataBuffer *src, uint32_t len)
{
    if (len < sizeof(uint32_t) * 2) {
        src->DataToDead(len);
        return false;
    }
    _errorCode = src->ReadInt32();
    uint32_t messageLen = src->ReadInt32();
    len -= 2 * sizeof(uint32_t);
    if (len != messageLen) {
        src->DataToDead(len);
        return false;
    }
    setErrorMessage(stringref(src->GetData(), messageLen));
    src->DataToDead(messageLen);
    return true;
}


vespalib::string
FS4Packet_ERROR::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_ERROR {\n", indent, "");
    s += make_string("%*s  errorCode : %d\n", indent, "", _errorCode);
    s += make_string("%*s  message   : %s\n", indent, "", _message.c_str());
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

void
FS4Packet_DOCSUM::SetBuf(const char *buf, uint32_t len)
{
    _buf.resize(len);
    memcpy(_buf.str(), buf, len);
}

FS4Packet_DOCSUM::FS4Packet_DOCSUM()
    : FS4Packet(),
      _gid(),
      _buf()
{ }

FS4Packet_DOCSUM::~FS4Packet_DOCSUM() { }

void
FS4Packet_DOCSUM::Encode(FNET_DataBuffer *dst)
{
    dst->WriteBytesFast(_gid.get(), document::GlobalId::LENGTH);
    dst->WriteBytesFast(_buf.c_str(), _buf.size());
}

bool
FS4Packet_DOCSUM::Decode(FNET_DataBuffer *src, uint32_t len)
{
    if (len < document::GlobalId::LENGTH) {
        src->DataToDead(len);
        return false;
    }
    unsigned char rawGid[document::GlobalId::LENGTH];
    src->ReadBytes(rawGid, document::GlobalId::LENGTH);
    _gid.set(rawGid);
    len -= document::GlobalId::LENGTH;
    SetBuf(src->GetData(), len);
    src->DataToDead(len);
    return true;
}

vespalib::string
FS4Packet_DOCSUM::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_DOCSUM {\n", indent, "");
    s += make_string("%*s  gid       : %s\n", indent, "", _gid.toString().c_str());

    uint32_t magic = SLIME_MAGIC_ID;
    if (_buf.size() >= sizeof(magic) &&
        memcmp(_buf.c_str(), &magic, sizeof(magic)) == 0) {
        vespalib::Slime slime;
        vespalib::Memory input(_buf.c_str() + sizeof(magic),
                                      _buf.size() - sizeof(magic));
        vespalib::SimpleBuffer buf;
        vespalib::slime::BinaryFormat::decode(input, slime);
        vespalib::slime::JsonFormat::encode(slime, buf, false);
        s += make_string("%*s  json dump : ", indent, "");
        s += buf.get().make_string();
    } else {
        s += make_string("%*s  data dump :\n", indent, "");
        const char *pt = _buf.c_str();
        uint32_t     i = 0;
        if ( ! _buf.empty())
            s += make_string("%*s  ", indent, "");
        while (i < _buf.size()) {
            s += make_string("%x ", (unsigned char) pt[i]);
            if ((++i % 16) == 0)
                s += make_string("\n%*s  ", indent, "");
        }
        if ((i % 16) != 0)
            s += make_string("\n");
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

FS4Packet_MONITORQUERYX::FS4Packet_MONITORQUERYX()
    : FS4Packet(),
      _features(0),
      _qflags(0u)
{
}


FS4Packet_MONITORQUERYX::~FS4Packet_MONITORQUERYX()
{
}


uint32_t
FS4Packet_MONITORQUERYX::GetLength()
{
    uint32_t plen = 0;

    plen += sizeof(uint32_t);
    if (_features & MQF_QFLAGS)
        plen += sizeof(uint32_t);
    return plen;
}


void
FS4Packet_MONITORQUERYX::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_features);

    if ((_features & MQF_QFLAGS) != 0)
        dst->WriteInt32Fast(_qflags);
}


bool
FS4Packet_MONITORQUERYX::Decode(FNET_DataBuffer *src, uint32_t len)
{
    if (len < sizeof(uint32_t))
        goto error;
    _features = src->ReadInt32();
    len -= sizeof(uint32_t);
    if ((_features & ~FNET_MQF_SUPPORTED_MASK) != 0)
        goto error;

    if ((_features & MQF_QFLAGS) != 0) {
        if (len < sizeof(uint32_t))
            goto error;
        _qflags = src->ReadInt32();
        len -= sizeof(uint32_t);
    }

    if (len != 0)
        goto error;

    return true;
 error:
    src->DataToDead(len);
    return false;           // FAIL
}


vespalib::string
FS4Packet_MONITORQUERYX::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_MONITORQUERYX {\n", indent, "");
    s += make_string("%*s  features    : 0x%x\n", indent, "", _features);
    s += make_string("%*s  qflags      : %d\n", indent, "", _qflags);
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

FS4Packet_MONITORRESULTX::FS4Packet_MONITORRESULTX()
    : FS4Packet(),
      _features(0),
      _partid(0),
      _timestamp(0),
      _totalNodes(0),
      _activeNodes(0),
      _totalParts(0),
      _activeParts(0),
      _rflags(0u),
      _activeDocs(0)
{ }

FS4Packet_MONITORRESULTX::~FS4Packet_MONITORRESULTX() { }

uint32_t
FS4Packet_MONITORRESULTX::GetLength()
{
    uint32_t plen = 2 * sizeof(uint32_t);

    plen += sizeof(uint32_t);
    if ((_features & MRF_MLD) != 0)
        plen +=  4 * sizeof(uint32_t);
    if ((_features & MRF_RFLAGS) != 0)
        plen += sizeof(uint32_t);
    if ((_features & MRF_ACTIVEDOCS) != 0)
        plen += sizeof(uint64_t);

    return plen;
}


void
FS4Packet_MONITORRESULTX::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_features);

    dst->WriteInt32Fast(_partid);
    dst->WriteInt32Fast(_timestamp);
    if ((_features & MRF_MLD) != 0) {
        dst->WriteInt32Fast(_totalNodes);
        dst->WriteInt32Fast(_activeNodes);
        dst->WriteInt32Fast(_totalParts);
        dst->WriteInt32Fast(_activeParts);
    }
    if ((_features & MRF_RFLAGS) != 0) {
        dst->WriteInt32Fast(_rflags);
    }
    if ((_features & MRF_ACTIVEDOCS) != 0) {
        dst->WriteInt64Fast(_activeDocs);
    }
}


bool
FS4Packet_MONITORRESULTX::Decode(FNET_DataBuffer *src, uint32_t len)
{
    if (len < sizeof(uint32_t)) goto error;
    _features = src->ReadInt32();
    len -= sizeof(uint32_t);
    if ((_features & ~FNET_MRF_SUPPORTED_MASK) != 0)
        goto error;

    if (len < 2 * sizeof(uint32_t))
        goto error;
    _partid     = src->ReadInt32();
    _timestamp   = src->ReadInt32();
    len -= 2 * sizeof(uint32_t);

    if ((_features & MRF_MLD) != 0) {
        if (len < 4 * sizeof(uint32_t))
            goto error;
        _totalNodes  = src->ReadInt32();
        _activeNodes = src->ReadInt32();
        _totalParts  = src->ReadInt32();
        _activeParts = src->ReadInt32();
        len -= 4 * sizeof(uint32_t);
    }

    if ((_features & MRF_RFLAGS) != 0) {
        if (len < sizeof(uint32_t))
            goto error;
        _rflags = src->ReadInt32();
        len -= sizeof(uint32_t);
    }

    if ((_features & MRF_ACTIVEDOCS) != 0) {
        if (len < sizeof(uint64_t))
            goto error;
        _activeDocs = src->ReadInt64();
        len -= sizeof(uint64_t);
    }

    if (len != 0)
        goto error;

    return true;            // OK
 error:
    src->DataToDead(len);
    return false;           // FAIL
}


vespalib::string
FS4Packet_MONITORRESULTX::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_MONITORRESULTX {\n", indent, "");
    s += make_string("%*s  features    : 0x%x\n", indent, "", _features);
    s += make_string("%*s  partid      : %d\n", indent, "", _partid);
    s += make_string("%*s  timestamp   : %d\n", indent, "", _timestamp);
    s += make_string("%*s  totalnodes  : %d\n", indent, "", _totalNodes);
    s += make_string("%*s  activenodes : %d\n", indent, "", _activeNodes);
    s += make_string("%*s  totalparts  : %d\n", indent, "", _totalParts);
    s += make_string("%*s  activeparts : %d\n", indent, "", _activeParts);
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

void
FS4Packet_QUERYRESULTX::AllocateSortIndex(uint32_t cnt)
{
    if (cnt == 0)
        return;

    cnt++; // end of data index entry
    _sortIndex = new uint32_t[cnt];
}


void
FS4Packet_QUERYRESULTX::AllocateSortData(uint32_t len)
{
    if (len == 0)
        return;

    _sortData = (char *) malloc(len);
}


void
FS4Packet_QUERYRESULTX::SetSortDataRef(uint32_t cnt,
                                       uint32_t *sortIndex,
                                       const char *sortData)
{
    if (cnt == 0)
        return;

    AllocateSortIndex(cnt);
    AllocateSortData(sortIndex[cnt] - sortIndex[0]);
    _sortIndex[0] = 0;
    search::common::SortData::Copy(cnt, _sortIndex, _sortData, sortIndex, sortData);
}


void
FS4Packet_QUERYRESULTX::AllocateGroupData(uint32_t len)
{
    if (len == 0)
        return;

    _groupData = (char *) malloc(len);
    _groupDataLen = len;
}


void
FS4Packet_QUERYRESULTX::SetGroupDataRef(const char *groupData,
                                        uint32_t len)
{
    if (len == 0)
        return;

    AllocateGroupData(len);
    memcpy(_groupData, groupData, len);
}


void
FS4Packet_QUERYRESULTX::AllocateHits(uint32_t cnt)
{
    if (cnt == 0)
        return;

    _hits = new FS4_hit[cnt];
    _numDocs = cnt;
}


FS4Packet_QUERYRESULTX::FS4Packet_QUERYRESULTX()
    : FS4Packet(),
      _distributionKey(0),
      _nodesQueried(1),
      _nodesReplied(1),
      _features(QRF_COVERAGE | QRF_EXTENDED_COVERAGE),
      _offset(0),
      _numDocs(0),
      _totNumDocs(0),
      _maxRank(0),
      _sortIndex(NULL),
      _sortData(NULL),
      _groupDataLen(0),
      _groupData(NULL),
      _coverageDocs(0),
      _activeDocs(0),
      _soonActiveDocs(0),
      _coverageDegradeReason(0),
      _hits(NULL),
      _propsVector()
{ }


FS4Packet_QUERYRESULTX::~FS4Packet_QUERYRESULTX()
{
    if (_sortIndex) { delete [] _sortIndex; }
    if (_sortData) { free(_sortData); }
    if (_groupData) { free(_groupData); }
    if (_hits) { delete [] _hits; }
}


uint32_t
FS4Packet_QUERYRESULTX::GetLength()
{
    uint32_t plen = 3 * sizeof(uint32_t) +
                    sizeof(uint64_t) + // hit count is now 64-bit
                    sizeof(search::HitRank) +
                    _numDocs * (sizeof(document::GlobalId) + sizeof(search::HitRank));

    plen += sizeof(uint32_t);
    plen += (_features & QRF_COVERAGE_NODES) ? (2 * sizeof(uint16_t)) : 0;
    plen += (_features & QRF_MLD) ? (_numDocs * 2 * sizeof(uint32_t)) : 0;
    plen += (_features & QRF_GROUPDATA) ? (sizeof(uint32_t) + _groupDataLen) : 0;
    plen += 3 * sizeof(uint64_t) + sizeof(uint32_t);

    if (((_features & QRF_SORTDATA) != 0) && (_numDocs > 0)) 
        plen += _numDocs * sizeof(uint32_t) + (_sortIndex[_numDocs] - _sortIndex[0]);

    if ((_features & QRF_PROPERTIES) != 0) {
        plen += sizeof(uint32_t);
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            plen += _propsVector[i].getLength();
        }
    }

    return plen;
}


void
FS4Packet_QUERYRESULTX::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_features);
    dst->WriteInt32Fast(_offset);
    dst->WriteInt32Fast(_numDocs);
    dst->WriteInt64Fast(_totNumDocs);
    union { uint64_t INT64; double DOUBLE; } mrval;
    mrval.DOUBLE = _maxRank;
    dst->WriteInt64Fast(mrval.INT64);
    dst->WriteInt32Fast(_distributionKey);

    if (_features & QRF_COVERAGE_NODES) {
        dst->WriteInt16Fast(_nodesQueried);
        dst->WriteInt16Fast(_nodesReplied);
    }

    if (((_features & QRF_SORTDATA) != 0) &&
        (_numDocs > 0))
    {
        uint32_t idx0 = _sortIndex[0];
        // implicit: first index entry always 0
        for (uint32_t i = 1; i <= _numDocs; i++) {
            dst->WriteInt32Fast(_sortIndex[i] - idx0);
        }
        dst->WriteBytesFast(_sortData + idx0, _sortIndex[_numDocs] - idx0);
    }

    if ((_features & QRF_GROUPDATA) != 0) {
        dst->WriteInt32Fast(_groupDataLen);
        dst->WriteBytesFast(_groupData, _groupDataLen);
    }

    dst->WriteInt64Fast(_coverageDocs);
    dst->WriteInt64Fast(_activeDocs);
    dst->WriteInt64Fast(_soonActiveDocs);
    dst->WriteInt32Fast(_coverageDegradeReason);


    for (uint32_t i = 0; i < _numDocs; i++) {
        dst->WriteBytesFast(_hits[i]._gid.get(), document::GlobalId::LENGTH);
        union { uint64_t INT64; double DOUBLE; } val;
        val.DOUBLE = _hits[i]._metric;
        dst->WriteInt64Fast(val.INT64);
        if ((_features & QRF_MLD) != 0) {
            dst->WriteInt32Fast(_hits[i]._partid);
            dst->WriteInt32Fast(_hits[i].getDistributionKey());
        }
    }

    if ((_features & QRF_PROPERTIES) != 0) {
        dst->WriteInt32Fast(_propsVector.size());
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            _propsVector[i].encode(*dst);
        }
    }
}


bool
FS4Packet_QUERYRESULTX::Decode(FNET_DataBuffer *src, uint32_t len)
{
    uint32_t hitSize = sizeof(document::GlobalId);

    if (len < sizeof(uint32_t)) goto error;
    _features = src->ReadInt32();
    len -= sizeof(uint32_t);

    if ((_features & ~FNET_QRF_SUPPORTED_MASK) != 0) {
        throwUnsupportedFeatures(_features, FNET_QRF_SUPPORTED_MASK);
    }
    hitSize += sizeof(uint64_t);

    if (len < 3 * sizeof(uint32_t) + sizeof(uint64_t) + sizeof(search::HitRank)) goto error;
    _offset     = src->ReadInt32();
    _numDocs    = src->ReadInt32();
    _totNumDocs = src->ReadInt64();
    union { uint64_t INT64; double DOUBLE; } mrval;
    mrval.INT64 = src->ReadInt64();
    _maxRank = mrval.DOUBLE;
    _distributionKey   = src->ReadInt32();
    len -= 3 * sizeof(uint32_t) + sizeof(uint64_t) + sizeof(search::HitRank);

    if (_features & QRF_COVERAGE_NODES) {
        if (len < 2* sizeof(uint16_t)) goto error;
        _nodesQueried = src->ReadInt16();
        _nodesReplied = src->ReadInt16();
        len -= 2*sizeof(uint16_t);
    }
    if (((_features & QRF_SORTDATA) != 0) && (_numDocs > 0)) {
        if (len < _numDocs * sizeof(uint32_t)) goto error;
        AllocateSortIndex(_numDocs);
        _sortIndex[0] = 0; // implicit
        for (uint32_t i = 1; i <= _numDocs; i++) {
            _sortIndex[i] = src->ReadInt32();
        }
        len -= _numDocs * sizeof(uint32_t);
        uint32_t sortDataLen = _sortIndex[_numDocs];

        if (len < sortDataLen) goto error;
        AllocateSortData(sortDataLen);
        src->ReadBytes(_sortData, sortDataLen);
        len -= sortDataLen;
    }

    if ((_features & QRF_GROUPDATA) != 0) {
        if (len < sizeof(uint32_t)) goto error;
        _groupDataLen = src->ReadInt32();
        len -= sizeof(uint32_t);

        if (len < _groupDataLen) goto error;
        AllocateGroupData(_groupDataLen);
        src->ReadBytes(_groupData, _groupDataLen);
        len -= _groupDataLen;
    }

    if (len < 3 * sizeof(uint64_t) + sizeof(uint32_t)) goto error;
    _coverageDocs  = src->ReadInt64();
    _activeDocs = src->ReadInt64();
    _soonActiveDocs  = src->ReadInt64();
    _coverageDegradeReason = src->ReadInt32();

    len -= 3*sizeof(uint64_t) + sizeof(uint32_t);

    if ((_features & QRF_MLD) != 0) {
        hitSize += 2 * sizeof(uint32_t);
    }

    if (len < _numDocs * hitSize) goto error;
    AllocateHits(_numDocs);
    unsigned char rawGid[document::GlobalId::LENGTH];
    for (uint32_t i = 0; i < _numDocs; i++) {
        src->ReadBytes(rawGid, document::GlobalId::LENGTH);
        _hits[i]._gid.set(rawGid);
        union { uint64_t INT64; double DOUBLE; } val;
        val.INT64 = src->ReadInt64();
        _hits[i]._metric = val.DOUBLE;
        if ((_features & QRF_MLD) != 0) {
            _hits[i]._partid   = src->ReadInt32();
            _hits[i].setDistributionKey(src->ReadInt32());
        } else {
            _hits[i]._partid   = 0; // partid not available
            _hits[i].setDistributionKey(getDistributionKey());
        }
    }
    len -= _numDocs * hitSize;

    if ((_features & QRF_PROPERTIES) != 0) {
        uint32_t sz = src->ReadInt32();
        _propsVector.resize(sz);
        len -= sizeof(uint32_t);
        for (uint32_t i = 0; i < sz; ++i) {
            if (! _propsVector[i].decode(*src, len)) goto error;
        }
    }

    if (len != 0) goto error;

    return true;            // OK

 error:
    src->DataToDead(len);
    return false;           // FAIL
}


vespalib::string
FS4Packet_QUERYRESULTX::toString(uint32_t indent) const
{
    vespalib::string s;
    uint32_t i;

    s += make_string("%*sFS4Packet_QUERYRESULTX {\n", indent, "");
    s += make_string("%*s  features      : 0x%x\n", indent, "", _features);
    s += make_string("%*s  offset        : %d\n", indent, "", _offset);
    s += make_string("%*s  numDocs       : %d\n", indent, "", _numDocs);
    s += make_string("%*s  totNumDocs    : %" PRIu64 "\n", indent, "", _totNumDocs);
    s += make_string("%*s  maxRank       : %f\n", indent, "", _maxRank);
    s += make_string("%*s  distrib key   : %d\n", indent, "", getDistributionKey());
    if (_numDocs > 0 && _sortIndex != NULL) {
        uint32_t offset = _sortIndex[0];
        for (i = 0; i < _numDocs; i++) {
            uint32_t end = _sortIndex[i + 1];
            s += make_string("%*s  sort[%d] = { 0x", indent, "", i);
            for (; offset < end; offset++)
                s += make_string("%02x", (unsigned char)*(_sortData + offset));
            s += make_string(" }\n");
        }
    }
    s += make_string("%*s  groupData     : %d bytes\n", indent, "", _groupDataLen);
    s += make_string("%*s  coverageDocs  : %" PRIu64 "\n", indent, "", _coverageDocs);
    s += make_string("%*s  activeDocs    : %" PRIu64 "\n", indent, "", _activeDocs);
    for (i = 0; i < _numDocs; i++) {
        s += make_string("%*s  hit {", indent, "");
        s += make_string("gid=%s, ", _hits[i]._gid.toString().c_str());
        s += make_string("metric=%f, ", _hits[i]._metric);
        s += make_string("partid=%d, ", _hits[i]._partid);
        s += make_string("distribkey=%d, ", _hits[i].getDistributionKey());
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================


FS4Packet_QUERYX::FS4Packet_QUERYX()
    : FS4Packet(),
      _timeout(0),
      _qflags(0),
      _features(0),
      _offset(0),
      _maxhits(0),
      _ranking(),
      _propsVector(),
      _sortSpec(),
      _groupSpec(),
      _sessionId(),
      _location(),
      _numStackItems(0),
      _stackDump()
{ }

FS4Packet_QUERYX::~FS4Packet_QUERYX() { }

uint32_t
FS4Packet_QUERYX::GetLength()
{
    uint32_t plen = 2 * sizeof(uint32_t);
    plen += FNET_DataBuffer::getCompressedPositiveLength(_offset);
    plen += FNET_DataBuffer::getCompressedPositiveLength(_maxhits);
    plen += sizeof(uint32_t);

    if ((_features & QF_PARSEDQUERY) != 0) {
        plen += sizeof(uint32_t)*2;
        plen += _stackDump.size();
    }
    if ((_features & QF_RANKP) != 0) {
        plen += FNET_DataBuffer::getCompressedPositiveLength(_ranking.size());
        plen += _ranking.size();
    }
    if ((_features & QF_PROPERTIES) != 0) {
        plen += sizeof(uint32_t);
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            plen += _propsVector[i].getLength();
        }
    }

    if ((_features & QF_SORTSPEC) != 0)
        plen += sizeof(uint32_t) + _sortSpec.size();

    if ((_features & QF_GROUPSPEC) != 0)
        plen += sizeof(uint32_t) + _groupSpec.size();

    if ((_features & QF_SESSIONID) != 0)
        plen += sizeof(uint32_t) + _sessionId.size();

    if ((_features & QF_LOCATION) != 0)
        plen += sizeof(uint32_t) + _location.size();

    return plen;
}


void
FS4Packet_QUERYX::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_features);

    dst->writeCompressedPositive(_offset);
    dst->writeCompressedPositive(_maxhits);
    dst->WriteInt32Fast(_timeout);
    dst->WriteInt32Fast(_qflags);

    if ((_features & QF_RANKP) != 0) {
        dst->writeCompressedPositive(_ranking.size());
        dst->WriteBytesFast(_ranking.c_str(), _ranking.size());
    }

    if ((_features & QF_PROPERTIES) != 0) {
        dst->WriteInt32Fast(_propsVector.size());
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            _propsVector[i].encode(*dst);
        }
    }

    if ((_features & QF_SORTSPEC) != 0) {
        dst->WriteInt32Fast(_sortSpec.size());
        dst->WriteBytesFast(_sortSpec.c_str(), _sortSpec.size());
    }

    if ((_features & QF_GROUPSPEC) != 0) {
        dst->WriteInt32Fast(_groupSpec.size());
        dst->WriteBytesFast(_groupSpec.c_str(), _groupSpec.size());
    }

    if ((_features & QF_SESSIONID) != 0) {
        dst->WriteInt32Fast(_sessionId.size());
        dst->WriteBytesFast(_sessionId.c_str(), _sessionId.size());
    }

    if ((_features & QF_LOCATION) != 0) {
        dst->WriteInt32Fast(_location.size());
        dst->WriteBytesFast(_location.c_str(), _location.size());
    }

    if ((_features & QF_PARSEDQUERY) != 0) {
        dst->WriteInt32Fast(_numStackItems);
        dst->WriteInt32Fast(_stackDump.size());
        dst->WriteBytesFast(_stackDump.c_str(), _stackDump.size());
    }
}

void FS4Packet::throwPropertieDecodeError(size_t i)
{
    throw vespalib::IllegalArgumentException(make_string("Failed decoding properties[%ld]", i));
}

void FS4Packet::throwUnsupportedFeatures(uint32_t features, uint32_t set)
{
    throw vespalib::UnderflowException(make_string("Unsupported features(%x), supported set(%x)", features, set));
}

void FS4Packet::throwNotEnoughData(FNET_DataBuffer & buf, uint32_t left, uint32_t needed, const char * text)
{
    (void) buf;
    throw vespalib::UnderflowException(make_string("Failed decoding packet of type %d. Only %d bytes left, needed %d from '%s'", GetPCODE(), left, needed, text));
}

#define VERIFY_LEN(needed, text) \
    { \
        if (len < needed) { \
            throwNotEnoughData(*src, len, needed, text); \
        } \
        len -= needed; \
    }

uint32_t FS4Packet::readUInt32(FNET_DataBuffer & buf, uint32_t & len, const char *text)
{
    if (len < sizeof(uint32_t)) {
        throwNotEnoughData(buf, len, sizeof(uint32_t), text); \
    }
    len -= sizeof(uint32_t);
    return buf.ReadInt32();
}

void
FS4Packet_GETDOCSUMSX::setTimeout(const fastos::TimeStamp & timeout)
{
    _timeout = std::max(0l, timeout.ms());
}

fastos::TimeStamp
FS4Packet_GETDOCSUMSX::getTimeout() const
{
    return fastos::TimeStamp(_timeout*fastos::TimeStamp::MS);
}

void
FS4Packet_QUERYX::setTimeout(const fastos::TimeStamp & timeout)
{
    _timeout = std::max(0l, timeout.ms());
}

fastos::TimeStamp
FS4Packet_QUERYX::getTimeout() const
{
    return fastos::TimeStamp(_timeout*fastos::TimeStamp::MS);
}

bool
FS4Packet_QUERYX::Decode(FNET_DataBuffer *src, uint32_t len)
{
    _features = readUInt32(*src, len, "features");

    if (((_features & ~FNET_QF_SUPPORTED_MASK) != 0)) {
        throwUnsupportedFeatures(_features, FNET_QF_SUPPORTED_MASK);
    }
    _offset  = src->readCompressedPositiveInteger();
    len -= FNET_DataBuffer::getCompressedPositiveLength(_offset);
    _maxhits = src->readCompressedPositiveInteger();
    len -= FNET_DataBuffer::getCompressedPositiveLength(_maxhits);
    VERIFY_LEN(2 * sizeof(uint32_t), "offset, maxhits, timeout and qflags");
    _timeout = src->ReadInt32();
    _qflags  = src->ReadInt32();

    if ((_features & QF_RANKP) != 0) {
        uint32_t rankingLen = src->readCompressedPositiveInteger();
        len -= FNET_DataBuffer::getCompressedPositiveLength(rankingLen);
        VERIFY_LEN(rankingLen, "ranking blob");
        setRanking(stringref(src->GetData(), rankingLen));
        src->DataToDead(rankingLen);
    }

    if ((_features & QF_PROPERTIES) != 0) {
        uint32_t cnt = readUInt32(*src, len, "#properties");
        _propsVector.resize(cnt);
        for (uint32_t i = 0; i < cnt; ++i) {
            if (!_propsVector[i].decode(*src, len)) {
                throwPropertieDecodeError(i);
            }
        }
    }

    if ((_features & QF_SORTSPEC) != 0) {
        uint32_t sortSpecLen = readUInt32(*src, len, "sortspec length");

        VERIFY_LEN(sortSpecLen, "sortspec string");
        setSortSpec(stringref(src->GetData(), sortSpecLen));
        src->DataToDead(sortSpecLen);
    }

    if ((_features & QF_GROUPSPEC) != 0) {
        uint32_t groupSpecLen = readUInt32(*src, len, "groupspec length");

        VERIFY_LEN(groupSpecLen, "groupspec string");
        setGroupSpec(stringref(src->GetData(), groupSpecLen));
        src->DataToDead(groupSpecLen);
    }

    if ((_features & QF_SESSIONID) != 0) {
        uint32_t sessionIdLen = readUInt32(*src, len, "sessionid length");
        VERIFY_LEN(sessionIdLen, "sessionid string");
        setSessionId(stringref(src->GetData(), sessionIdLen));
        src->DataToDead(sessionIdLen);
    }

    if ((_features & QF_LOCATION) != 0) {
        uint32_t locationLen = readUInt32(*src, len, "location length");

        VERIFY_LEN(locationLen, "location string");
        setLocation(stringref(src->GetData(), locationLen));
        src->DataToDead(locationLen);
    }

    if ((_features & QF_PARSEDQUERY) != 0) {
        _numStackItems = readUInt32(*src, len, "# querystack items");

        uint32_t stackDumpLen = readUInt32(*src, len, "stackdump length");
        VERIFY_LEN(stackDumpLen, "stackdump");
        setStackDump(stringref(src->GetData(), stackDumpLen));
        src->DataToDead(stackDumpLen);
    }
    if (len != 0) {
        throwNotEnoughData(*src, len, 0, "eof");
    }

    return true;
}


vespalib::string
FS4Packet_QUERYX::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_QUERYX {\n", indent, "");
    s += make_string("%*s  features    : 0x%x\n", indent, "", _features);
    s += make_string("%*s  offset      : %d\n", indent, "", _offset);
    s += make_string("%*s  maxhits     : %d\n", indent, "", _maxhits);
    s += make_string("%*s  qflags      : %x\n", indent, "", _qflags);
    s += make_string("%*s  ranking     : %s\n", indent, "", _ranking.c_str());
    for (uint32_t i = 0; i < _propsVector.size(); ++i) {
        s += _propsVector[i].toString(indent + 2);
    }
    s += make_string("%*s  sortspec    : %s\n", indent, "", _sortSpec.c_str());
    s += make_string("%*s  groupspec   : (%d bytes)\n", indent, "", (int)_groupSpec.size());
    s += make_string("%*s  sessionId   : (%d bytes) %s\n", indent, "", (int)_sessionId.size(), _sessionId.c_str());
    s += make_string("%*s  location    : %s\n", indent, "", _location.c_str());
    s += make_string("%*s  timeout     : %d\n", indent, "", _timeout);
    s += make_string("%*s  stackitems  : %d\n", indent, "", _numStackItems);
    s += make_string("%*s  stack dump  :\n", indent, "");
    if (_stackDump.size() > 0) {
        const char *pt = _stackDump.c_str();
        s += make_string("%*s  ", indent, "");
        uint32_t i = 0;
        while (i < _stackDump.size()) {
            s += make_string("%x ", (unsigned char) pt[i]);
            if ((++i % 16) == 0 && i < _stackDump.size()) {
                s += make_string("\n%*s  ", indent, "");
            }
        }
        if ((i % 16) != 0) s += make_string("\n");
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================


void
FS4Packet_GETDOCSUMSX::AllocateDocIDs(uint32_t cnt)
{
    if (cnt == 0)
        return;

    _docid.resize(cnt);
}


FS4Packet_GETDOCSUMSX::FS4Packet_GETDOCSUMSX()
    : FS4Packet(),
      _timeout(0),
      _features(0),
      _ranking(),
      _qflags(0),
      _resultClassName(),
      _propsVector(),
      _stackItems(0),
      _stackDump(),
      _location(),
      _flags(0u),
      _docid()
{ }


FS4Packet_GETDOCSUMSX::~FS4Packet_GETDOCSUMSX() { }

uint32_t
FS4Packet_GETDOCSUMSX::GetLength()
{
    uint32_t plen = 2 * sizeof(uint32_t) +
        + _docid.size() * (sizeof(document::GlobalId));

    plen += sizeof(uint32_t);

    if ((_features & GDF_MLD) != 0)
        plen += 2 * _docid.size() * sizeof(uint32_t);

    if ((_features & GDF_QUERYSTACK) != 0)
        plen += 2 * sizeof(uint32_t) + _stackDump.size();

    if ((_features & GDF_RESCLASSNAME) != 0)
        plen += sizeof(uint32_t) + _resultClassName.size();

    if ((_features & GDF_PROPERTIES) != 0) {
        plen += sizeof(uint32_t);
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            plen += _propsVector[i].getLength();
        }
    }

    if ((_features & GDF_RANKP_QFLAGS) != 0) {
        plen += FNET_DataBuffer::getCompressedPositiveLength(_ranking.size());
        plen += _ranking.size();
        plen += sizeof(uint32_t);
    }

    if ((_features & GDF_LOCATION) != 0)
        plen += sizeof(uint32_t) + _location.size();

    if ((_features & GDF_FLAGS) != 0)
        plen += sizeof(uint32_t);

    return plen;
}


void
FS4Packet_GETDOCSUMSX::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_features);

    dst->WriteInt32Fast(0);
    dst->WriteInt32Fast(_timeout);

    if ((_features & GDF_RANKP_QFLAGS) != 0) {
        dst->writeCompressedPositive(_ranking.size());
        dst->WriteBytesFast(_ranking.c_str(), _ranking.size());
        dst->WriteInt32Fast(_qflags);
    }

    if ((_features & GDF_RESCLASSNAME) != 0) {
        writeLenString(dst, _resultClassName);
    }

    if ((_features & GDF_PROPERTIES) != 0) {
        dst->WriteInt32Fast(_propsVector.size());
        for (uint32_t i = 0; i < _propsVector.size(); ++i) {
            _propsVector[i].encode(*dst);
        }
    }

    if ((_features & GDF_QUERYSTACK) != 0) {
        dst->WriteInt32Fast(_stackItems);
        writeLenString(dst, _stackDump);
    }

    if ((_features & GDF_LOCATION) != 0) {
        writeLenString(dst, _location);
    }

    if ((_features & GDF_FLAGS) != 0) {
        dst->WriteInt32Fast(_flags);
    }

    for (const auto & docid : _docid) {
        dst->WriteBytesFast(docid._gid.get(), document::GlobalId::LENGTH);

        if ((_features & GDF_MLD) != 0) {
            dst->WriteInt32Fast(docid._partid);
            dst->WriteInt32Fast(0);
        }
    }
}


bool
FS4Packet_GETDOCSUMSX::Decode(FNET_DataBuffer *src, uint32_t len)
{
    uint32_t docidSize = sizeof(document::GlobalId);

    _features = readUInt32(*src, len, "features");

    if ((_features & ~FNET_GDF_SUPPORTED_MASK) != 0) {
        throwUnsupportedFeatures(_features, FNET_GDF_SUPPORTED_MASK);
    }

    VERIFY_LEN(2*sizeof(uint32_t), "unused and timeout");
    src->ReadInt32();  // unused
    _timeout = src->ReadInt32();

    if ((_features & GDF_RANKP_QFLAGS) != 0) {
        uint32_t rankingLen = src->readCompressedPositiveInteger();
        len -= FNET_DataBuffer::getCompressedPositiveLength(rankingLen);

        VERIFY_LEN(rankingLen, "ranking blob");
        setRanking(vespalib::stringref(src->GetData(), rankingLen));
        src->DataToDead(rankingLen);

        _qflags = readUInt32(*src, len, "qflags");
    }

    if ((_features & GDF_RESCLASSNAME) != 0) {
        uint32_t resultClassNameLen = readUInt32(*src, len, "result class name length");

        VERIFY_LEN(resultClassNameLen, "result class");
        setResultClassName(stringref(src->GetData(), resultClassNameLen));
        src->DataToDead(resultClassNameLen);
    }

    if ((_features & GDF_PROPERTIES) != 0) {
        uint32_t cnt = readUInt32(*src, len, "#properties");
        _propsVector.resize(cnt);
        for (uint32_t i = 0; i < cnt; ++i) {
            if (!_propsVector[i].decode(*src, len)) {
                throwPropertieDecodeError(i);
            }
        }
    }

    if ((_features & GDF_QUERYSTACK) != 0) {
        _stackItems = readUInt32(*src, len, "num stack items");
        uint32_t stackDumpLen = readUInt32(*src, len, "stackdump length");
        VERIFY_LEN(stackDumpLen, "stackdump");
        setStackDump(stringref(src->GetData(), stackDumpLen));
        src->DataToDead(stackDumpLen);
    }

    if ((_features & GDF_LOCATION) != 0) {
        uint32_t locationLen = readUInt32(*src, len, "location length");
        VERIFY_LEN(locationLen, "location string");
        setLocation(stringref(src->GetData(), locationLen));
        src->DataToDead(locationLen);
    }

    if ((_features & GDF_FLAGS) != 0) {
        _flags = readUInt32(*src, len, "flags");
    }

    if ((_features & GDF_MLD) != 0)
        docidSize += 2 * sizeof(uint32_t);

    AllocateDocIDs(len / docidSize);

    unsigned char rawGid[document::GlobalId::LENGTH];
    for (auto & docid : _docid) {
        src->ReadBytes(rawGid, document::GlobalId::LENGTH);
        docid._gid.set(rawGid);

        if ((_features & GDF_MLD) != 0) {
            docid._partid   = src->ReadInt32();
            src->ReadInt32();  // unused
        } else {
            docid._partid   = 0; // partid not available
        }
    }
    len -= _docid.size() * docidSize;

    if (len != 0) {
        throwNotEnoughData(*src, len, 0, "eof");
    }

    return true;            // OK
}


vespalib::string
FS4Packet_GETDOCSUMSX::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_GETDOCSUMSX {\n", indent, "");
    s += make_string("%*s  features       : %d\n", indent, "", _features);
    s += make_string("%*s  ranking        : %s\n", indent, "", _ranking.c_str());
    s += make_string("%*s  qflags         : %x\n", indent, "", _qflags);
    s += make_string("%*s  resClassName: %s\n", indent, "", _resultClassName.c_str());
    for (uint32_t i = 0; i < _propsVector.size(); ++i) {
        s += _propsVector[i].toString(indent + 2);
    }
    s += make_string("%*s  stackItems     : %d\n", indent, "", _stackItems);
    s += make_string("%*s  stackDumpLen   : %d\n", indent, "", (int)_stackDump.size());
    s += make_string("%*s  stackDump      :\n", indent, "");

    uint32_t i = 0;
    if (_stackDump.size() > 0) {
        const char *pt = _stackDump.c_str();
        s += make_string("%*s  ", indent, "");
        while (i < _stackDump.size()) {
            s += make_string("%x ", (unsigned char) pt[i]);
            if ((++i % 16) == 0)
                s += make_string("\n%*s  ", indent, "");
        }
        if ((i % 16) != 0) s += make_string("\n");
    }
    for (const auto & docId : _docid) {
        s += make_string("%*s  gid=%s, partid=%d\n", indent, "",
                         docId._gid.toString().c_str(), docId._partid);
    }
    s += make_string("%*s  location       : %s\n", indent, "", _location.c_str());
    s += make_string("%*s  timeout        : %d\n", indent, "", _timeout);
    s += make_string("%*s  flags          : %d\n", indent, "", _flags);
    s += make_string("%*s}\n", indent, "");
    return s;
}

//============================================================

uint32_t
FS4Packet_TRACEREPLY::GetLength()
{
    uint32_t plen = sizeof(uint32_t);
    for (uint32_t i = 0; i < _propsVector.size(); ++i) {
        plen += _propsVector[i].getLength();
    }
    return plen;
}

void
FS4Packet_TRACEREPLY::Encode(FNET_DataBuffer *dst)
{
    dst->WriteInt32Fast(_propsVector.size());
    for (uint32_t i = 0; i < _propsVector.size(); ++i) {
        _propsVector[i].encode(*dst);
    }
}

bool
FS4Packet_TRACEREPLY::Decode(FNET_DataBuffer *src, uint32_t len)
{
    uint32_t cnt = readUInt32(*src, len, "#properties");
    _propsVector.resize(cnt);
    for (uint32_t i = 0; i < cnt; ++i) {
        if (!_propsVector[i].decode(*src, len)) {
            throwPropertieDecodeError(i);
        }
    }
    if (len != 0) goto error;
    return true;            // OK
 error:
    src->DataToDead(len);
    return false;           // FAIL
}

vespalib::string
FS4Packet_TRACEREPLY::toString(uint32_t indent) const
{
    vespalib::string s;
    s += make_string("%*sFS4Packet_TRACEREPLY {\n", indent, "");
    for (uint32_t i = 0; i < _propsVector.size(); ++i) {
        s += _propsVector[i].toString(indent + 2);
    }
    s += make_string("%*s}\n", indent, "");
    return s;
}


//============================================================

FNET_Packet*
FS4PacketFactory::CreateFS4Packet(uint32_t pcode)
{
    switch(pcode) {
    case PCODE_EOL:
        return new FS4Packet_EOL;
    case PCODE_ERROR:
        return new FS4Packet_ERROR;
    case PCODE_DOCSUM:
        return new FS4Packet_DOCSUM;
    case PCODE_QUERYRESULTX:
        return new FS4Packet_QUERYRESULTX;
    case PCODE_QUERYX:
        return new FS4Packet_QUERYX;
    case PCODE_GETDOCSUMSX:
        return new FS4Packet_GETDOCSUMSX;
    case PCODE_MONITORQUERYX:
        return new FS4Packet_MONITORQUERYX;
    case PCODE_MONITORRESULTX:
        return new FS4Packet_MONITORRESULTX;
    case PCODE_TRACEREPLY:
        return new FS4Packet_TRACEREPLY;
    default:
        return NULL;
    }
}

}
