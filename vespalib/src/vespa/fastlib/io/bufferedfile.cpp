// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferedfile.h"
#include <cassert>
#include <cstring>
#include <cinttypes>

namespace {

const size_t DEFAULT_BUF_SIZE = 0x200000;
const size_t MIN_ALIGNMENT = 0x1000;

}

void
Fast_BufferedFile::flushWriteBuf()
{
    if (_bufi != buf()) {
        _file->WriteBuf(buf(), _bufi - buf());
        _filepos += _bufi - buf();
        _bufi = buf();
    }
    // Calculate how much the buffer can be filled before next write attempt
    size_t nextwrite = _buf.size();
    if (_directIOEnabled && (_filepos & (MIN_ALIGNMENT - 1)) != 0) {
        // Align end of next write to direct IO boundary
        size_t maxWrite = MIN_ALIGNMENT - (_filepos & (MIN_ALIGNMENT - 1));
        nextwrite = std::min(nextwrite, maxWrite);
    }
    _bufe = buf() + nextwrite;
}

void
Fast_BufferedFile::fillReadBuf()
{
    size_t toread = std::min(static_cast<int64_t>(_buf.size()), _fileleft);
    if (toread > 0) {
        _file->ReadBuf(buf(), toread, _filepos);
        _filepos += toread;
        _bufe = buf() + toread;
        _fileleft -= toread;
    } else {
        _bufe = buf();
        _fileleft = 0;
    }
    _bufi = buf();
}

void
Fast_BufferedFile::addNum(unsigned int num, int fieldw, char fill)
{
    char buf1[20];
    char *p = buf1;
    do {
        *p++ = '0' + (num % 10);
        num /= 10;
    } while (num != 0);
    while (p - buf1 < fieldw) {
        if (_bufi >= _bufe)
            flushWriteBuf();
        while (p - buf1 < fieldw && _bufi < _bufe) {
            *_bufi++ = fill;
            fieldw--;
        }
    }
    while (p > buf1) {
        if (_bufi >= _bufe)
            flushWriteBuf();
        while (p > buf1 && _bufi < _bufe)
            *_bufi++ = *--p;
    }
}

bool
Fast_BufferedFile::Eof() const
{
    return _fileleft == 0 && _bufi == _bufe;
}

int64_t
Fast_BufferedFile::GetSize()
{
    return _file->GetSize();
}

bool
Fast_BufferedFile::SetSize (int64_t s)
{
    Flush();
    bool res = _file->SetSize(s);
    if (res) {
        _filepos = s;
    }
    return res;
}

bool
Fast_BufferedFile::IsOpened () const
{
    return _file->IsOpened();
}

bool
Fast_BufferedFile::Sync()
{
    Flush();
    return _file->Sync();
}

time_t
Fast_BufferedFile::GetModificationTime()
{
    time_t retval = _file->GetModificationTime();
    return retval;
}

void
Fast_BufferedFile::EnableDirectIO()
{
    _file->EnableDirectIO();
    _directIOEnabled = true;
}

void
Fast_BufferedFile::EnableSyncWrites()
{
    FastOS_FileInterface::EnableSyncWrites();
    _file->EnableSyncWrites();
}

int64_t
Fast_BufferedFile::GetPosition()
{
    if (_file->IsWriteMode()) {
        int64_t filePosition = _file->GetPosition();
        return (filePosition == -1) ? -1 : filePosition + (_bufi - buf());
    } else {
        return _filepos - (_bufe - _bufi);
    }
}


void
Fast_BufferedFile::Flush()
{
    if (_file->IsWriteMode()) {
        flushWriteBuf();
    }
    ResetBuf();
}


