// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transport.h"
#include "hitrank.h"
#include <vespa/fnet/context.h>
#include <vespa/fnet/ipacketstreamer.h>
#include <vespa/fnet/packet.h>
#include <vespa/fnet/databuffer.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/fastos/timestamp.h>
#include <vector>

namespace search::fs4transport {

using vespalib::string;

enum fnet_feature_masks {
    FNET_QRF_SUPPORTED_MASK = (QRF_MLD |
                               QRF_SORTDATA |
                               QRF_COVERAGE_NODES |
                               QRF_EXTENDED_COVERAGE |
                               QRF_COVERAGE |
                               QRF_GROUPDATA |
                               QRF_PROPERTIES),

    FNET_QF_SUPPORTED_MASK  = (QF_PARSEDQUERY |
                               QF_RANKP |
                               QF_SORTSPEC |
                               QF_LOCATION |
                               QF_PROPERTIES |
                               QF_GROUPSPEC |
                               QF_SESSIONID),

    FNET_GDF_SUPPORTED_MASK = (GDF_MLD |
                               GDF_QUERYSTACK |
                               GDF_RANKP_QFLAGS |
                               GDF_LOCATION |
                               GDF_RESCLASSNAME |
                               GDF_PROPERTIES |
                               GDF_FLAGS),

    FNET_MQF_SUPPORTED_MASK = (MQF_QFLAGS),

    FNET_MRF_SUPPORTED_MASK = (MRF_MLD | MRF_RFLAGS | MRF_ACTIVEDOCS)
};

enum pcode_mask {
    PCODE_MASK = 0x00ffffff
};

//==========================================================================

class PacketArray
{
private:
    PacketArray(const PacketArray &);
    PacketArray& operator=(const PacketArray &);

    FNET_Packet **_extArray;
    FNET_Packet **_array;
    uint32_t      _size;
    uint32_t      _used;

public:
    PacketArray(FNET_Packet **arr = nullptr, uint32_t size = 0)
        : _extArray(arr),
          _array(arr),
          _size(size),
          _used(0) {}
    ~PacketArray()
    {
        if (_array != _extArray)
            delete [] _array;
    }
    void Add(FNET_Packet *packet)
    {
        if (_used == _size) {
            _size *= 2;
            if (_size < 16)
                _size = 16;
            FNET_Packet **newArray = new FNET_Packet*[_size];
            for (uint32_t i = 0; i < _used; i++)
                newArray[i] = _array[i];
            if (_array != _extArray)
                delete [] _array;
            _array = newArray;
        }
        _array[_used++] = packet;
    }
    FNET_Packet **Array() const { return _array; }
    uint32_t Length() const { return _used; }
};

//==========================================================================

class FS4PacketFactory
{
public:
    typedef FNET_Packet *(* CreatePacket_t)(uint32_t pcode);

    static FNET_Packet *CreateFS4Packet(uint32_t pcode);
};

//==========================================================================

class FS4PersistentPacketStreamer : public FNET_IPacketStreamer {
    FS4PersistentPacketStreamer(const FS4PersistentPacketStreamer &);
    FS4PersistentPacketStreamer& operator=(const FS4PersistentPacketStreamer &);
    using CompressionConfig = vespalib::compression::CompressionConfig;

    unsigned int _compressionLimit;
    unsigned int _compressionLevel;
    CompressionConfig::Type _compressionType;
protected:
    bool _conservative;  // Set to true if out of sync should mark the
                         // stream as broken.
    FS4PacketFactory::CreatePacket_t _createPacket;

    bool HasChannelID(uint32_t pcode);
    bool ValidPCode(uint32_t pcode) const {
        return ((pcode & PCODE_MASK) >= PCODE_EOL)
            && ((pcode & PCODE_MASK) < PCODE_LastCode);
    }

public:
    static FS4PersistentPacketStreamer Instance;

    FS4PersistentPacketStreamer(FS4PacketFactory::CreatePacket_t cp);

    bool GetPacketInfo(FNET_DataBuffer *src, uint32_t *plen,
                       uint32_t *pcode, uint32_t *chid, bool *broken) override;
    FNET_Packet *Decode(FNET_DataBuffer *src, uint32_t plen,
                        uint32_t pcode, FNET_Context context) override;
    void Encode(FNET_Packet *packet, uint32_t chid, FNET_DataBuffer *dst) override;

