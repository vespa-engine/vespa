// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#ifndef MAX_URL_LEN
#define MAX_URL_LEN 4096
#endif

/**
 * Class that parses URL's and split them into
 * a number of subelements. Detects different types
 * of "URL's", such as http:, https:, ftp:, mailto:,
 * file:, etc. Only http: and https: URL's are
 * processed into smaller subelements. For http: and
 * https: URL's, the parser tries to locate the name
 * of the owner of the domain ('siteowner') by
 * extracting the last word before the TLD from the
 * domain part of the URL. A list of TLD's may be
 * loaded to improve the siteowner extraction algorithm.
 * The class handles relative as well as absolute URL's.
 *
 * Note that memory consumption is quite high for this version,
 * roughly 40kB / instance.
 */

#include <cstddef>

namespace search::util {

class URL
{
private:
    URL(const URL &);
    URL& operator=(const URL &);

public:
    enum URL_CONTEXT {
        URL_SCHEME,
        URL_HOST,
        URL_DOMAIN,
        URL_MAINTLD,
        URL_PORT,
        URL_PATH,
        URL_FILENAME,
        URL_EXTENSION,
        URL_PARAMS,
        URL_QUERY,
        URL_FRAGMENT,
        URL_ADDRESS
    };

protected:

    unsigned char _url[MAX_URL_LEN+1];
    unsigned char _scheme[MAX_URL_LEN+1];
    unsigned char _host[MAX_URL_LEN+1];
    unsigned char _siteowner[MAX_URL_LEN+1];
    unsigned char _port[MAX_URL_LEN+1];
    unsigned char _path[MAX_URL_LEN+1];
    unsigned char _filename[MAX_URL_LEN+1];
    unsigned char _extension[MAX_URL_LEN+1];
    unsigned char _params[MAX_URL_LEN+1];
    unsigned char _query[MAX_URL_LEN+1];
    unsigned char _fragment[MAX_URL_LEN+1];
    unsigned char _address[MAX_URL_LEN+1];
    unsigned char *_maintld;
    const unsigned char *_tld;
    const unsigned char *_domain;
    const unsigned char *_tldregion;
    unsigned char _emptystring[1];
    int _pathDepth;
    unsigned char _token[MAX_URL_LEN+1];

    unsigned char *_startScheme;
    unsigned char *_startHost;
    unsigned char *_startDomain;
    unsigned char *_startMainTld;
    unsigned char *_startPort;
    unsigned char *_startPath;
    unsigned char *_startFileName;
    unsigned char *_startExtension;
    unsigned char *_startParams;
    unsigned char *_startQuery;
    unsigned char *_startFragment;
    unsigned char *_startAddress;
    unsigned char *_tokenPos;

    bool _gotCompleteURL;

    void Reset();

    template <bool (*IsPartChar)(unsigned char c)>
    static unsigned char *ParseURLPart(unsigned char *url, unsigned char *buf, unsigned int bufsize);

public:
    static bool IsAlphaChar(unsigned char c);
    static bool IsDigitChar(unsigned char c);
    static bool IsMarkChar(unsigned char c);
    static bool IsUnreservedChar(unsigned char c);
    static bool IsEscapedChar(unsigned char c);
    static bool IsReservedChar(unsigned char c);
    static bool IsUricChar(unsigned char c);
    static bool IsPChar(unsigned char c);

    static bool IsSchemeChar(unsigned char c);
    static bool IsHostChar(unsigned char c);
    static bool IsPortChar(unsigned char c);
    static bool IsPathChar(unsigned char c);
    static bool IsFileNameChar(unsigned char c);
    static bool IsParamsChar(unsigned char c);
    static bool IsParamChar(unsigned char c);
    static bool IsQueryChar(unsigned char c);
    static bool IsFragmentChar(unsigned char c);

    static bool IsTokenChar(unsigned char c);

    /**
     * Defautl constructor. Optionally, the URL to be parsed may be given
     * as a parameter.
     *
     * @param url The URL to parse.
     * @param length The length of url.
     */
    URL(const unsigned char *url=0, size_t length=0);

    /**
     * Use a new URL to be parsed and split into subelements.
     *
     * @param url The URL to parse.
     * @param length The length of url.
     */
    void SetURL(const unsigned char *url, size_t length=0);