bool
Fast_BufferedFile::SetPosition(const int64_t s)
{
    if (_file->IsWriteMode()) {
        Flush();
        bool res = _file->SetPosition(s);
        if (res) {
            _filepos = s;
        }
        return res;
    } else {
        int64_t diff = _filepos - s;
        if ((diff <= 0l) || (diff > (_bufe - buf()))) {
            const int64_t newPos(s & ~(_buf.size() - 1l) );
            if ((s - newPos) >= static_cast<int64_t>(_buf.size())) {
                abort();
            }
            int64_t oldPos(_filepos);
            int64_t oldLeft(_fileleft);
            _fileleft -= (newPos - oldPos);
            _filepos = newPos;
        
            fillReadBuf();
           
            if ((oldLeft == _fileleft) && (_fileleft != 0l)) {
                abort();
            }
            if ((_filepos == oldPos) && (_fileleft != 0l)) {
                abort();
            }
            if ((_filepos < s) || ((_filepos == s) && (_fileleft != 0))) {
                abort();
            }
            diff = _filepos - s;
            if ( !(((diff > 0l) || ((diff == 0l) && (_fileleft == 0l))) && (diff <= static_cast<int64_t>(_buf.size())))) {
                char tmp[64];
                snprintf(tmp, sizeof(tmp), "diff %" PRId64 " _fileleft=%" PRId64 " _buflen=%zu", diff, _fileleft, _buf.size());
                abort();
            }
        }
        _bufi = _bufe - diff;
        return true;
    }
}

const char *
Fast_BufferedFile::GetFileName() const
{
    return _file ? _file->GetFileName() : "";
}

char *
Fast_BufferedFile::ReadLine(char *line, size_t buflen)
{
    char *p;
    char *ep;

    p = line;
    ep = line + buflen - 1;
    while (1) {
        while (_bufi < _bufe && *_bufi != '\n' && p < ep)
            *p++ = *_bufi++;
        if (p >= ep) {
            *p = 0;
            return line;
        }
        if (_bufi >= _bufe) {
            fillReadBuf();
            if (_bufi >= _bufe) {
                if (p == line)
                    return nullptr;
                *p = 0;
                return line;
            }
            continue;
        }
        *p++ = *_bufi++;
        *p = 0;
        return line;
    }
}

ssize_t
Fast_BufferedFile::Write2(const void * src, size_t srclen)
{
    const char *p, *pe;
    p = static_cast<const char *>(src);
    pe = p + srclen;
    while (p < pe) {
        if (_bufi >= _bufe) {
            flushWriteBuf();
        }
        while (p < pe && _bufi < _bufe) {
            *_bufi++ = *p++;
        }
    }
    return srclen;
}

void
Fast_BufferedFile::WriteString(const char *src)
{
    while (*src) {
        if (_bufi >= _bufe)
            flushWriteBuf();
        while (*src && _bufi < _bufe)
            *_bufi++ = *src++;
    }
}

ssize_t
Fast_BufferedFile::Read(void *dst, size_t dstlen)
{
    char * p = static_cast<char *>(dst);
    char * pe = p + dstlen;
    while (1) {
        int64_t sz = std::min(_bufe - _bufi, pe - p);
        memcpy(p, _bufi, sz);
        p += sz;
        _bufi += sz;
        if (p >= pe)
            break;
        fillReadBuf();
        if (_bufi >= _bufe)
            break;
    }
    return p - static_cast<char *>(dst);
}

void
Fast_BufferedFile::WriteByte(char byte)
{
    if (_bufi >= _bufe) {
        flushWriteBuf();
    }
    *_bufi++ = byte;
}

void
Fast_BufferedFile::ReadOpenExisting(const char *name)
{
    bool ok = Close();
    ok &= _file->OpenReadOnlyExisting(true, name);
    if (!ok) {
        fprintf(stderr, "ERROR opening %s for read: %s",
                _file->GetFileName(), getLastErrorString().c_str());
        assert(ok);
    }
    _openFlags = FASTOS_FILE_OPEN_READ;
    //CASTWARN
    _fileleft = static_cast<uint64_t>(GetSize());
    _filepos = 0;
    ResetBuf();
}