    void SetConservativeMode(bool cons) { _conservative = cons; }
    void SetCompressionLimit(unsigned int limit) { _compressionLimit = limit; }
    void SetCompressionLevel(unsigned int level) { _compressionLevel = level; }
    void SetCompressionType(CompressionConfig::Type compressionType) { _compressionType = compressionType; }
    CompressionConfig::Type getCompressionType() const { return _compressionType; }
    uint32_t getCompressionLimit() const { return _compressionLimit; }
    uint32_t getCompressionLevel() const { return _compressionLevel; }
};

//==========================================================================

class FS4Properties
{
private:
    typedef std::pair<uint32_t, uint32_t> StringRef;
    typedef std::pair<StringRef, StringRef> Entry;
    typedef std::vector<Entry> KeyValueVector;

    KeyValueVector   _entries;
    vespalib::string _name;
    vespalib::string _backing;
    const char * c_str(size_t sz) const { return _backing.c_str() + sz; }
    void set(StringRef & e, const vespalib::stringref & s);
public:
    FS4Properties(FS4Properties &&);
    FS4Properties &operator=(FS4Properties &&);

    FS4Properties();
    ~FS4Properties();
    void allocEntries(uint32_t cnt);
    void setName(const char *name, uint32_t nameSize) { _name.assign(name, nameSize); }
    void setName(const vespalib::stringref &val) {
        setName(val.data(), val.size());
    }
    void setKey(uint32_t entry, const char *key, uint32_t keySize);
    void setKey(uint32_t entry, const vespalib::stringref &val) {
        setKey(entry, val.data(), val.size());
    }
    void setValue(uint32_t entry, const char *value, uint32_t valueSize);
    void setValue(uint32_t entry, const vespalib::stringref &val) {
        setValue(entry, val.data(), val.size());
    }
    uint32_t size() const { return _entries.size(); }
    const char *getName() const { return _name.c_str(); }
    uint32_t getNameLen() const { return _name.size(); }
    const char *getKey(uint32_t entry) const { return c_str(_entries[entry].first.first); }
    uint32_t getKeyLen(uint32_t entry) const { return _entries[entry].first.second; }
    const char *getValue(uint32_t entry) const { return c_str(_entries[entry].second.first); }
    uint32_t getValueLen(uint32_t entry) const { return _entries[entry].second.second; }

    // sub-packet methods below
    uint32_t getLength();
    void encode(FNET_DataBuffer &dst);
    bool decode(FNET_DataBuffer &src, uint32_t &len);
    vespalib::string toString(uint32_t indent = 0) const;
};

//==========================================================================

typedef std::vector<FS4Properties> PropsVector;

//==========================================================================

class FS4Packet : public FNET_Packet
{
private:
    FS4Packet(const FS4Packet &);
    FS4Packet& operator=(const FS4Packet &);

public:
    FS4Packet();
    ~FS4Packet();
    vespalib::string Print(uint32_t indent) override;
    void Free() override;
    virtual vespalib::string toString(uint32_t indent) const = 0;
protected:
    uint32_t readUInt32(FNET_DataBuffer & buf, uint32_t & len, const char *text) __attribute__((noinline));
    void throwNotEnoughData(FNET_DataBuffer & buf, uint32_t left, uint32_t needed, const char * text) __attribute__((noinline));
    void throwUnsupportedFeatures(uint32_t features, uint32_t set) __attribute__((noinline));
    void throwPropertieDecodeError(size_t i) __attribute__((noinline));
};

//==========================================================================

class FS4Packet_EOL : public FS4Packet
{
public:
    FS4Packet_EOL();
    ~FS4Packet_EOL();
    uint32_t GetPCODE() override { return PCODE_EOL; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

class FS4Packet_PreSerialized : public FS4Packet
{
public:
    FS4Packet_PreSerialized(FNET_Packet & packet);
    ~FS4Packet_PreSerialized();
    uint32_t GetPCODE() override;
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
private:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    uint32_t                _pcode;
    CompressionConfig::Type _compressionType;
    FNET_DataBuffer         _data;
};

class FS4Packet_Shared : public FS4Packet
{
public:
    FS4Packet_Shared(FNET_Packet::SP packet);
    ~FS4Packet_Shared();
    uint32_t GetPCODE() override;
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *, uint32_t ) override;
    vespalib::string toString(uint32_t indent) const override;
private:
    FNET_Packet::SP _packet;
};

//==========================================================================

class FS4Packet_ERROR : public FS4Packet
{
private:
    FS4Packet_ERROR(const FS4Packet_ERROR &);
    FS4Packet_ERROR& operator=(const FS4Packet_ERROR &);

public:
    uint32_t  _errorCode;
    string    _message;

    void setErrorMessage(const vespalib::stringref &msg) { _message = msg; }

