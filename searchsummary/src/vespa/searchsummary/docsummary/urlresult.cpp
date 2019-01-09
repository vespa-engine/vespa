// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "urlresult.h"
#include "resultconfig.h"
#include <zlib.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.urlresult");

namespace search::docsummary {

urlresult::urlresult(uint32_t partition, uint32_t docid, HitRank metric)
    : _partition(partition),
      _docid(docid),
      _metric(metric)
{ }


urlresult::~urlresult() = default;


/*===============================================================*/


badurlresult::badurlresult()
    : urlresult(0, 0, 0)
{ }


badurlresult::badurlresult(uint32_t partition, uint32_t docid, HitRank metric)
    : urlresult(partition, docid, metric)
{ }


badurlresult::~badurlresult() = default;


int
badurlresult::unpack(const char *, const size_t )
{
    LOG(warning, "badurlresult::unpack");
    return 0;
}


/*===============================================================*/


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



GeneralResult::GeneralResult(const ResultClass *resClass, uint32_t partition, uint32_t docid, HitRank metric)
    : urlresult(partition, docid, metric),
      _resClass(resClass),
      _entrycnt(0),
      _entries(nullptr),
      _buf(nullptr),
      _bufEnd(nullptr)
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

    return (idx >= 0 && (uint32_t)idx < _entrycnt) ?
                   &_entries[idx] : nullptr;
}


ResEntry *
GeneralResult::GetEntryFromEnumValue(uint32_t value)
{
    int idx = _resClass->GetIndexFromEnumValue(value);

    return (idx >= 0 && (uint32_t)idx < _entrycnt) ?
                   &_entries[idx] : nullptr;
}


