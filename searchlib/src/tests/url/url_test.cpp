// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/url.h>
#include <cstdio>
#include <cstring>
#include <cassert>

// needed to resolve external symbol from httpd.h on AIX
void FastS_block_usr2() {}


static search::util::URL GlobalURL;

static bool
CheckString(const char *name,
            const unsigned char *test1,
            const unsigned char *test2)
{
    assert(test1 != NULL);
    assert(test2 != NULL);

    if (strcmp((const char*)test1, (const char*)test2)!=0) {
        printf("FAILED: %s: '%s' != '%s'!\n", name, test1, test2);
        GlobalURL.Dump();

        return false;
    }
    return true;
}

static bool
CheckInt(const char *name,
         int test1,
         int test2)
{
    if (test1 != test2) {
        printf("FAILED: %s: %d != %d!\n", name, test1, test2);
        GlobalURL.Dump();

        return false;
    }
    return true;
}

const char *
GetTokenString(search::util::URL &url)
{
    static char tokenbuffer[1000];

    const unsigned char *token;
    search::util::URL::URL_CONTEXT ctx;

    tokenbuffer[0] = '\0';

    while ((token = url.GetToken(ctx)) != NULL) {
        if (tokenbuffer[0] != '\0')
            strcat(tokenbuffer, ",");
        strcat(tokenbuffer, url.ContextName(ctx));
        strcat(tokenbuffer, ":");
        strcat(tokenbuffer, (const char*)token);
    }

    return tokenbuffer;
}


static bool
CheckURL(const char *url,
         const char *scheme,
         const char *host,
         const char *domain,
         const char *siteowner,
         const char *tld,
         const char *maintld,
         const char */* tldregion */,
         const char *port,
         const char *path,
         int pathdepth,
         const char *filename,
         const char *extension,
         const char *params,
         const char *query,
         const char *fragment,
         const char *address,
         const char *tokens,
         int verbose=0)
{
    if (verbose>0)
        printf("Checking with URL: '%s'\n", url);

    GlobalURL.SetURL((const unsigned char *)url);

    if (verbose>0)
        GlobalURL.Dump();
    //  GlobalURL.Dump();

    return
        CheckString("URL", (const unsigned char *)url, GlobalURL.GetURL()) &&
        CheckString("urltype", (const unsigned char *)scheme,
                    GlobalURL.GetScheme()) &&
        CheckString("host", (const unsigned char *)host,
                    GlobalURL.GetHost()) &&
        CheckString("domain", (const unsigned char *)domain,
                    GlobalURL.GetDomain()) &&
        CheckString("siteowner", (const unsigned char *)siteowner,
                    GlobalURL.GetSiteOwner()) &&
        CheckString("tld", (const unsigned char *)tld,
                    GlobalURL.GetTLD()) &&
        CheckString("maintld", (const unsigned char *)maintld,
                    GlobalURL.GetMainTLD()) &&
#if 0
        CheckString("tldregion", (const unsigned char *)tldregion,
                    GlobalURL.GetTLDRegion()) &&
#endif
        CheckString("port", (const unsigned char *)port,
                    GlobalURL.GetPort()) &&
        CheckString("path", (const unsigned char *)path,
                    GlobalURL.GetPath()) &&
        CheckInt("pathdepth", pathdepth,
                 GlobalURL.GetPathDepth()) &&
        CheckString("filename", (const unsigned char *)filename,
                    GlobalURL.GetFilename()) &&
        CheckString("extension", (const unsigned char *)extension,
                    GlobalURL.GetExtension()) &&
        CheckString("params", (const unsigned char *)params,
                    GlobalURL.GetParams()) &&
        CheckString("query", (const unsigned char *)query,
                    GlobalURL.GetQuery()) &&
        CheckString("fragment", (const unsigned char *)fragment,
                    GlobalURL.GetFragment()) &&
        CheckString("address", (const unsigned char *)address,
                    GlobalURL.GetAddress()) &&
        CheckString("TOKENS", (const unsigned char *)tokens,
                    (const unsigned char*)GetTokenString(GlobalURL));
}


