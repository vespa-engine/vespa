// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentlist.h"
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>
#include <vespa/document/util/stringutil.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/datatype/documenttype.h>

#include <vespa/log/log.h>
LOG_SETUP(".vdslib.container.documentlist");

using document::ByteBuffer;

namespace vdslib {

DocumentList::MetaEntry::MetaEntry()
{
    memset(this, 0, sizeof(MetaEntry));
}

void
DocumentList::MetaEntry::print(std::ostream& out, const std::string& indent) const
{
    (void) indent;
    out << "MetaEntry(Ts 0x" << std::hex << timestamp << ", h "
        << headerPos << "/" << headerLen << ", b " << bodyPos << "/" << bodyLen;
    if (flags & REMOVE_ENTRY) out << ", remove";
    if (flags & BODY_STRIPPED) out << ", body stripped";
    if (flags & BODY_IN_HEADER) out << ", body in header";
    out << ")";
}

DocumentList::Entry::Entry(char* start, uint32_t entry,
                           const document::DocumentTypeRepo &repo)
    : _metaEntry(reinterpret_cast<MetaEntry*>(
                        start + sizeof(uint32_t) + entry * sizeof(MetaEntry))),
      _start(start),
      _entry(entry),
      _repo(&repo)
{
}

DocumentList::Entry
DocumentList::Entry::next() const
{
    if (_entry + 1 >= *reinterpret_cast<uint32_t*>(_start)) {
        return Entry();
    }
    return Entry(_start, _entry + 1, *_repo);
}

document::DocumentId
DocumentList::Entry::getDocumentId() const
{
    ByteBuffer buf(_start + _metaEntry->headerPos, _metaEntry->headerLen);
    try {
        if (isUpdateEntry()) {
            document::DocumentUpdate::UP update(
                    document::DocumentUpdate::create42(*_repo, buf));
            return update->getId();
        } else {
            return document::Document::getIdFromSerialized(buf);
        }
    } catch (const document::DeserializeException& e) {
        std::ostringstream ss;
        ss << "Failed to deserialize document ID from " << *this;
        throw document::DeserializeException(ss.str(), e, VESPA_STRLOC);
    }
}

std::unique_ptr<document::Document>
DocumentList::Entry::getDocument(
        const document::DocumentType *anticipatedType) const
{
    if (isUpdateEntry()) {
        throw vespalib::IllegalStateException("Entry contains an update. "
                "Call getUpdate(), not getDocument()", VESPA_STRLOC);
    }
    ByteBuffer hbuf(_start + _metaEntry->headerPos, _metaEntry->headerLen);
    ByteBuffer bbuf(_start + _metaEntry->bodyPos, _metaEntry->bodyLen);
    std::unique_ptr<document::Document> doc;
    try {
        if (_metaEntry->bodyLen == 0) {
            doc.reset(new document::Document(*_repo, hbuf, anticipatedType));
        } else {
            doc.reset(new document::Document(*_repo, hbuf, bbuf, anticipatedType));
        }
    } catch (const document::DeserializeException& e) {
        std::ostringstream ss;
        ss << "Failed to deserialize document from " << *this;
        throw document::DeserializeException(ss.str(), e, VESPA_STRLOC);
    }
    if (hbuf.getRemaining() != 0 || bbuf.getRemaining() != 0) {
        assert(hbuf.getPos() + hbuf.getRemaining() == hbuf.getLength());
        assert(bbuf.getPos() + bbuf.getRemaining() == bbuf.getLength());
        throw document::DeserializeException(vespalib::make_string(
            "Deserializing document %s, only %lu of %lu header bytes and "
            "%lu of %lu body bytes were consumed.",
            doc->getId().toString().c_str(),
            hbuf.getPos(), hbuf.getLength(),
            bbuf.getPos(), bbuf.getLength()), VESPA_STRLOC);
    }
    doc->setLastModified(_metaEntry->timestamp);
    return doc;
}

std::unique_ptr<document::DocumentUpdate>
DocumentList::Entry::getUpdate() const
{
    if (!isUpdateEntry()) {
        throw vespalib::IllegalStateException("Entry contains a document. "
                "Call getDocument(), not getUpdate()", VESPA_STRLOC);
    }
    assert(_metaEntry->bodyLen == 0);
    ByteBuffer buf(_start + _metaEntry->headerPos, _metaEntry->headerLen);
    document::DocumentUpdate::UP update(
            document::DocumentUpdate::create42(*_repo, buf));
    if (buf.getRemaining() != 0) {
        assert(buf.getPos() + buf.getRemaining() == buf.getLength());
        throw document::DeserializeException(vespalib::make_string(
                "Deserializing document update %s, only %lu of %lu bytes "
                "were consumed.",
                update->getId().toString().c_str(),
                buf.getPos(), buf.getLength()), VESPA_STRLOC);
    }
    return update;
}

bool
DocumentList::Entry::getUpdate(document::DocumentUpdate& update) const
{
    if (!isUpdateEntry()) {
        throw vespalib::IllegalStateException("Entry contains a document. "
                "Call getDocument(), not getUpdate()", VESPA_STRLOC);
    }
    ByteBuffer buf(_start + _metaEntry->headerPos, _metaEntry->headerLen);
    assert(_metaEntry->bodyLen == 0);
    update.deserialize42(*_repo, buf);
    return (buf.getRemaining() == 0);
}


const DocumentList::Entry::BufferPosition
DocumentList::Entry::getRawHeader() const
{
    return BufferPosition(_start + _metaEntry->headerPos,
                          _metaEntry->headerLen);
}

const DocumentList::Entry::BufferPosition
DocumentList::Entry::getRawBody() const
{
    return BufferPosition(_start + _metaEntry->bodyPos, _metaEntry->bodyLen);
}

void
DocumentList::Entry::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "DocEntry(Timestamp: " << getTimestamp();
    if (isRemoveEntry()) out << ", removed";
    out << ", (h p/s " << _metaEntry->headerPos << "/" << _metaEntry->headerLen
        << ", b " << _metaEntry->bodyPos << "/" << _metaEntry->bodyLen << ")";
    if (verbose) {
        vespalib::string escaped;
        if (_metaEntry->headerLen > 0 && _metaEntry->headerLen < 256) {
            vespalib::string header(_start+_metaEntry->headerPos, _metaEntry->headerLen);
            out << "\n" << indent << "         "
                << document::StringUtil::escape(header, escaped);
        }
        if (_metaEntry->bodyLen > 0 && _metaEntry->bodyLen < 256) {
            vespalib::string body(_start + _metaEntry->bodyPos, _metaEntry->bodyLen);
            out << "\n" << indent << "         "
                << document::StringUtil::escape(body, escaped);
        }
    }
    out << ")";
}