int
GeneralResult::unpack(const char *buf, const size_t buflen)
{
    bool        rc   = true;
    const char *ebuf = buf + buflen;      // Ref to first after buffer
    const char *p    = buf;               // current position in buffer

    if (_entries != nullptr)
        FreeEntries();

    AllocEntries(buflen);

    for (uint32_t i = 0; rc && i < _entrycnt; i++) {
        const ResConfigEntry *entry = _resClass->GetEntry(i);

        switch (entry->_type) {

        case RES_INT: {

            if (p + sizeof(_entries[i]._intval) <= ebuf) {

                memcpy(&_entries[i]._intval, p, sizeof(_entries[i]._intval));
                _entries[i]._type = RES_INT;
                p += sizeof(_entries[i]._intval);

            } else {

                LOG(debug, "GeneralResult::unpack: p + sizeof(..._intval) > ebuf");
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

                LOG(debug, "GeneralResult::unpack: p + sizeof(shortval) > ebuf");
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

                LOG(debug, "GeneralResult::unpack: p + sizeof(byteval) > ebuf");
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

                    _entries[i]._stringval = _buf + (p - buf);
                    memcpy(_entries[i]._stringval, p, slen);
                    _entries[i]._stringval[slen] = '\0';
                    _entries[i]._stringlen = slen;
                    _entries[i]._type = RES_STRING;
                    p += slen;

                } else {

                    LOG(debug, "GeneralResult::unpack: p + slen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }

            } else {

                LOG(debug, "GeneralResult::unpack: p + sizeof(slen) > ebuf");
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

                    _entries[i]._dataval = _buf + (p - buf);
                    memcpy(_entries[i]._dataval, p, dlen);
                    _entries[i]._dataval[dlen] = '\0'; // just in case.
                    _entries[i]._datalen = dlen;
                    _entries[i]._type = RES_DATA;
                    p += dlen;

                } else {

                    LOG(debug, "GeneralResult::unpack: p + dlen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }

            } else {

                LOG(debug, "GeneralResult::unpack: p + sizeof(dlen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_XMLSTRING:
        case RES_JSONSTRING:
        case RES_FEATUREDATA:
        case RES_LONG_STRING: {

            uint32_t lslen;
            bool compressed;
            if (p + sizeof(lslen) <= ebuf) {

                memcpy(&lslen, p, sizeof(lslen));
                p += sizeof(lslen);

                compressed = ((lslen & 0x80000000) != 0);
                lslen &= 0x7fffffff;

                if (p + lslen <= ebuf) {

                    if (compressed) {               // COMPRESSED
                        uint32_t realLen = 0;
                        if (lslen >= sizeof(realLen))
                            memcpy(&realLen, p, sizeof(realLen));
                        else
                            LOG(warning, "Cannot uncompress docsum field %s; docsum field meta-data incomplete",
                                entry->_bindname.c_str());
                        if (realLen > 0) {
                            _entries[i]._stringval = new char[realLen + 1];
                        }
                        if (_entries[i]._stringval != nullptr) {
                            uLongf rlen = realLen;
                            if ((uncompress((Bytef *)_entries[i]._stringval, &rlen,
                                            (const Bytef *)(p + sizeof(realLen)),
                                            lslen - sizeof(realLen)) == Z_OK) &&
                                rlen == realLen) {
                                assert(rlen == realLen);

                                // COMPRESSED LONG STRING FIELD OK
                                _entries[i]._stringval[realLen] = '\0';
                                _entries[i]._stringlen = realLen;

                            } else {
                                LOG(warning, "Cannot uncompress docsum field %s; decompression error",
                                    entry->_bindname.c_str());
                                delete [] _entries[i]._stringval;
                                _entries[i]._stringval = nullptr;
                            }
                        }
                        // insert empty field if decompress failed
                        if (_entries[i]._stringval == nullptr) {
                            _entries[i]._stringval    = _buf + (p - buf);
                            _entries[i]._stringval[0] = '\0';
                            _entries[i]._stringlen    = 0;
                        }

                    } else {                        // UNCOMPRESSED

                        _entries[i]._stringval = _buf + (p - buf);
                        memcpy(_entries[i]._stringval, p, lslen);
                        _entries[i]._stringval[lslen] = '\0';
                        _entries[i]._stringlen = lslen;

                    }
                    _entries[i]._type = RES_STRING; // type normalization
                    p += lslen;

                } else {

                    LOG(debug, "GeneralResult::unpack: p + lslen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }

            } else {

                LOG(debug, "GeneralResult::unpack: p + sizeof(lslen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        case RES_TENSOR:
        case RES_LONG_DATA: {

            uint32_t ldlen;
            bool compressed;
            if (p + sizeof(ldlen) <= ebuf) {

                memcpy(&ldlen, p, sizeof(ldlen));
                p += sizeof(ldlen);

                compressed = ((ldlen & 0x80000000) != 0);
                ldlen &= 0x7fffffff;

                if (p + ldlen <= ebuf) {

                    if (compressed) {               // COMPRESSED
                        uint32_t realLen = 0;
                        if (ldlen >= sizeof(realLen))
                            memcpy(&realLen, p, sizeof(realLen));
                        else
                            LOG(warning, "Cannot uncompress docsum field %s; docsum field meta-data incomplete",
                                entry->_bindname.c_str());
                        if (realLen > 0) {
                            _entries[i]._dataval = new char [realLen + 1];
                        }
                        if (_entries[i]._dataval != nullptr) {
                            uLongf rlen = realLen;
                            if ((uncompress((Bytef *)_entries[i]._dataval, &rlen,
                                            (const Bytef *)(p + sizeof(realLen)),
                                            ldlen - sizeof(realLen)) == Z_OK) &&
                                rlen == realLen) {
                                assert(rlen == realLen);

                                // COMPRESSED LONG DATA FIELD OK
                                _entries[i]._dataval[realLen] = '\0';
                                _entries[i]._datalen = realLen;

                            } else {
                                LOG(warning, "Cannot uncompress docsum field %s; decompression error",
                                    entry->_bindname.c_str());
                                delete [] _entries[i]._dataval;
                                _entries[i]._dataval = nullptr;
                            }
                        }

                        // insert empty field if decompress failed
                        if (_entries[i]._dataval == nullptr) {
                            _entries[i]._dataval    = _buf + (p - buf);
                            _entries[i]._dataval[0] = '\0';
                            _entries[i]._datalen    = 0;
                        }

                    } else {                        // UNCOMPRESSED

                        _entries[i]._dataval = _buf + (p - buf);
                        memcpy(_entries[i]._dataval, p, ldlen);
                        _entries[i]._dataval[ldlen] = '\0'; // just in case
                        _entries[i]._datalen = ldlen;

                    }
                    _entries[i]._type = RES_DATA; // type normalization
                    p += ldlen;

                } else {

                    LOG(debug, "GeneralResult::unpack: p + ldlen > ebuf");
                    LOG(error, "Document summary too short, couldn't unpack");
                    rc = false;
                }

            } else {

                LOG(debug, "GeneralResult::unpack: p + sizeof(ldlen) > ebuf");
                LOG(error, "Document summary too short, couldn't unpack");
                rc = false;
            }
            break;
        }

        default:
            LOG(warning, "GeneralResult::unpack: no such type:%d", entry->_type);
            LOG(error, "Incorrect type in document summary, couldn't unpack");
            rc = false;
            break;
        } // END -- switch (entry->_type) {
    }   // END -- for (uint32_t i = 0; rc && i < _entrycnt; i++) {

    if (rc && p != ebuf) {
        LOG(debug, "GeneralResult::unpack: p:%p != ebuf:%p", p, ebuf);
        LOG(error, "Document summary too long, couldn't unpack.");
        rc = false;
    }

    if (rc)
        return 0;  // SUCCESS

    // clean up on failure
    FreeEntries();
    _entrycnt = 0;
    _entries  = nullptr;
    _buf      = nullptr;
    _bufEnd   = nullptr;

    return -1;   // FAIL
}


bool
GeneralResult::_inplace_unpack(const char *buf, const size_t buflen)
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