    /**
     * Check if the current URL is a base (absolute) URL.
     *
     * @return true if this is an absolute URL, false otherwise.
     */
    bool IsBaseURL() const;

    /**
     * Get a pointer to the current URL.
     * @return Pointer to string containing the URL, "" if none set.
     */
    const unsigned char *GetURL() const {return _url;}

    /**
     * Get the scheme part of the current URL (e.g. "http", "mailto", etc).
     * @return Pointer to string containing the scheme, "" if none found.
     */
    const unsigned char *GetScheme() const {return _scheme;}

    /**
     * Get the host part of the current URL.
     * @return Pointer to string containing the host name, "" if none found.
     */
    const unsigned char *GetHost() const {return _host;}

    /**
     * Get the domain part of the current URL.
     * @return Pointer to string containing the domain name, "" if none found.
     */
    const unsigned char *GetDomain() const {return _domain;}

    /**
     * Get the siteowner part of the current URL.
     * @return Pointer to string containing the siteowner, "" if none found.
     */
    const unsigned char *GetSiteOwner() const {return _siteowner;}

    /**
     * Get the region correlated to the document tld. I.e. 'no', 'com', etc.
     * @return Pointer to string containing the tld name, "" if none found.
     */
    const unsigned char *GetMainTLD() const {return _maintld;}
    unsigned char *GetMainTLD_NoConst() const {return _maintld;}

    /**
     * Similar til GetMainTLD, but includes tld's taken from the tldlist file;
     * may return strings like 'co.uk.'.
     * @return Pointer to string containing the tld name, "" if none found.
     */
    const unsigned char *GetTLD() const {return _tld;}

    /**
     * Get the region correlated to the document tld. I.e. 'europe' for '.no'.
     * @return Pointer to string containing the region name, "" if none found.
     */
    const unsigned char *GetTLDRegion() const {return _tldregion;}

    /**
     * Get the port part of the current URL.
     * @return Pointer to string containing the port, "" if none found.
     */
    const unsigned char *GetPort() const {return _port;}

    /**
     * Get the path part of the current URL.
     * @return Pointer to string containing the path, "" if none found.
     */
    const unsigned char *GetPath() const {return _path;}

    /**
     * Get the path part of the current URL.
     * @return Pointer to string containing the path, "" if none found.
     */
    unsigned int GetPathDepth() const {return _pathDepth;}

    /**
     * Get the filename part of the current URL.
     * @return Pointer to string containing the filename, "" if none found.
     */
    const unsigned char *GetFilename() const {return _filename;}

    /**
     * Get the filename extension of the current URL.
     * @return Pointer to string containing the extension, "" if none found.
     */
    const unsigned char *GetExtension() const {return _extension;}

    /**
     * Get the params information part of the current URL. This is the part
     * of the URL located between the filename and the params parts of the URL.
     * @return Pointer to string containing the params part, "" if none found.
     */
    const unsigned char *GetParams() const {return _params;}

    /**
     * Get the query information part of the current URL. This is the part
     * of the URL located between the path and the fragment parts of the URL.
     * @return Pointer to string containing the param part, "" if none found.
     */
    const unsigned char *GetQuery() const {return _query;}

    /**
     * Get the fragment part of the current URL. This is
     * treated as everythin behind any '#' character in the URL.
     * @return Pointer to string containing the fragment, "" if none found.
     */
    const unsigned char *GetFragment() const {return _fragment;}

    /**
     * Get the adress part of the current URL. In the current version,
     * this is everything behind the type field if different from
     * http: and https:.
     * @return Pointer to string containing the address, "" if none found.
     */
    const unsigned char *GetAddress() const {return _address;}

    /**
     * Get tokens with corresponding context information from the current url.
     * The first call to this function will return the first token in the url.
     * This function may be called repetedly untill the value nullptr is returned.
     * @return Pointer to string containing the token, nullptr when all tokens have
     * been returned.
     */
    const unsigned char *GetToken(URL_CONTEXT &ctx);

    /**
     * Get a pointer to a string that contains the name of a given context.
     * @return Pointer to string containing the name of a given contexttoken.
     */
    const char *ContextName(URL_CONTEXT ctx);

    /**
     * Dump the contents of the URL and subelements to stdout. Only
     * elements that contains information are shown.
     */
    void Dump();
};

}
