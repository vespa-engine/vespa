// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "url.h"
#include <algorithm>
#include <cstdio>
#include <cstring>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.util.url");

namespace search::util {

bool
URL::IsAlphaChar(unsigned char c) // According to RFC2396
{
    return (c>='A' && c<='Z') || (c>='a' && c<='z');
}

bool
URL::IsDigitChar(unsigned char c) // According to RFC2396
{
    return (c>='0' && c<='9');
}

bool
URL::IsMarkChar(unsigned char c) // According to RFC2396
{
    return (c=='-' || c=='_' || c=='.' || c=='!' || c=='~' ||
            c=='*' || c=='\'' || c=='(' || c==')');
}

bool
URL::IsUnreservedChar(unsigned char c) // According to RFC2396
{
    return (IsAlphaChar(c) ||
            IsDigitChar(c) ||
            IsMarkChar(c));
}

bool
URL::IsEscapedChar(unsigned char c) // According to RFC2396
{
    // Cheat! Shoud be ('%' hex hex)
    return (c=='%');
}

bool
URL::IsReservedChar(unsigned char c) // According to RFC2396
{
    return (c==';' || c=='/' || c=='?' || c==':' || c=='@' ||
            c=='&' || c=='=' || c=='+' || c=='$' || c==',');
}

bool
URL::IsPChar(unsigned char c) // According to RFC2068
{
    return (IsUnreservedChar(c) ||
            IsEscapedChar(c) ||
            (c==':' || c=='@' || c=='&' || c=='=' || c=='+' ||
             c=='$' || c==','));
}
bool
URL::IsUricChar(unsigned char c) // According to RFC2068
{
    return (IsUnreservedChar(c) ||
            IsEscapedChar(c) ||
            IsReservedChar(c));
}





bool
URL::IsSchemeChar(unsigned char c) // According to RFC2068
{
    return (IsAlphaChar(c) ||
            IsDigitChar(c) ||
            c=='+' || c=='-' || c=='.');
}

bool
URL::IsHostChar(unsigned char c) // According to RFC2068
{
    return (IsAlphaChar(c) ||
            IsDigitChar(c) ||
            c=='.' || c=='+' || c=='-');
}

bool
URL::IsPortChar(unsigned char c) // According to RFC2068
{
    return IsDigitChar(c);
}

bool
URL::IsPathChar(unsigned char c) // According to RFC2068
{
    return (IsPChar(c) ||
            c=='/' || c==';');
}

bool
URL::IsFileNameChar(unsigned char c) // According to RFC2068
{
    return IsPChar(c);
}

bool
URL::IsParamChar(unsigned char c) // According to RFC2068
{
    return IsPChar(c) || c=='/';
}

bool
URL::IsParamsChar(unsigned char c) // According to RFC2068
{
    return IsParamChar(c) || c==';';
}

bool
URL::IsQueryChar(unsigned char c) // According to RFC2068
{
    return IsUricChar(c);
}

bool
URL::IsFragmentChar(unsigned char c) // According to RFC2068
{
    return IsUricChar(c);
}

bool
URL::IsTokenChar(unsigned char c) // According to FAST URL tokenization
{
    return (IsAlphaChar(c) ||
            IsDigitChar(c) ||
            c == '_' || c == '-');
}

template <bool (*IsPartChar)(unsigned char c)>
unsigned char *
URL::ParseURLPart(unsigned char *src,
            unsigned char *dest,
            unsigned int destsize)
{
    unsigned char *p = src;
    unsigned int len = 0;

    while (IsPartChar(*p) && len<(destsize-1)) {
        len++;
        p++;
    }
    if (len > 0) {
        memcpy(dest, src, len);
        dest[len] = '\0';
    }

    return p;
}


URL::URL(const unsigned char *url, size_t len) :
    _maintld(_emptystring),
    _tld(reinterpret_cast<const unsigned char *>("")),
    _domain(reinterpret_cast<const unsigned char *>("")),
    _tldregion(reinterpret_cast<const unsigned char *>("")),
    _pathDepth(0),
    _startScheme(&_token[sizeof(_token) - 1]),
    _startHost(&_token[sizeof(_token) - 1]),
    _startDomain(&_token[sizeof(_token) - 1]),
    _startMainTld(&_token[sizeof(_token)-1]),
    _startPort(&_token[sizeof(_token)-1]),
    _startPath(&_token[sizeof(_token)-1]),
    _startFileName(&_token[sizeof(_token) - 1]),
    _startExtension(&_token[sizeof(_token) - 1]),
    _startParams(&_token[sizeof(_token) - 1]),
    _startQuery(&_token[sizeof(_token) - 1]),
    _startFragment(&_token[sizeof(_token) - 1]),
    _startAddress(&_token[sizeof(_token) - 1]),
    _tokenPos(_url),
    _gotCompleteURL(false)
{
    Reset();
    if (url != nullptr)
        SetURL(url, len);
}


void
URL::Reset()
{
    _gotCompleteURL = false;

    _emptystring[0] = '\0';

    _url[0]       = '\0';
    _scheme[0]    = '\0';
    _host[0]      = '\0';
    _siteowner[0] = '\0';
    _port[0]      = '\0';
    _path[0]      = '\0';
    _filename[0]  = '\0';
    _extension[0] = '\0';
    _params[0]    = '\0';
    _query[0]     = '\0';
    _fragment[0]  = '\0';
    _address[0]   = '\0';
    _maintld      = _emptystring; // Hack needed to please langid.
    _tld          = (const unsigned char *) "";
    _domain       = (const unsigned char *) "";
    _tldregion    = (const unsigned char *) "";
    _pathDepth    = 0;

    _token[0]     = '\0';

    _startScheme    = &_token[sizeof(_token)-1];
    _startHost      = &_token[sizeof(_token)-1];
    _startDomain    = &_token[sizeof(_token)-1];
    _startMainTld   = &_token[sizeof(_token)-1];
    _startPort      = &_token[sizeof(_token)-1];
    _startPath      = &_token[sizeof(_token)-1];
    _startFileName  = &_token[sizeof(_token)-1];
    _startExtension = &_token[sizeof(_token)-1];
    _startParams    = &_token[sizeof(_token)-1];
    _startQuery     = &_token[sizeof(_token)-1];
    _startFragment  = &_token[sizeof(_token)-1];
    _startAddress   = &_token[sizeof(_token)-1];

    _tokenPos       = _url;
}

void
URL::SetURL(const unsigned char *url, size_t length)
{
    int len = 0;
    unsigned char
        *p, *ptmp, *siteowner = 0, *filename = 0, *extension = 0;

    Reset();
    if (length > MAX_URL_LEN) {
        LOG(warning,
            "Max link size overflow: len=%lu, max=%d",
            static_cast<unsigned long>(length), MAX_URL_LEN);
        length = MAX_URL_LEN;
    }
    if (length == 0)
        length = MAX_URL_LEN;

    strncpy(reinterpret_cast<char *>(_url),
            reinterpret_cast<const char *>(url), length);
    _url[length] = '\0';

    p = _url;

    // Look for ':' as the first non-scheme-char character. If so => scheme
    for (p = _url, len = 0; *p != '\0' && IsSchemeChar(*p); p++, len++)
        ;

    if (*p++ == ':') {
        strncpy(reinterpret_cast<char *>(_scheme),
                reinterpret_cast<char *>(_url), len);
        _scheme[len] = '\0';
        _startScheme = _url;
    } else
        p = _url;

    // get host name
    if ((strncasecmp(reinterpret_cast<char *>(_scheme), "http", 4) == 0 &&
         p[0] == '/' && p[1] == '/') ||
        strncasecmp(reinterpret_cast<char *>(_url), "www.", 4) == 0) {
        if (p[0] == '/' && p[1] == '/')
            p += 2;
        _startHost = p;
        p = ParseURLPart<IsHostChar>(p, _host, sizeof(_host));

        // Locate siteowner. eg. 'www.sony.com' => 'sony'
        if (_host[0] != '\0') {
            unsigned char *pso;

            int solen = 0;

            // First check entries from config.
            siteowner = pso = _host;

            for (solen = 0; *pso != '\0'; pso++, solen++) {
                if (*pso == '.') {
                    siteowner = pso + 1;
                    solen = -1;
                }
            }
            _domain = siteowner;
            _startDomain = _startHost + (siteowner - _host);
            _startMainTld = _startDomain;

            // Locate main-tld info.
            ptmp = reinterpret_cast<unsigned char *>
                   (strrchr(reinterpret_cast<char *>(_host), '.'));
            if (ptmp != nullptr) {
                _maintld = &ptmp[1];
                _startMainTld = _startHost + (_maintld - _host);
                if (*_tld == '\0') {
                    _tld = _maintld;
                }
            }

            // If siteowner is not found in config entries use second latest word in host.
            if (_siteowner[0] == '\0') {
                pso = reinterpret_cast<unsigned char *>
                      (strrchr(reinterpret_cast<char *>(_host), '.'));
                if (pso != nullptr && pso > _host) {
                    pso--;
                    solen = 0;
                    while (pso > _host && *pso != '.') {
                        solen++;
                        pso--;
                    }
                    if (*pso != '.')
                        solen++;
                    else
                        pso++;
                    if (solen > 0) {
                        strncpy(reinterpret_cast<char *>(_siteowner),
                                reinterpret_cast<char *>(pso), solen);
                        _siteowner[solen] = '\0';
                        _startDomain = _startHost + (pso - _host);
                        _domain = pso;
                    }
                }
            }
        }

        // Parse port number
        if (*p == ':') {
            p++;
            _startPort = p;
            p = ParseURLPart<IsDigitChar>(p, _port, sizeof(_port));
        }
    }

    if (_scheme[0] == '\0' ||
        strncasecmp(reinterpret_cast<char *>(_scheme), "http", 4) == 0) {
        // Handle http url.

        // Parse path, filename, extension.
        _startPath = p;
        p = ParseURLPart<IsPathChar>(p, _path, sizeof(_path));

        filename = _path;
        if (IsFileNameChar(*filename))
            _pathDepth++;
        for (ptmp = _path ; *ptmp != '\0' && *ptmp != ';' ; ptmp++)
            if (*ptmp == '/') {
                filename = ptmp + 1;
                if (IsFileNameChar(*filename))
                    _pathDepth++;
            }
        _startFileName = _startPath + (filename - _path);
        ParseURLPart<IsFileNameChar>(filename, _filename, sizeof(_filename));

        extension = reinterpret_cast<unsigned char *>
                    (strrchr(reinterpret_cast<char *>(_filename), '.'));
        if (extension != nullptr) {
            extension++;
            strcpy(reinterpret_cast<char *>(_extension),
                   reinterpret_cast<char *>(extension));
            _startExtension = _startFileName + (extension - _filename);
        }

        // Parse params part.
        if ((ptmp = reinterpret_cast<unsigned char *>
             (strchr(reinterpret_cast<char *>(_path), ';'))) != nullptr) {
            ptmp++;
            _startParams = _startPath + (ptmp - _path);
            ParseURLPart<IsParamsChar>(ptmp, _params, sizeof(_params));
        }

        // Parse query part.
        if (*p == '?') {
            p++;
            _startQuery = p;
            p = ParseURLPart<IsQueryChar>(p, _query, sizeof(_query));
        }

        // Parse fragment part
        if (*p == '#') {
            p++;
            _startFragment = p;
            p = ParseURLPart<IsFragmentChar>(p, _fragment, sizeof(_fragment));
        }
    }
    // stuff the rest into address
    _startAddress = p;
    _address[0] = '\0';
    ssize_t sz = length - (p - _url);
    if (sz > 0) {
        ssize_t toCopy = std::min(ssize_t(sizeof(_address) - 1), sz);
        memcpy(_address, p, toCopy);
        _address[toCopy] = '\0';
    }
}

bool
URL::IsBaseURL() const
{
    return (_scheme[0] != '\0' &&
            _host[0] != '\0' &&
            _path[0] == '/');
}

const unsigned char *
URL::GetToken(URL_CONTEXT &ctx)
{
    int i = 0;

    // Skip whitespace
    while (!IsTokenChar(*_tokenPos) && *_tokenPos != '\0')
        _tokenPos++;

    while (IsTokenChar(*_tokenPos))
        _token[i++] = *_tokenPos++;
    _token[i] = '\0';

    ctx = URL_SCHEME;
    if (_tokenPos > _startHost)
        ctx = URL_HOST;
    if (_tokenPos > _startDomain)
        ctx = URL_DOMAIN;
    if (_tokenPos > _startMainTld)
        ctx = URL_MAINTLD;
    if (_tokenPos > _startPort)
        ctx = URL_PORT;
    if (_tokenPos > _startPath)
        ctx = URL_PATH;
    if (_tokenPos > _startFileName)
        ctx = URL_FILENAME;
    if (_tokenPos > _startExtension)
        ctx = URL_EXTENSION;
    if (_tokenPos > _startParams)
        ctx = URL_PARAMS;
    if (_tokenPos > _startQuery)
        ctx = URL_QUERY;
    if (_tokenPos > _startFragment)
        ctx = URL_FRAGMENT;
    if (_tokenPos > _startAddress)
        ctx = URL_ADDRESS;

    if (_token[0] != '\0')
        return _token;
    else
        return nullptr;
}

const char *
URL::ContextName(URL_CONTEXT ctx)
{
    switch (ctx) {
    case URL_SCHEME:
        return "SCHEME";
    case URL_HOST:
        return "HOST";
    case URL_DOMAIN:
        return "DOMAIN";
    case URL_MAINTLD:
        return "MAINTLD";
    case URL_PORT:
        return "PORT";
    case URL_PATH:
        return "PATH";
    case URL_FILENAME:
        return "FILENAME";
    case URL_EXTENSION:
        return "EXTENSION";
    case URL_PARAMS:
        return "PARAMS";
    case URL_QUERY:
        return "QUERY";
    case URL_FRAGMENT:
        return "FRAGMENT";
    case URL_ADDRESS:
        return "ADDRESS";
    }

    return "UNKNOWN";
}

void
URL::Dump()
{
    printf("URL: '%s'\n", _url);

    if (_scheme[0] != '\0')
        printf("  scheme:    '%s'\n", _scheme);
    if (_host[0] != '\0')
        printf("  host:      '%s'\n", _host);
    if (_domain[0] != '\0')
        printf("  domain: '%s'\n", _domain);
    if (_siteowner[0] != '\0')
        printf("  siteowner: '%s'\n", _siteowner);
    if (_maintld[0] != '\0')
        printf("  maintld:   '%s'\n", _maintld);
    if (_tld[0] != '\0')
        printf("  tld:       '%s'\n", _tld);
    if (_tldregion[0] != '\0')
        printf("  tldregion: '%s'\n", _tldregion);
    if (_port[0] != '\0')
        printf("  port:      '%s'\n", _port);
    if (_path[0] != '\0')
        printf("  path:      '%s'\n", _path);
    if (_pathDepth != 0)
        printf("  pathdepth: '%d'\n", _pathDepth);
    if (_filename[0] != '\0')
        printf("  filename:  '%s'\n", _filename);
    if (_extension[0] != '\0')
        printf("  extension: '%s'\n", _extension);
    if (_params[0] != '\0')
        printf("  params:    '%s'\n", _params);
    if (_query[0] != '\0')
        printf("  query:     '%s'\n", _query);
    if (_fragment[0] != '\0')
        printf("  fragment:  '%s'\n", _fragment);
    if (_address[0] != '\0')
        printf("  address:   '%s'\n", _address);

    printf("_startScheme:    '%s'\n", _startScheme);
    printf("_startHost:      '%s'\n", _startHost);
    printf("_startDomain:    '%s'\n", _startDomain);
    printf("_startMainTld:   '%s'\n", _startMainTld);
    printf("_startPort:      '%s'\n", _startPort);
    printf("_startPath:      '%s'\n", _startPath);
    printf("_startFileName:  '%s'\n", _startFileName);
    printf("_startExtension: '%s'\n", _startExtension);
    printf("_startParams:    '%s'\n", _startParams);
    printf("_startQuery:     '%s'\n", _startQuery);
    printf("_startFragment:  '%s'\n", _startFragment);
    printf("_startAddress:   '%s'\n", _startAddress);

    const unsigned char *token;
    URL_CONTEXT ctx;
    while ((token = GetToken(ctx)) != nullptr) {
        printf("TOKEN: %s '%s'\n", ContextName(ctx), token);
    }
}

}