int main(int, char **)
{
    bool success = true;

    success = success &&
              CheckURL("", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "");// Tokenstring
    success = success &&
              CheckURL(".", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       ".", // path
                       1, // pathdepth
                       ".", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "");// Tokenstring
    success = success &&
              CheckURL("..", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "..", // path
                       1, // pathdepth
                       "..", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "");// Tokenstring
    success = success &&
              CheckURL("CHANGES_2.0a", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "CHANGES_2.0a", // path
                       1, // pathdepth
                       "CHANGES_2.0a", // filename
                       "0a", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "FILENAME:CHANGES_2,EXTENSION:0a");// Tokenstring
    success = success &&
              CheckURL("patches/patch-cvs-1.9.10", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "patches/patch-cvs-1.9.10", // path
                       2, // pathdepth
                       "patch-cvs-1.9.10", // filename
                       "10", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:patches,FILENAME:patch-cvs-1,FILENAME:9,EXTENSION:10");// Tokenstring
    success = success &&
              CheckURL("http:patches/patch-ssh-1.2.14", // URL
                       "http", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "patches/patch-ssh-1.2.14", // path
                       2, // pathdepth
                       "patch-ssh-1.2.14", // filename
                       "14", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,PATH:patches,FILENAME:patch-ssh-1,FILENAME:2,EXTENSION:14");// Tokenstring
    success = success &&
              CheckURL("http://180.uninett.no/servlet/online.Bransje", // URL
                       "http", // scheme
                       "180.uninett.no", // host
                       "uninett.no", // domain
                       "uninett", // siteowner
                       "no", // tld
                       "no", // maintld
                       "europe", // tldregion
                       "", // port
                       "/servlet/online.Bransje", // path
                       2, // pathdepth
                       "online.Bransje", // filename
                       "Bransje", // extension
                       "", // query
                       "", // params
                       "", // fragment
                       "", // address
                       "SCHEME:http,HOST:180,DOMAIN:uninett,MAINTLD:no,PATH:servlet,FILENAME:online,EXTENSION:Bransje");// Tokenstring
    success = success &&
              CheckURL("Bilder.gif/rule11.GIF", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "Bilder.gif/rule11.GIF", // path
                       2, // pathdepth
                       "rule11.GIF", // filename
                       "GIF", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:Bilder,PATH:gif,FILENAME:rule11,EXTENSION:GIF");// Tokenstring
    success = success &&
              CheckURL("bilder/meny/Buer/bue_o.GIF", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "bilder/meny/Buer/bue_o.GIF", // path
                       4, // pathdepth
                       "bue_o.GIF", // filename
                       "GIF", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:bilder,PATH:meny,PATH:Buer,FILENAME:bue_o,EXTENSION:GIF");// Tokenstring
    success = success &&
              CheckURL("./fakadm/grafikk/indus_bilde.JPG", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "./fakadm/grafikk/indus_bilde.JPG", // path
                       4, // pathdepth
                       "indus_bilde.JPG", // filename
                       "JPG", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:fakadm,PATH:grafikk,FILENAME:indus_bilde,EXTENSION:JPG");// Tokenstring
    success = success &&
              CheckURL("linux-2.0.35.tar.bz2", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "linux-2.0.35.tar.bz2", // path
                       1, // pathdepth
                       "linux-2.0.35.tar.bz2", // filename
                       "bz2", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "FILENAME:linux-2,FILENAME:0,FILENAME:35,FILENAME:tar,EXTENSION:bz2");// Tokenstring
    success = success &&
              CheckURL("http://www.underdusken.no", // URL
                       "http", // scheme
                       "www.underdusken.no", // host
                       "underdusken.no", // domain
                       "underdusken", // siteowner
                       "no", // tld
                       "no", // maintld
                       "europe", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,HOST:www,DOMAIN:underdusken,MAINTLD:no");// Tokenstring
    success = success &&
              CheckURL("http://www.underdusken.no/?page=dusker/html/0008/Uholdbar.html", // URL
                       "http", // scheme
                       "www.underdusken.no", // host
                       "underdusken.no", // domain
                       "underdusken", // siteowner
                       "no", // tld
                       "no", // maintld
                       "europe", // tldregion
                       "", // port
                       "/", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "page=dusker/html/0008/Uholdbar.html", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,HOST:www,DOMAIN:underdusken,MAINTLD:no,QUERY:page,QUERY:dusker,QUERY:html,QUERY:0008,QUERY:Uholdbar,QUERY:html");// Tokenstring
    success = success &&
              CheckURL("http://www.uni-karlsruhe.de/~ig25/ssh-faq/", // URL
                       "http", // scheme
                       "www.uni-karlsruhe.de", // host
                       "uni-karlsruhe.de", // domain
                       "uni-karlsruhe", // siteowner
                       "de", // tld
                       "de", // maintld
                       "", // tldregion
                       "", // port
                       "/~ig25/ssh-faq/", // path
                       2, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,HOST:www,DOMAIN:uni-karlsruhe,MAINTLD:de,PATH:ig25,PATH:ssh-faq");// Tokenstring
    success = success &&
              CheckURL("java/", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "java/", // path
                       1, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:java");// Tokenstring
    success = success &&
              CheckURL("javascript:OpenWindow('/survey/faq.html', 'Issues', 'width=635,height=400,toolbars=no,location=no,menubar=yes,status=no,resizable=yes,scrollbars=yes", // URL
                       "javascript", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "OpenWindow('/survey/faq.html', 'Issues', 'width=635,height=400,toolbars=no,location=no,menubar=yes,status=no,resizable=yes,scrollbars=yes", // address
                       "SCHEME:javascript,ADDRESS:OpenWindow,ADDRESS:survey,ADDRESS:faq,ADDRESS:html,ADDRESS:Issues,ADDRESS:width,ADDRESS:635,ADDRESS:height,ADDRESS:400,ADDRESS:toolbars,ADDRESS:no,ADDRESS:location,ADDRESS:no,ADDRESS:menubar,ADDRESS:yes,ADDRESS:status,ADDRESS:no,ADDRESS:resizable,ADDRESS:yes,ADDRESS:scrollbars,ADDRESS:yes");// Tokenstring
    success = success &&
              CheckURL("mailto: dmf-post@medisin.ntnu.no", // URL
                       "mailto", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       " dmf-post@medisin.ntnu.no", // address
                       "SCHEME:mailto,ADDRESS:dmf-post,ADDRESS:medisin,ADDRESS:ntnu,ADDRESS:no");// Tokenstring
    success = success &&
              CheckURL("mailto:%20Harald%20Danielsen@energy.sintef.no", // URL
                       "mailto", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "%20Harald%20Danielsen@energy.sintef.no", // address
                       "SCHEME:mailto,ADDRESS:20Harald,ADDRESS:20Danielsen,ADDRESS:energy,ADDRESS:sintef,ADDRESS:no");// Tokenstring
    success = success &&
              CheckURL("www.underdusken.no", // URL
                       "", // scheme
                       "www.underdusken.no", // host
                       "underdusken.no", // domain
                       "underdusken", // siteowner
                       "no", // tld
                       "no", // maintld
                       "europe", // tldregion
                       "", // port
                       "", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "HOST:www,DOMAIN:underdusken,MAINTLD:no");// Tokenstring
    success = success &&
              CheckURL("~janie/", // URL
                       "", // scheme
                       "", // host
                       "", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "~janie/", // path
                       1, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "PATH:janie");// Tokenstring
    success = success &&
              CheckURL("https://dette.er.en:2020/~janie/index.htm?param1=q&param2=r", // URL
                       "https", // scheme
                       "dette.er.en", // host
                       "er.en", // domain
                       "er", // siteowner
                       "en", // tld
                       "en", // maintld
                       "", // tldregion
                       "2020", // port
                       "/~janie/index.htm", // path
                       2, // pathdepth
                       "index.htm", // filename
                       "htm", // extension
                       "", // params
                       "param1=q&param2=r", // query
                       "", // fragment
                       "", // address
                       "SCHEME:https,HOST:dette,DOMAIN:er,MAINTLD:en,PORT:2020,PATH:janie,FILENAME:index,EXTENSION:htm,QUERY:param1,QUERY:q,QUERY:param2,QUERY:r");// Tokenstring
#if 0
    success = success &&
              CheckURL("http://www.sony.co.uk/", // URL
                       "http", // scheme
                       "www.sony.co.uk", // host
                       "sony.co.uk", // domain
                       "sony", // siteowner
                       "co.uk", // tld
                       "uk", // maintld
                       "unitedkingdom", // tldregion
                       "", // port
                       "/", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,HOST:www,DOMAIN:sony,DOMAIN:co,MAINTLD:uk");// Tokenstring
    success = success &&
              CheckURL("http://sony.co.uk/", // URL
                       "http", // scheme
                       "sony.co.uk", // host
                       "sony.co.uk", // domain
                       "sony", // siteowner
                       "co.uk", // tld
                       "uk", // maintld
                       "unitedkingdom", // tldregion
                       "", // port
                       "/", // path
                       0, // pathdepth
                       "", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,DOMAIN:sony,DOMAIN:co,MAINTLD:uk");// Tokenstring
#endif
    // Test fixes for bugs reported in cvs commit:
    // toregge       2000/10/27 22:42:59 CEST
    success = success &&
              CheckURL("http://somehost.somedomain/this!is!it/boom", // URL
                       "http", // scheme
                       "somehost.somedomain", // host
                       "somehost.somedomain", // domain
                       "somehost", // siteowner
                       "somedomain", // tld
                       "somedomain", // maintld
                       "", // tldregion
                       "", // port
                       "/this!is!it/boom", // path
                       2, // pathdepth
                       "boom", // filename
                       "", // extension
                       "", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,DOMAIN:somehost,MAINTLD:somedomain,PATH:this,PATH:is,PATH:it,FILENAME:boom");// Tokenstring
    success = success &&
              CheckURL("http://test.com/index.htm?p1=q%20test&p2=r%10d", // URL
                       "http", // scheme
                       "test.com", // host
                       "test.com", // domain
                       "test", // siteowner
                       "com", // tld
                       "com", // maintld
                       "northamerica", // tldregion
                       "", // port
                       "/index.htm", // path
                       1, // pathdepth
                       "index.htm", // filename
                       "htm", // extension
                       "", // params
                       "p1=q%20test&p2=r%10d", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,DOMAIN:test,MAINTLD:com,FILENAME:index,EXTENSION:htm,QUERY:p1,QUERY:q,QUERY:20test,QUERY:p2,QUERY:r,QUERY:10d");// Tokenstring

    // Test bugs found 2001/06/25
    success = success &&
              CheckURL("http://arthur/qm/images/qm1.gif", // URL
                       "http", // scheme
                       "arthur", // host
                       "arthur", // domain
                       "", // siteowner
                       "", // tld
                       "", // maintld
                       "", // tldregion
                       "", // port
                       "/qm/images/qm1.gif", // path
                       3, // pathdepth
                       "qm1.gif", // filename
                       "gif", // extension
                       "", // params
                       "", // query
                       "", // address
                       "", // fragment
                       "SCHEME:http,MAINTLD:arthur,PATH:qm,PATH:images,FILENAME:qm1,EXTENSION:gif");// Tokenstring

    // Test Orjan's hypothesis 2003/02/17
    success = success &&
              CheckURL("http://foo.com/ui;.gif", // URL
                       "http", // scheme
                       "foo.com", // host
                       "foo.com", // domain
                       "foo", // siteowner
                       "com", // tld
                       "com", // maintld
                       "northamerica", // tldregion
                       "", // port
                       "/ui;.gif", // path
                       1, // pathdepth
                       "ui", // filename
                       "", // extension
                       ".gif", // params
                       "", // query
                       "", // address
                       "", // fragment
                       "SCHEME:http,DOMAIN:foo,MAINTLD:com,FILENAME:ui,PARAMS:gif");// Tokenstring

    // Test Orjan's hypothesis 2003/02/17
    success = success &&
              CheckURL("http://foo.com/ui;.gif", // URL
                       "http", // scheme
                       "foo.com", // host
                       "foo.com", // domain
                       "foo", // siteowner
                       "com", // tld
                       "com", // maintld
                       "northamerica", // tldregion
                       "", // port
                       "/ui;.gif", // path
                       1, // pathdepth
                       "ui", // filename
                       "", // extension
                       ".gif", // params
                       "", // query
                       "", // address
                       "", // fragment
                       "SCHEME:http,DOMAIN:foo,MAINTLD:com,FILENAME:ui,PARAMS:gif");// Tokenstring

    // Verify params handling
    success = success &&
              CheckURL("http://foo.com/ui;par1=1/par2=2", // URL
                       "http", // scheme
                       "foo.com", // host
                       "foo.com", // domain
                       "foo", // siteowner
                       "com", // tld
                       "com", // maintld
                       "northamerica", // tldregion
                       "", // port
                       "/ui;par1=1/par2=2", // path
                       1, // pathdepth
                       "ui", // filename
                       "", // extension
                       "par1=1/par2=2", // params
                       "", // query
                       "", // fragment
                       "", // address
                       "SCHEME:http,DOMAIN:foo,MAINTLD:com,FILENAME:ui,PARAMS:par1,PARAMS:1,PARAMS:par2,PARAMS:2");// Tokenstring

    // Verify synthetic url
    success = success &&
              CheckURL("http://www.foo.no:8080/path/filename.ext;par1=hello/par2=world?query=test#fragment", // URL
                       "http", // scheme
                       "www.foo.no", // host
                       "foo.no", // domain
                       "foo", // siteowner
                       "no", // tld
                       "no", // maintld
                       "europe", // tldregion
                       "8080", // port
                       "/path/filename.ext;par1=hello/par2=world", // path
                       2, // pathdepth
                       "filename.ext", // filename
                       "ext", // extension
                       "par1=hello/par2=world", // params
                       "query=test", // query
                       "fragment", // fragment
                       "", // address
                       "SCHEME:http,HOST:www,DOMAIN:foo,MAINTLD:no,PORT:8080,PATH:path,FILENAME:filename,EXTENSION:ext,PARAMS:par1,PARAMS:hello,PARAMS:par2,PARAMS:world,QUERY:query,QUERY:test,FRAGMENT:fragment");// Tokenstring

    // '&' should be allowed in path according to RFC 1738, 2068 og 2396
    success = success &&
              CheckURL("http://canonsarang.com/zboard/data/gallery04/HU&BANG.jpg", // URL
                       "http", // scheme
                       "canonsarang.com", // host
                       "canonsarang.com", // domain
                       "canonsarang", // siteowner
                       "com", // tld
                       "com", // maintld
                       "northamerica", // tldregion
                       "", // port
                       "/zboard/data/gallery04/HU&BANG.jpg", // path
                       4, // pathdepth
                       "HU&BANG.jpg", // filename
                       "jpg", // extension
                       "", // params
                       "", // query
                       "", // address
                       "", // fragment
                       "SCHEME:http,DOMAIN:canonsarang,MAINTLD:com,PATH:zboard,PATH:data,PATH:gallery04,FILENAME:HU,FILENAME:BANG,EXTENSION:jpg");// Tokenstring

    return !success;
}