    FS4Packet_ERROR();
    ~FS4Packet_ERROR();
    uint32_t GetPCODE() override { return PCODE_ERROR; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_DOCSUM : public FS4Packet
{
public:
    typedef vespalib::MallocPtr Buf;
private:
    FS4Packet_DOCSUM(const FS4Packet_DOCSUM &);
    FS4Packet_DOCSUM& operator=(const FS4Packet_DOCSUM &);

    document::GlobalId _gid;
    Buf                _buf;
public:
    FS4Packet_DOCSUM();
    ~FS4Packet_DOCSUM();
    const Buf & getBuf() const { return _buf; }
    void swapBuf(Buf & other) { _buf.swap(other); }
    void setGid(const document::GlobalId & gid) { _gid = gid; }
    const document::GlobalId & getGid() const { return _gid; }
    bool empty() const { return _buf.empty(); }
    void SetBuf(const char *buf, uint32_t len);
    uint32_t GetPCODE() override { return PCODE_DOCSUM; }
    uint32_t GetLength() override { return sizeof(_gid) + _buf.size(); }
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_MONITORQUERYX : public FS4Packet
{
    FS4Packet_MONITORQUERYX(const FS4Packet_MONITORQUERYX &);
    FS4Packet_MONITORQUERYX& operator=(const FS4Packet_MONITORQUERYX &);

public:
    uint32_t _features;         // see monitorquery_features
    uint32_t _qflags;           // if MQF_QFLAGS

    FS4Packet_MONITORQUERYX();
    ~FS4Packet_MONITORQUERYX();
    uint32_t GetPCODE() override { return PCODE_MONITORQUERYX; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_MONITORRESULTX : public FS4Packet
{
private:
    FS4Packet_MONITORRESULTX(const FS4Packet_MONITORRESULTX &);
    FS4Packet_MONITORRESULTX& operator=(const FS4Packet_MONITORRESULTX &);

public:
    uint32_t _features;         // see monitor
    uint32_t _partid;
    uint32_t _timestamp;

    uint32_t _totalNodes;       // if MRF_MLD
    uint32_t _activeNodes;      // if MRF_MLD
    uint32_t _totalParts;       // if MRF_MLD
    uint32_t _activeParts;      // if MRF_MLD

    uint32_t _rflags;           // if MRF_RFLAGS
    uint64_t _activeDocs;       // if MRF_ACTIVEDOCS

    FS4Packet_MONITORRESULTX();
    ~FS4Packet_MONITORRESULTX();
    uint32_t GetPCODE()  override { return PCODE_MONITORRESULTX; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_QUERYRESULTX : public FS4Packet
{
private:
    FS4Packet_QUERYRESULTX(const FS4Packet_QUERYRESULTX &);
    FS4Packet_QUERYRESULTX& operator=(const FS4Packet_QUERYRESULTX &);

    uint32_t _distributionKey;
    uint16_t _nodesQueried;
    uint16_t _nodesReplied;

public:
    uint32_t _features;      // see queryresult_features
    uint32_t _offset;
    uint32_t _numDocs;
    uint64_t _totNumDocs;
    search::HitRank _maxRank;
    uint32_t *_sortIndex;             // if QRF_SORTDATA
    char     *_sortData;              // if QRF_SORTDATA
    uint32_t _groupDataLen;           // if QRF_GROUPDATA
    char    *_groupData;              // if QRF_GROUPDATA
    uint64_t _coverageDocs;           // if QRF_COVERAGE
    uint64_t _activeDocs;             // if QRF_COVERAGE
    uint64_t _soonActiveDocs;         // if QRF_EXTENDED_COVERAGE
    uint32_t _coverageDegradeReason;  // if QRF_EXTENDED_COVERAGE
    class FS4_hit {
    public:
        FS4_hit() : _gid(), _metric(0), _partid(0), _distributionKey(0) { }
        uint32_t getDistributionKey() const { return _distributionKey; }
        void setDistributionKey(uint32_t key) { _distributionKey = key; }
        const document::GlobalId & HT_GetGlobalID() const { return _gid; }
        search::HitRank HT_GetMetric()   const { return   _metric; }
        uint32_t HT_GetPartID()   const { return   _partid; }

        void HT_SetGlobalID(const document::GlobalId & val) { _gid = val; }
        void HT_SetMetric(search::HitRank val)   {   _metric = val; }
        void HT_SetPartID(uint32_t val)   {   _partid = val; }
        document::GlobalId _gid;
        search::HitRank _metric;
        uint32_t _partid;      // if QRF_MLD
    private:
        uint32_t _distributionKey;    // if QRF_MLD
    } *_hits;
    PropsVector _propsVector; // if QRF_PROPERTIES

    void AllocateSortIndex(uint32_t cnt);
    void AllocateSortData(uint32_t len);
    void SetSortDataRef(uint32_t cnt, uint32_t *sortIndex, const char *sortData);
    void AllocateGroupData(uint32_t len);
    void SetGroupDataRef(const char *groupData, uint32_t len);
    void AllocateHits(uint32_t cnt);

    FS4Packet_QUERYRESULTX();
    ~FS4Packet_QUERYRESULTX();
    uint32_t GetPCODE() override { return PCODE_QUERYRESULTX; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override ;
    vespalib::string toString(uint32_t indent) const override ;
    uint32_t getDistributionKey() const { return _distributionKey; }
    void setDistributionKey(uint32_t key) { _distributionKey = key; }
    uint16_t getNodesQueried() const { return _nodesQueried; }
    void setNodesQueried(uint16_t key) { _nodesQueried = key; }
    uint16_t getNodesReplied() const { return _nodesReplied; }
    void setNodesReplied(uint16_t key) { _nodesReplied = key; }
};
//==========================================================================

class FS4Packet_QUERYX : public FS4Packet
{
private:
    FS4Packet_QUERYX(const FS4Packet_QUERYX &);
    FS4Packet_QUERYX& operator=(const FS4Packet_QUERYX &);

    uint32_t  _timeout;

public:
    uint32_t  _features;      // see query_features
    uint32_t  _offset;
    uint32_t  _maxhits;
    uint32_t  _qflags;
    string    _ranking;       // if QF_RANKP
    PropsVector _propsVector; // if QF_PROPERTIES
    string    _sortSpec;      // if QF_SORTSPEC
    string    _groupSpec;     // if QF_GROUPSPEC
    string    _sessionId;     // if QF_SESSIONID
    string    _location;      // if QF_LOCATION

    uint32_t  _numStackItems; // if QF_PARSEDQUERY
    string    _stackDump;     // if QF_PARSEDQUERY

    void setRanking(const vespalib::stringref &ranking) { _ranking = ranking; }
    void setSortSpec(const vespalib::stringref &spec) { _sortSpec = spec; }
    void setGroupSpec(const vespalib::stringref &spec) { _groupSpec = spec; }
    void setSessionId(const vespalib::stringref &sid) { _sessionId = sid; }
    void setLocation(const vespalib::stringref &loc) { _location = loc; }
    void setStackDump(const vespalib::stringref &buf) { _stackDump = buf; }
    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp getTimeout() const;

    explicit FS4Packet_QUERYX();
    ~FS4Packet_QUERYX();
    uint32_t GetPCODE() override { return PCODE_QUERYX; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_GETDOCSUMSX : public FS4Packet
{
private:
    FS4Packet_GETDOCSUMSX(const FS4Packet_GETDOCSUMSX &);
    FS4Packet_GETDOCSUMSX& operator=(const FS4Packet_GETDOCSUMSX &);

    uint32_t       _timeout;
public:
    uint32_t       _features;          // see getdocsums_features
    string         _ranking;           // if GDF_RANKP_QFLAGS
    uint32_t       _qflags;            // if GDF_RANKP_QFLAGS
    string         _resultClassName;   // if GDF_RESCLASSNAME
    PropsVector    _propsVector;       // if GDF_PROPERTIES
    uint32_t       _stackItems;        // if GDF_QUERYSTACK
    string         _stackDump;         // if GDF_QUERYSTACK
    string         _location;          // if GDF_LOCATION
    uint32_t       _flags;             // if GDF_FLAGS
    class FS4_docid {
    public:
        FS4_docid() : _gid(), _partid(0) { }
        document::GlobalId _gid;
        uint32_t _partid;           // if GDF_MLD
    };
    std::vector<FS4_docid> _docid;

    void AllocateDocIDs(uint32_t cnt);

    void setResultClassName(const vespalib::stringref &name) { _resultClassName = name; }
    void setStackDump(const vespalib::stringref &buf) { _stackDump = buf; }
    void setRanking(const vespalib::stringref &ranking) { _ranking = ranking; }
    void setLocation(const vespalib::stringref &loc) { _location = loc; }
    void setTimeout(const fastos::TimeStamp & timeout);
    fastos::TimeStamp getTimeout() const;

    FS4Packet_GETDOCSUMSX();
    ~FS4Packet_GETDOCSUMSX();
    uint32_t GetPCODE() override { return PCODE_GETDOCSUMSX; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;
};

//==========================================================================

class FS4Packet_TRACEREPLY : public FS4Packet
{
public:
    FS4Packet_TRACEREPLY() {}
    ~FS4Packet_TRACEREPLY() {}
    uint32_t GetPCODE() override { return PCODE_TRACEREPLY; }
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *dst) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
    vespalib::string toString(uint32_t indent) const override;

    PropsVector _propsVector;
};

//==========================================================================

}