DocumentList::const_iterator& DocumentList::const_iterator::operator++()
{
    assert(_entry.valid());
    _entry = _entry.next();
    return *this;
}

DocumentList::const_iterator DocumentList::const_iterator::operator++(int)
{
    const_iterator it(_entry);
    operator++();
    return it;
}

bool DocumentList::const_iterator::operator==(const const_iterator& it) const
{
    return (_entry == it._entry);
}

bool DocumentList::const_iterator::operator!=(const const_iterator& it) const
{
    return !(_entry == it._entry);
}


DocumentList::DocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer, uint32_t bufferSize, bool keepexisting)
    : _buffer(buffer),
      _bufferSize(bufferSize),
      _wasted(0),
      _freePtr(buffer),
      _repo(repo)
{
    init(keepexisting);
}

void DocumentList::init(bool keepexisting)
{
    if (_buffer == 0) {
        assert(_bufferSize == 0);
        return;
    }
    assert(_bufferSize > sizeof(uint32_t));
    if (keepexisting) {
        uint32_t min = 0xFFFFFFFF;
        for (uint32_t i=0, n=docCount(); i<n; ++i) {
            MetaEntry& entry(getMeta(i));
            if (entry.headerLen > 0 && entry.headerPos < min) {
                min = entry.headerPos;
            }
            if (entry.bodyLen > 0 && entry.bodyPos < min) {
                min = entry.bodyPos;
            }
        }
        if (docCount() > 0) {
            assert(min < _bufferSize);
            _freePtr += min;
        } else {
            _freePtr += _bufferSize;
        }
    } else {
        docCount() = 0;
        _freePtr += _bufferSize;
    }
    checkConsistency();
}

