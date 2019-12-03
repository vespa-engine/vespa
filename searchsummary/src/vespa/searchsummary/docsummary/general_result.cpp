// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "general_result.h"
#include "resultconfig.h"
#include <vespa/document/fieldvalue/document.h>
#include <zlib.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.urlresult");

namespace search::docsummary {

void
GeneralResult::AllocEntries(uint32_t buflen, bool inplace)
{
    uint32_t cnt = _resClass->GetNumEntries();
    uint32_t needMem = (inplace)
                       ? cnt * sizeof(ResEntry)
                       : cnt * sizeof(ResEntry) + buflen + 1;

    if (cnt > 0) {
        _entrycnt = cnt;
        _entries = (ResEntry *) malloc(needMem);
        assert(_entries != nullptr);
        if (inplace) {
            _buf = nullptr;
            _bufEnd = nullptr;
        } else {
            _buf = ((char *)_entries) + cnt * sizeof(ResEntry);
            _bufEnd = _buf + buflen + 1;
        }
        memset(_entries, 0, cnt * sizeof(ResEntry));
    } else {
        _entrycnt = 0;
        _entries  = nullptr;
        _buf      = nullptr;
        _bufEnd   = nullptr;
    }
}

void
GeneralResult::FreeEntries()
{
    uint32_t cnt = _entrycnt;

    // (_buf == nullptr) <=> (_inplace_unpack() || (cnt == 0))
    if (_buf != nullptr) {
        for (uint32_t i = 0; i < cnt; i++) {
            if (ResultConfig::IsVariableSize(_entries[i]._type) &&
                !InBuf(_entries[i]._stringval))
                delete [] (_entries[i]._stringval);
        }
    }
    free(_entries); // free '_entries'/'_buf' chunk
}

GeneralResult::GeneralResult(const ResultClass *resClass)
    : _resClass(resClass),
      _entrycnt(0),
      _entries(nullptr),
      _buf(nullptr),
      _bufEnd(nullptr),
      _document()
{
}

GeneralResult::~GeneralResult()
{
    FreeEntries();
}

ResEntry *
GeneralResult::GetEntry(uint32_t idx)
{
    return (idx < _entrycnt) ? &_entries[idx] : nullptr;
}

ResEntry *
GeneralResult::GetEntry(const char *name)
{
    int idx = _resClass->GetIndexFromName(name);

    return (idx >= 0 && (uint32_t)idx < _entrycnt) ? &_entries[idx] : nullptr;
}


ResEntry *
GeneralResult::GetEntryFromEnumValue(uint32_t value)
{
    int idx = _resClass->GetIndexFromEnumValue(value);
    return (idx >= 0 && (uint32_t)idx < _entrycnt) ? &_entries[idx] : nullptr;
}

std::unique_ptr<document::FieldValue>
GeneralResult::get_field_value(const vespalib::string& field_name) const
{
    if (_document != nullptr) {
        return _document->getValue(field_name);
    }
    return std::unique_ptr<document::FieldValue>();
}

bool
GeneralResult::unpack(const char *buf, const size_t buflen)
{
    bool        rc   = true;
    const char *ebuf = buf + buflen;      // Ref to first after buffer
    const char *p    = buf;               // current position in buffer

    if (_entries != nullptr)
        FreeEntries();

    AllocEntries(buflen, true);

    for (uint32_t i = 0; rc && i < _entrycnt; i++) {
        const ResConfigEntry *entry = _resClass->GetEntry(i);

        switch (entry->_type) {

        case RES_INT: {
            if (p + sizeof(_entries[i]._intval) <= ebuf) {
                memcpy(&_entries[i]._intval, p, sizeof(_entries[i]._intval));
                _entries[i]._type = RES_INT;
                p += sizeof(_entries[i]._intval);
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(..._intval) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_SHORT: {
            uint16_t shortval;
            if (p + sizeof(shortval) <= ebuf) {
                memcpy(&shortval, p, sizeof(shortval));
                _entries[i]._intval = (uint32_t)shortval;
                _entries[i]._type = RES_INT; // type promotion
                p += sizeof(shortval);
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(shortval) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }
        case RES_BOOL:
        case RES_BYTE: {
            uint8_t byteval;
            if (p + sizeof(byteval) <= ebuf) {
                memcpy(&byteval, p, sizeof(byteval));
                _entries[i]._intval = (uint32_t)byteval;
                _entries[i]._type = RES_INT; // type promotion
                p += sizeof(byteval);
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(byteval) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_FLOAT: {
            float floatval;
            if (p + sizeof(floatval) <= ebuf) {
                memcpy(&floatval, p, sizeof(floatval));
                _entries[i]._doubleval = (double)floatval;
                _entries[i]._type = RES_DOUBLE; // type promotion
                p += sizeof(floatval);
            } else {
                LOG(debug, "GeneralResult::unpack: p + sizeof(floatval) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_DOUBLE: {
            if (p + sizeof(_entries[i]._doubleval) <= ebuf) {
                memcpy(&_entries[i]._doubleval, p, sizeof(_entries[i]._doubleval));
                _entries[i]._type = RES_DOUBLE;
                p += sizeof(_entries[i]._doubleval);
            } else {
                LOG(debug, "GeneralResult::unpack: p + sizeof(..._doubleval) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_INT64: {
            if (p + sizeof(_entries[i]._int64val) <= ebuf) {
                memcpy(&_entries[i]._int64val, p, sizeof(_entries[i]._int64val));
                _entries[i]._type = RES_INT64;
                p += sizeof(_entries[i]._int64val);
            } else {
                LOG(debug, "GeneralResult::unpack: p + sizeof(..._int64val) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_STRING: {
            uint16_t slen;
            if (p + sizeof(slen) <= ebuf) {
                memcpy(&slen, p, sizeof(slen));
                p += sizeof(slen);
                if (p + slen <= ebuf) {
                    _entries[i]._stringval = const_cast<char *>(p);
                    _entries[i]._stringlen = slen;
                    _entries[i]._type = RES_STRING;
                    p += slen;
                } else {
                    LOG(debug, "GeneralResult::_inplace_unpack: p + slen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(slen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_DATA: {
            uint16_t dlen;
            if (p + sizeof(dlen) <= ebuf) {
                memcpy(&dlen, p, sizeof(dlen));
                p += sizeof(dlen);
                if (p + dlen <= ebuf) {
                    _entries[i]._dataval = const_cast<char *>(p);
                    _entries[i]._datalen = dlen;
                    _entries[i]._type = RES_DATA;
                    p += dlen;
                } else {
                    LOG(debug, "GeneralResult::_inplace_unpack: p + dlen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(dlen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_XMLSTRING:
        case RES_JSONSTRING:
        case RES_FEATUREDATA:
        case RES_LONG_STRING: {
            uint32_t flen;
            uint32_t lslen;
            if (p + sizeof(flen) <= ebuf) {
                memcpy(&flen, p, sizeof(flen));
                p += sizeof(flen);
                lslen = flen & 0x7fffffff;
                if (p + lslen <= ebuf) {
                    _entries[i]._stringval = const_cast<char *>(p);
                    _entries[i]._stringlen = flen;  // with compression flag
                    _entries[i]._type = RES_STRING; // type normalization
                    p += lslen;
                } else {
                    LOG(debug, "GeneralResult::_inplace_unpack: p + lslen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(lslen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }
        case RES_TENSOR :
        case RES_LONG_DATA: {
            uint32_t flen;
            uint32_t ldlen;
            if (p + sizeof(flen) <= ebuf) {
                memcpy(&flen, p, sizeof(flen));
                p += sizeof(flen);
                ldlen = flen & 0x7fffffff;
                if (p + ldlen <= ebuf) {
                    _entries[i]._dataval = const_cast<char *>(p);
                    _entries[i]._datalen = flen;  // with compression flag
                    _entries[i]._type = RES_DATA; // type normalization
                    p += ldlen;
                } else {
                    LOG(debug, "GeneralResult::_inplace_unpack: p + ldlen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }
            } else {
                LOG(debug, "GeneralResult::_inplace_unpack: p + sizeof(ldlen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        default:
            LOG(warning, "GeneralResult::_inplace_unpack: no such type:%d", entry->_type);
            LOG(error, "Incorrect type in document summary, couldn't unpack");
            rc = false;
            break;
        } // END -- switch (entry->_type) {
    }   // END -- for (uint32_t i = 0; rc && i < _entrycnt; i++) {

    if (rc && p != ebuf) {
        LOG(debug, "GeneralResult::_inplace_unpack: p:%p != ebuf:%p", p, ebuf);
        LOG(error, "Document summary too long, couldn't unpack.");
        rc = false;
    }

    if (rc)
        return true;  // SUCCESS

    // clean up on failure
    FreeEntries();
    _entrycnt = 0;
    _entries  = nullptr;
    _buf      = nullptr;
    _bufEnd   = nullptr;

    return false;   // FAIL
}

}