void
Fast_BufferedFile::ReadOpen(const char *name)
{
    bool ok = Close();
    ok &= _file->OpenReadOnly(name);
    if (!ok) {
        fprintf(stderr, "ERROR opening %s for read: %s",
                _file->GetFileName(), getLastErrorString().c_str());
        assert(ok);
    }
    if (_file->IsOpened()) {
        //CASTWARN
        _fileleft = static_cast<uint64_t>(GetSize());
        _openFlags = FASTOS_FILE_OPEN_READ;
    } else
        _fileleft = 0;
    _filepos = 0;
    ResetBuf();
}

void
Fast_BufferedFile::WriteOpen(const char *name)
{
    bool ok = Close();
    ok &= _file->OpenWriteOnly(name);
    if (!ok) {
        fprintf(stderr, "ERROR opening %s for write: %s",
                _file->GetFileName(), getLastErrorString().c_str());
        assert(ok);
    }
    _filepos = 0;
    ResetBuf();
    if (_file->IsOpened())
        _openFlags = FASTOS_FILE_OPEN_WRITE;
}

Fast_BufferedFile::Fast_BufferedFile(FastOS_FileInterface *file) :
    Fast_BufferedFile(file, DEFAULT_BUF_SIZE)
{
}

Fast_BufferedFile::Fast_BufferedFile() :
    Fast_BufferedFile(DEFAULT_BUF_SIZE) 
{
}

Fast_BufferedFile::Fast_BufferedFile(size_t bufferSize) :
    Fast_BufferedFile(new FastOS_File(), bufferSize)
{
}

namespace {

size_t computeBufLen(size_t buflen)
{
    size_t bitCount;
    for ( bitCount = 1; buflen >> bitCount; bitCount++);
    buflen = 1 << (bitCount - 1);

    if (buflen & (MIN_ALIGNMENT-1)) {
        buflen = std::max(MIN_ALIGNMENT, buflen & ~(MIN_ALIGNMENT-1));
    }
    return buflen;
}

}

Fast_BufferedFile::Fast_BufferedFile(FastOS_FileInterface *file, size_t bufferSize) :
    FastOS_FileInterface(),
    _fileleft(static_cast<uint64_t>(-1)),
    _buf(vespalib::alloc::Alloc::alloc(computeBufLen(bufferSize))),
    _bufi(nullptr),
    _bufe(nullptr),
    _filepos(0),
    _directIOEnabled(false),
    _file(file)
{
    ResetBuf();
}

Fast_BufferedFile::~Fast_BufferedFile()
{
    bool close_ok = Close();
    assert(close_ok);
}

void
Fast_BufferedFile::ResetBuf()
{
    _bufi = buf();
    _bufe = _bufi;
}

bool
Fast_BufferedFile::Close()
{
    Flush();
    _openFlags = 0;
    ResetBuf();
    return _file->Close();
}

bool Fast_BufferedFile::Open(unsigned int openFlags, const char * name)
{
    bool ok;
    if (openFlags & FASTOS_FILE_OPEN_READ) {
        ok = Close();
        _filepos = 0;
        _fileleft = 0;
        ResetBuf();

        ok &= _file->Open(openFlags, name);
        if (ok) {
            _openFlags = openFlags;
            //CASTWARN
            _fileleft = static_cast<uint64_t>(GetSize());
        } else {
            // caller will have to check return value
        }
    } else {
        ok = Close();
        _filepos = 0;
        ResetBuf();
        ok &= _file->Open(FASTOS_FILE_OPEN_WRITE | openFlags, name);
        if (ok) {
            _openFlags = FASTOS_FILE_OPEN_WRITE | openFlags;
        } else {
            // caller will have to check return value
        }
    }
    return ok;
}

bool Fast_BufferedFile::Delete()
{
    return _file->Delete();
}

void Fast_BufferedFile::alignEndForDirectIO()
{
    while( (_bufi - buf())%MIN_ALIGNMENT ) {
        WriteByte(0);
    }
}