DocumentList::DocumentList(const DocumentList& source,
                           char* buffer, uint32_t bufferSize)
    : _buffer(buffer),
      _bufferSize(bufferSize),
      _wasted(0),
      _freePtr(buffer),
      _repo(source._repo)
{
    assert(buffer == 0 ? bufferSize == 0 : bufferSize >= sizeof(uint32_t));
    if (source.size() == 0) {
        if (buffer != 0) {
            docCount() = 0;
            _freePtr += bufferSize;
        }
        return;
    }

    // If we get here we know that source contains documents
    uint32_t n = source.docCount();
    uint64_t need = source.spaceNeeded();

    if (need > bufferSize) {
        std::ostringstream ost;
        ost << "Cannot create a documentlist of size " << bufferSize
            << " bytes containing the data of documentlist of size "
            << source.getBufferSize() << ", needing " << need
            << " bytes minimum.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    // If we get here we know that this object has enough space to fit all
    uint32_t pos = bufferSize;
    for (uint32_t i = 0; i < n; ++i) {
        MetaEntry& entry(getMeta(i) = source.getMeta(i));

        pos -= entry.bodyLen;
        memcpy(_buffer + pos, source._buffer + entry.bodyPos, entry.bodyLen);
        entry.bodyPos = pos;

        pos -= entry.headerLen;
        memcpy(_buffer + pos, source._buffer + entry.headerPos, entry.headerLen);
        entry.headerPos = pos;
    }
    _freePtr = _buffer + pos;
    docCount() = n;
    checkConsistency();
}

DocumentList::~DocumentList()
{
}

DocumentList::DocumentList(const DocumentList &rhs)
    : document::Printable(rhs),
      _buffer(rhs._buffer),
      _bufferSize(rhs._bufferSize),
      _wasted(rhs._wasted),
      _freePtr(rhs._freePtr),
      _repo(rhs._repo)
{
    checkConsistency();
}

DocumentList&
DocumentList::operator=(const DocumentList &rhs)
{
    document::Printable::operator=(rhs);
    _buffer = rhs._buffer;
    _bufferSize = rhs._bufferSize;
    _wasted = rhs._wasted;
    _freePtr = rhs._freePtr;
    _repo = _repo;
    checkConsistency();
    return *this;
}

namespace {
struct PosLen {
    uint32_t pos;
    uint32_t len;
    PosLen() : pos(0), len(0) {};
    bool operator< (const PosLen& other) const { return pos < other.pos; }
};
} // namespace

void
DocumentList::checkConsistency(bool do_memset)
{
    unsigned long need = spaceNeeded();
    unsigned long free = countFree();
    unsigned long bsiz = getBufferSize();
    if (do_memset || need + free + _wasted != bsiz) {
        std::vector<PosLen> blocks;
        uint32_t n = docCount();
        for (uint32_t i = 0; i < n; ++i) {
            MetaEntry& entry(getMeta(i));
            if (entry.headerLen > 0) {
                PosLen pl;
                pl.pos = entry.headerPos;
                pl.len = entry.headerLen;
                blocks.push_back(pl);
            }
            if (entry.bodyLen > 0) {
                PosLen pl;
                pl.pos = entry.bodyPos;
                pl.len = entry.bodyLen;
                blocks.push_back(pl);
            }
        }
        std::sort(blocks.begin(), blocks.end());
        _wasted = 0;
        uint32_t prevStart = bsiz;
        uint32_t prevLength = 0;
        for (uint32_t i = blocks.size(); i-- > 0; ) {
            uint32_t curEnd = blocks[i].pos + blocks[i].len;
            if (curEnd > prevStart
                && (blocks[i].pos != prevStart
                    || blocks[i].len != prevLength))
            {
                LOG(error, "DocumentList has overlapping blocks (block %u: curEnd(%u) > prevStart(%u))",
                    i, curEnd, prevStart);
                std::ostringstream oss;
                print(oss, true, "");
                fprintf(stderr, "%s\n", oss.str().c_str());
                assert(!"DocumentList has overlapping blocks!");
            }
            if (curEnd < prevStart) {
                uint32_t len = prevStart - curEnd;
                if (do_memset) {
                    LOG(debug, "waste %u bytes filled with 0xFF", len);
                    memset(_buffer + curEnd, 0xff, len);
                }
                _wasted += len;
            }
            prevStart = blocks[i].pos;
            prevLength = blocks[i].len;
        }
        if (_freePtr > _buffer + prevStart) {
            assert(!"_freePtr inside data block");
        }
        if (_freePtr < _buffer + prevStart) {
            // may be needed for alignment
            uint32_t len = _buffer + prevStart - _freePtr;
            if (do_memset) {
                LOG(debug, "waste %u bytes before start, filled with 0xFF", len);
                memset(_freePtr, 0xFF, len);
            }
            _wasted += len;
        }
        if (_freePtr < _buffer + sizeof(uint32_t) + sizeof(MetaEntry)*size()) {
            assert(!"_freePtr inside meta block");
        }
    }
}

void
DocumentList::print(std::ostream& out, bool verbose,
                const std::string& indent) const
{
    out << "DocumentList(buffer: " << (void*) _buffer << ", size: "
        << std::dec << _bufferSize << ", freeptr: " << (void*) _freePtr;
    if (_buffer != 0) {
        out << ", doccount: " << size();
        if (_bufferSize >= sizeof(MetaEntry) * size() + sizeof(uint32_t)) {
            for (uint32_t i=0, n=size(); i<n; ++i) {
                out << "\n" << indent << "         ";
                const MetaEntry& entry(getMeta(i));
                entry.print(out, indent + "         ");
                if (entry.headerPos + entry.headerLen > _bufferSize ||
                    entry.bodyPos + entry.bodyLen > _bufferSize) {
                    fprintf(stderr, " Invalid entry. Aborting print.\n");
                    return;
                }
            }
        } else {
            out << "\n" << indent << "  Too small to contain these entries.";
        }
    }
    uint32_t counter = 0;
    for (DocumentList::const_iterator it = begin(); it != end(); ++it) {
        out << "\n" << indent << "  ";
        if (++counter > 16) {
            out << "...";
            break;
        }
        it->print(out, verbose, indent + "  ");
    }
    if (verbose && _bufferSize < 256) {
        vespalib::string escaped;
        vespalib::string tmp(_buffer, _bufferSize);
        out << "\n" << indent << "  content: "
            << document::StringUtil::escape(tmp, escaped);
    }
    out << ")";
}

std::ostream& operator<<(std::ostream& out, const DocumentList::MetaEntry& e) {
    e.print(out);
    return out;
}

} // namespace vdslib
