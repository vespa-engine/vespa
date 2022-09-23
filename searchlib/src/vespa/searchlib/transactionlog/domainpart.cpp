// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "domainpart.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".transactionlog.domainpart");

using vespalib::make_string_short::fmt;
using vespalib::FileHeader;
using vespalib::string;
using vespalib::getLastErrorString;
using vespalib::IllegalHeaderException;
using vespalib::nbostream;
using vespalib::nbostream_longlivedbuf;
using vespalib::alloc::Alloc;
using search::common::FileHeaderContext;
using std::runtime_error;

namespace search::transactionlog {

namespace {

constexpr size_t TARGET_PACKET_SIZE = 0x3f000;

string
handleWriteError(const char *text, FastOS_FileInterface &file, int64_t lastKnownGoodPos,
                 SerialNumRange range, int bufLen) __attribute__ ((noinline));

bool
handleReadError(const char *text, FastOS_FileInterface &file, ssize_t len, ssize_t rlen,
                int64_t lastKnownGoodPos, bool allowTruncate) __attribute__ ((noinline));

void handleSync(FastOS_FileInterface &file) __attribute__ ((noinline));
void addPacket(Packet &packet, const Packet::Entry &e) __attribute__ ((noinline));
bool tailOfFileIsZero(FastOS_FileInterface &file, int64_t lastKnownGoodPos) __attribute__ ((noinline));

void
addPacket(Packet &packet, const Packet::Entry &e)
{
    LOG(spam, "Adding serial #%" PRIu64 ", of type %d and size %zd into packet of size %zu and %zu bytes",
              e.serial(), e.type(), e.data().size(), packet.size(), packet.sizeBytes());
    packet.add(e);
}

void
handleSync(FastOS_FileInterface &file)
{
    if ( file.IsOpened() && ! file.Sync() ) {
        int osError = errno;
        throw runtime_error(fmt("Failed to synchronize file '%s' of size %" PRId64 " due to '%s'. "
                                "Does not know how to handle this so throwing an exception.",
                                file.GetFileName(), file.GetSize(), FastOS_File::getErrorString(osError).c_str()));
    }
}

string
handleWriteError(const char *text, FastOS_FileInterface &file, int64_t lastKnownGoodPos,
                 SerialNumRange range, int bufLen)
{
    string last(FastOS_File::getLastErrorString());
    string e(fmt("%s. File '%s' at position %" PRId64 " for entries [%" PRIu64 ", %" PRIu64 "] of length %u. "
                 "OS says '%s'. Rewind to last known good position %" PRId64 ".",
                 text, file.GetFileName(), file.GetPosition(), range.from(), range.to(), bufLen,
                 last.c_str(), lastKnownGoodPos));
    LOG(error, "%s",  e.c_str());
    if ( ! file.SetPosition(lastKnownGoodPos) ) {
        last = FastOS_File::getLastErrorString();
        throw runtime_error(fmt("Failed setting position %" PRId64 " of file '%s' of size %" PRId64 " : OS says '%s'",
                                lastKnownGoodPos, file.GetFileName(), file.GetSize(), last.c_str()));
    }
    handleSync(file);
    return e;
}

string
getError(FastOS_FileInterface & f)
{
    return fmt("File '%s' of size %" PRId64 " has last error of '%s'.",
               f.GetFileName(), f.GetSize(), FastOS_File::getLastErrorString().c_str());
}

bool
tailOfFileIsZero(FastOS_FileInterface &file, int64_t lastKnownGoodPos)
{
    ssize_t rest(file.GetSize() - lastKnownGoodPos);
    if (rest < 0 || rest > 0x100000) {
        return false;
    }
    std::vector<char> buf(rest, 0);
    file.ReadBuf(&buf[0], buf.size(), lastKnownGoodPos);
    for (char c : buf) {
        if (c != 0) {
            return false;
        }
    }
    return true;
}

bool
handleReadError(const char *text, FastOS_FileInterface &file, ssize_t len, ssize_t rlen,
                int64_t lastKnownGoodPos, bool allowTruncate)
{
    bool retval(true);
    if (rlen != -1) {
        string e;
        if (len == rlen) {
            e = fmt("Error in data read of size %zd bytes at pos %" PRId64 " trying to read %s. ",
                    len, file.GetPosition() - rlen, text);
        } else {
            e = fmt("Short Read. Got only %zd of %zd bytes at pos %" PRId64 " trying to read %s. ",
                    rlen, len, file.GetPosition() - rlen, text);
        }
        e += getError(file);
        if (!allowTruncate) {
            LOG(error, "%s", e.c_str());
            throw runtime_error(e);
        }
        // Short read. Log error, Truncate, continue.
        e += fmt(" Truncate to %" PRId64 " and continue", lastKnownGoodPos);
        LOG(error, "%s", e.c_str());
        FastOS_File truncateFile(file.GetFileName());
        if ( ! file.Close()) {
            e += getError(file);
        }
        if ( truncateFile.OpenWriteOnlyExisting()) {
            if (truncateFile.SetSize(lastKnownGoodPos)) {
                if (truncateFile.Close()) {
                    if (file.OpenReadOnly()) {
                        if (file.SetPosition(lastKnownGoodPos)) {
                            retval = false;
                        } else {
                            throw runtime_error(fmt("Failed setting position %" PRId64 ". %s", lastKnownGoodPos, getError(file).c_str()));
                        }
                    } else {
                        throw runtime_error(fmt("Failed reopening file after truncate: %s", getError(file).c_str()));
                    }
                } else {
                    throw runtime_error(fmt("Failed closing truncated file: %s", getError(truncateFile).c_str()));
                }
            } else {
                throw runtime_error(fmt("Failed truncating to %" PRId64 ": %s", lastKnownGoodPos, getError(truncateFile).c_str()));
            }
        } else {
            throw runtime_error(fmt("Failed opening for truncating: %s", getError(file).c_str()));
        }
    } else {
        // Some kind of IO error throw exception.
        string errString = FastOS_File::getLastErrorString();
        throw runtime_error(fmt("IO error when reading %zd bytes at pos %" PRId64 "trying to read %s."
                                " Last known good position is %" PRId64 ": %s",
                                len, file.GetPosition(), text, lastKnownGoodPos, getError(file).c_str()));
    }
    return retval;
}

}

Packet
DomainPart::readPacket(FastOS_FileInterface & transLog, SerialNumRange wanted, size_t targetSize, bool allowTruncate) {
    Alloc buf;
    Packet packet(targetSize);
    int64_t fSize(transLog.GetSize());
    int64_t currPos(transLog.GetPosition());
    while ((packet.sizeBytes() < targetSize) && (currPos < fSize) && (packet.range().to() < wanted.to())) {
        IChunk::UP chunk;
        if (read(transLog, chunk, buf, allowTruncate)) {
            if (chunk) {
                try {
                    for (const Packet::Entry & e : chunk->getEntries()) {
                        if ((wanted.from() < e.serial()) && (e.serial() <= wanted.to())) {
                            addPacket(packet, e);
                        }
                    }
                } catch (const std::exception & ex) {
                    throw runtime_error(fmt("%s : Failed creating packet for list %s(%" PRIu64 ") at pos(%" PRIu64 ", %" PRIu64 ")",
                                            ex.what(), transLog.GetFileName(), fSize, currPos, transLog.GetPosition()));
                }
            } else {
                throw runtime_error(fmt("Invalid entry reading file %s(%" PRIu64 ") at pos(%" PRIu64 ", %" PRIu64 ")",
                                        transLog.GetFileName(), fSize, currPos, transLog.GetPosition()));
            }
        } else {
            if (transLog.GetSize() != fSize) {
                fSize = transLog.GetSize();
            } else {
                throw runtime_error(fmt("Failed reading file %s(%" PRIu64 ") at pos(%" PRIu64 ", %" PRIu64 ")",
                                        transLog.GetFileName(), fSize, currPos, transLog.GetPosition()));
            }
        }
        currPos = transLog.GetPosition();
    }
    return packet;
}

int64_t
DomainPart::buildPacketMapping(bool allowTruncate)
{
    Fast_BufferedFile transLog;
    transLog.EnableDirectIO();
    if ( ! transLog.OpenReadOnly(_transLog->GetFileName())) {
        throw runtime_error(fmt("Failed opening '%s' for buffered readinf with direct io.", transLog.GetFileName()));
    }
    int64_t fSize(transLog.GetSize());
    int64_t currPos(0);
    try {
        FileHeader header;
        _headerLen = header.readFile(transLog);
        transLog.SetPosition(_headerLen);
        currPos = _headerLen;
    } catch (const IllegalHeaderException &e) {
        transLog.SetPosition(0);
        try {
            FileHeader::FileReader fr(transLog);
            uint32_t header2Len = FileHeader::readSize(fr);
            if (header2Len <= fSize)
                e.throwSelf(); // header not truncated
        } catch (const IllegalHeaderException &e2) {
        }
        if (fSize > 0) {
            // Truncate file (dropping header) if cannot even read
            // header length, or if header has been truncated.
            handleReadError("file header", transLog, 0, FileHeader::getMinSize(), 0, allowTruncate);
        }
    }
    const SerialNumRange all(0, std::numeric_limits<SerialNum>::max());
    while ((currPos < fSize)) {
        const int64_t firstPos(currPos);
        Packet packet = readPacket(transLog, all, TARGET_PACKET_SIZE, allowTruncate);
        if (!packet.empty()) {
            set_size(size() + packet.size());
            const SerialNum firstSerial = packet.range().from();
            if (currPos == _headerLen) {
                set_range_from(firstSerial);
            }
            set_range_to(packet.range().to());
            // Called only from constructor so no need to hold lock
            _skipList.emplace_back(firstSerial, firstPos);
        } else {
            fSize = transLog.GetSize();
        }
        currPos = transLog.GetPosition();
    }
    return currPos;
}

DomainPart::DomainPart(const string & name, const string & baseDir, SerialNum s,
                       const FileHeaderContext &fileHeaderContext, bool allowTruncate)
    : _lock(),
      _fileLock(),
      _range_from(s),
      _range_to(s ? s - 1 : s),
      _sz(0),
      _byteSize(0),
      _fileName(fmt("%s/%s-%016" PRIu64, baseDir.c_str(), name.c_str(), s)),
      _transLog(std::make_unique<FastOS_File>(_fileName.c_str())),
      _skipList(),
      _headerLen(0),
      _writeLock(),
      _writtenSerial(0),
      _syncedSerial(0)
{
    if (_transLog->OpenReadOnly()) {
        int64_t currPos = buildPacketMapping(allowTruncate);
        if ( ! _transLog->Close() ) {
            throw runtime_error(fmt("Failed closing file '%s' after reading.", _transLog->GetFileName()));
        }
        if ( ! _transLog->OpenWriteOnlyExisting() ) {
            string e(fmt("Failed opening existing file '%s' for writing: %s", _transLog->GetFileName(), getLastErrorString().c_str()));
            LOG(error, "%s", e.c_str());
            throw runtime_error(e);
        }
        if (currPos == 0) {
            // Previous header was truncated.  Write new one.
            writeHeader(fileHeaderContext);
            currPos = _headerLen;
        }
        _byteSize = currPos;
    } else {
        if ( ! _transLog->OpenWriteOnly()) {
            string e(fmt("Failed opening new file '%s' for writing: '%s'",
                         _transLog->GetFileName(), getLastErrorString().c_str()));

            LOG(error, "%s", e.c_str());
            throw runtime_error(e);
        }
        writeHeader(fileHeaderContext);
        _byteSize = _headerLen;
    }
    if ( ! _transLog->SetPosition(_transLog->GetSize()) ) {
        throw runtime_error(fmt("Failed moving write pointer to the end of the file %s(%" PRIu64 ").",
                                _transLog->GetFileName(), _transLog->GetSize()));
    }
    handleSync(*_transLog);
    _writtenSerial = get_range_to();
    _syncedSerial = _writtenSerial;
    assert(int64_t(byteSize()) == _transLog->GetSize());
    assert(int64_t(byteSize()) == _transLog->GetPosition());
}

DomainPart::~DomainPart()
{
    close();
}

void
DomainPart::writeHeader(const FileHeaderContext &fileHeaderContext)
{
    typedef vespalib::GenericHeader::Tag Tag;
    FileHeader header;
    assert(_transLog->IsOpened());
    assert(_transLog->IsWriteMode());
    assert(_transLog->GetPosition() == 0);
    fileHeaderContext.addTags(header, _transLog->GetFileName());
    header.putTag(Tag("desc", "Transaction log domain part file"));
    _headerLen = header.writeFile(*_transLog);
}

bool
DomainPart::close()
{
    bool retval(false);
    {
        std::lock_guard guard(_fileLock);
        /*
         * Sync old domainpart before starting writing new, to avoid
         * hole.  XXX: Feed latency spike due to lack of delayed open
         * for new domainpart.
         */
        handleSync(*_transLog);
        _transLog->dropFromCache();
        retval = _transLog->Close();
        std::lock_guard wguard(_writeLock);
        _syncedSerial = _writtenSerial;
    }
    if ( ! retval ) {
        throw runtime_error(fmt("Failed closing file '%s' of size %" PRId64 ".",
                                _transLog->GetFileName(), _transLog->GetSize()));
    }
    return retval;
}

bool
DomainPart::isClosed() const {
    return ! _transLog->IsOpened();
}

bool
DomainPart::openAndFind(FastOS_FileInterface &file, const SerialNum &from)
{
    bool retval(file.OpenReadOnly(_transLog->GetFileName()));
    if (retval) {
        int64_t pos(_headerLen);
        std::lock_guard guard(_lock);
        for (const auto & skipInfo : _skipList) {
            if (skipInfo.id() > from) break;
            pos = skipInfo.filePos();
        }
        retval = file.SetPosition(pos);
    }
    return retval;
}

bool
DomainPart::erase(SerialNum to)
{
    bool retval(true);
    if (to > get_range_to()) {
        close();
        _transLog->Delete();
    } else {
        auto range_from = get_range_from();
        if (to > range_from) {
            set_range_from(to);
        }
    }
    return retval;
}

void
DomainPart::commit(const SerializedChunk & serialized)
{
    SerialNumRange range = serialized.range();

    int64_t firstPos(byteSize());
    assert(get_range_to() < range.to());
    set_size(size() + serialized.getNumEntries());
    set_range_to(range.to());
    if (get_range_from() == 0) {
        set_range_from(range.from());
    }

    write(*_transLog, range, serialized.getData());
    std::lock_guard guard(_lock);
    _skipList.emplace_back(range.from(), firstPos);
}

void
DomainPart::sync()
{
    SerialNum syncSerial(0);
    {
        std::lock_guard guard(_writeLock);
        syncSerial = _writtenSerial;
    }
    std::lock_guard guard(_fileLock);
    handleSync(*_transLog);
    std::lock_guard wguard(_writeLock);
    if (_syncedSerial < syncSerial) {
        _syncedSerial = syncSerial;
    }
}

bool
DomainPart::visit(FastOS_FileInterface &file, SerialNumRange &r, Packet &packet)
{
    if ( ! file.IsOpened() && ! openAndFind(file, r.from() + 1)) {
        return false;
    }

    packet = readPacket(file, r, TARGET_PACKET_SIZE, false);
    if (!packet.empty()) {
        r.from(packet.range().to());
    }

    return ! packet.empty();
}

void
DomainPart::write(FastOS_FileInterface &file, SerialNumRange range, vespalib::ConstBufferRef buf)
{
    std::lock_guard guard(_writeLock);
    size_t written = file.Write2(buf.data(), buf.size());
    if ( written != buf.size() ) {
        throw runtime_error(handleWriteError("Failed writing the entry.", file, byteSize(), range, buf.size()));
    }
    LOG(debug, "Wrote chunk with and %zu bytes, range[%" PRIu64 ", %" PRIu64 "]", buf.size(), range.from(), range.to());
    _writtenSerial = range.to();
    _byteSize.fetch_add(buf.size(), std::memory_order_release);
}

bool
DomainPart::read(FastOS_FileInterface &file, IChunk::UP & chunk, Alloc & buf, bool allowTruncate)
{
    char tmp[5];
    int64_t lastKnownGoodPos(file.GetPosition());
    size_t rlen = file.Read(tmp, sizeof(tmp));
    nbostream his(tmp, sizeof(tmp));
    uint8_t encoding(-1);
    uint32_t len(0);
    his >> encoding >> len;
    if (rlen != sizeof(tmp)) {
        return (rlen == 0)
               ? true
               : handleReadError("packet length", file, sizeof(len), rlen, lastKnownGoodPos, allowTruncate);
    }

    try {
        chunk = IChunk::create(encoding);
    } catch (const std::exception & e) {
        string msg(fmt("Version mismatch. Expected 'ccitt_crc32=1' or 'xxh64=2', got %d from '%s' at position %" PRId64,
                       encoding, file.GetFileName(), lastKnownGoodPos));
        if ((encoding == 0) && (len == 0) && tailOfFileIsZero(file, lastKnownGoodPos)) {
            LOG(warning, "%s", msg.c_str());
            return handleReadError("packet version", file, sizeof(tmp), rlen, lastKnownGoodPos, allowTruncate);
        } else {
            throw runtime_error(msg);
        }
    }
    if (len > buf.size()) {
        Alloc::alloc(len).swap(buf);
    }
    rlen = file.Read(buf.get(), len);
    if (rlen != len) {
        return handleReadError("packet blob", file, len, rlen, lastKnownGoodPos, allowTruncate);
    }
    try {
        nbostream_longlivedbuf is(buf.get(), len);
        chunk->decode(is);
    } catch (const std::exception & e) {
        throw runtime_error(fmt("Got exception during decoding of packet '%s' from file '%s' (pos=%" PRId64 ", len=%d)",
                            e.what(), file.GetFileName(), file.GetPosition() - len - sizeof(len), static_cast<int>(len)));
    }
    return true;
}

}
