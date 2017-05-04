// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*******************************************************************************
*
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-1-7
* @version         $Id$
*
* @file
*
* Generic http server and connection classes
*
* Copyright (c)  : 1997-1999 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/


#pragma once

#include <vespa/fastlib/io/inputstream.h>
#include <vespa/fastlib/io/outputstream.h>
#include <vespa/fastlib/net/socket.h>
#include <vespa/fastlib/util/bag.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastos/thread.h>
#include <vespa/fastos/cond.h>
#include <vespa/fastos/serversocket.h>

class FastOS_FileInterface;
class Fast_HTTPServer;

#define FASTLIB_SUCCESS (0)
#define FASTLIB_FAILURE (1)

/**
********************************************************************************
*
* Generic HTTP connection class
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-1-7
* @version         $Id$
*
* Generic HTTP connection class
*
* Copyright (c)  : 1997-1999 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/

// Error codes
#define FASTLIB_HTTPSERVER_NEWTHREADFAILED (2)
#define FASTLIB_HTTPSERVER_BADLISTEN       (3)
#define FASTLIB_HTTPSERVER_ALREADYSTARTED  (4)

// Default buffer sizes for HTTPConnection streams
#define FASTLIB_HTTPSERVER_INBUFSIZE   32768
#define FASTLIB_HTTPSERVER_OUTBUFSIZE  32768


class Fast_HTTPConnection : public FastOS_Runnable
{
private:
    Fast_HTTPConnection(const Fast_HTTPConnection&);
    Fast_HTTPConnection& operator=(const Fast_HTTPConnection&);

protected:

    bool                  _decode; // Decode incoming URLs?
    Fast_Socket          *_socket;
    Fast_InputStream     *_input;
    Fast_OutputStream    *_output;
    Fast_HTTPServer      *_server;
    bool                  _chunkedInput;
    bool                  _chunkedOutput;
    bool                  _keepAlive;

    vespalib::string      _auth_user;
    vespalib::string      _auth_pass;

    uint32_t              _versionMajor;
    uint32_t              _versionMinor;
    vespalib::string      _httpVersion;
    vespalib::string      _cookies;

public:

    Fast_HTTPConnection(Fast_Socket *sock,
                        bool decode = true,
                        size_t inBufSize = 32768,
                        size_t outbufSize = 32768);

    virtual ~Fast_HTTPConnection(void);

    void Run (FastOS_ThreadInterface *thisThread, void *params) override;
    void Output(const char *outputString);
    void OutputData(const void *data, size_t len);
    void OutputFile(FastOS_FileInterface *file);

    const Fast_InputStream *GetInputStream() const {return _input;}
    Fast_InputStream *GetInputStream() {return _input;}

    const Fast_OutputStream *GetOutputStream() const {return _output;}
    Fast_OutputStream *GetOutputStream() {return _output;}

    void Interrupt();
    unsigned short GetPort() const {return _socket == 0 ? 0 : _socket->GetPort();}

    const vespalib::string & AuthUser() { return _auth_user; }
    const vespalib::string & AuthPass() { return _auth_pass; }

    const vespalib::string & GetHTTPVersion() { return _httpVersion; }
    void SetKeepAlive(bool keepAlive = true) { _keepAlive = keepAlive; }
    bool GetKeepAlive() { return _keepAlive; }

    const vespalib::string & GetCookies() { return _cookies; }

};



class Fast_HTTPServerSocketFactory : public FastOS_SocketFactory
{
private:
    int _readTimeout; // Timeout value for reads.

public:

    Fast_HTTPServerSocketFactory(int readTimeout = -1 /* no timeout */)
        : _readTimeout(readTimeout) {}

    /**
     * Create a streaming socket object
     */
    FastOS_SocketInterface *CreateSocket() override {
        return new Fast_Socket(_readTimeout);
    }
};



/**
********************************************************************************
*
* Generic HTTP server class
*
* @author          Stein Hardy Danielsen
* @date            Creation date: 2000-1-7
* @version         $Id$
*
* Copyright (c)  : 1997-1999 Fast Search & Transfer ASA
*                  ALL RIGHTS RESERVED
*
******************************************************************************/
class Fast_HTTPServer : public FastOS_Runnable
{
private:
    Fast_HTTPServer(const Fast_HTTPServer&);
    Fast_HTTPServer& operator=(const Fast_HTTPServer&);

    Fast_Bag<Fast_HTTPConnection*> _connections;
    FastOS_Cond _connectionCond;

protected:
    typedef vespalib::string string;

    /** Threadpool to use for new connections */
    FastOS_ThreadPool *_threadPool;

    /** Helper variables for backward compatibility. */
    FastOS_Thread     *_acceptThread;

    /** Tells if the server is running */
    bool         _isRunning;
    bool         _isListening;
    bool         _stopSignalled;
    FastOS_Mutex _runningMutex;

    /** Max number of concurrent threads */
    int _maxThreads;

    /** Base directory for web server content files */
    vespalib::string  _baseDir;

    /** Flag indicating whether up (..) relative paths are
     * allowed for file requests */
    bool _allowUpRelativePath;

    /** Socket listening on desired port for incoming http connections */
    FastOS_ServerSocket _serverSocket;

    /** Decode incoming URLs? */
    bool _decode;

    /** Flag to allow keepAlive connections?, default true */
    bool _keepAlive;

    /** Socket factory for creating streamable Fast_Sockets */
    Fast_HTTPServerSocketFactory _serverSocketFactory;

    /** Buffer sizes for HTTPConnection objects */
    size_t _inBufSize;
    size_t _outBufSize;

    bool IsFileRequest(const string & url);
    void HandleFileRequest(const string & url, Fast_HTTPConnection& conn);
    void PushHtml(const string & url, Fast_HTTPConnection& conn);
    void OutputNotFound(Fast_HTTPConnection& connection,
                        const string *url = NULL,
                        bool addHeaders = true);
    int  Listen(void);


public:
    Fast_HTTPServer(int portNumber,
                    const char* strictBindHostName = NULL, int backlog = 10,
                    bool decode = true,
                    int stackSize=128*1024, int maxThreads=0,
                    int readTimeout = -1 /* No Timeout */);

    virtual ~Fast_HTTPServer(void);

    int getListenPort() { return _serverSocket.GetLocalPort(); }

    size_t GetInBufSize() { return _inBufSize; }
    size_t GetOutBufSize() { return _outBufSize; }
    void SetInBufSize(size_t inBufSize) { _inBufSize = inBufSize; }
    void SetOutBufSize(size_t outBufSize) { _outBufSize = outBufSize; }

    void SetBaseDir(const char *baseDir);
    void SetAllowUpRelativePath(bool allowUpRelativePath = true);
    bool GetAllowUpRelativePath();

    /** Method for turning off keepalive connections, or back on again.
     * Default value is true after the contructor is called.
     * @param keepAlive flag to allow HTTP/1.1 keep alive connections
     * */
    void SetKeepAlive(bool keepAlive = true) { _keepAlive = keepAlive; }
    bool GetKeepAlive() { return _keepAlive; }

    void Run (FastOS_ThreadInterface *thisThread, void *params) override;

    virtual int  Start(void);
    virtual void Stop(void);
    virtual bool StopSignalled(void);

    /**
     * Callback for GET requests. Use it to send back your own set of headers,
     * and body parts.
     * @param  url string that contains the URL given as parameter
     *             to the GET request
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the GET request.
     * @param connection Fast_HTTPConnection reference.
     *                   Call connection.Output() to output data back to
     *                   the client.
     */
    virtual void onGetRequest(const string & url,
                              const string & host,
                              Fast_HTTPConnection& connection);

    /**
     * Callback for writing the body part of a request. If you only
     * overload OnWriteBody(), the default OnGetRequest() will be called first
     * and will write a standard set of headers, then OnWriteBody will
     * be called to write out the rest of the reply.
     * @param  url string that contains the URL given as parameter
     *             to the GET request
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the GET request.
     * @param connection A reference to the Fast_HTTPConnection.
     *                   Call connection.Output() to output data back to
     *                   the client.
     */
    virtual void OnWriteBody (const string & url,
                              const string & host,
                              Fast_HTTPConnection& connection);

    /**
     * Callback for receiving all data from a POST request, and writing
     * back a reply.
     * @param  url string that contains the URL given as parameter
     *             to the POST request. This is used to indicate what
     *             'script' should be used to process the data.
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the POST request.
     * @param contentType string containing the Content-Type: header as
     *                    given by the HTTP client
     * @param contentLength int value containing the Content-Length: header
     *                      as given by the HTTP client
     * @param conn A reference to the Fast_HTTPConnection.
     *             Call connection.Output() to output data back to
     *             the client.
     * @param inputStream Use this Fast_InputStream to read the POST data
     * @param outputStream Use this Fast_OutputStream to output data back
     *                     to the client.
     */
    virtual void OnPostRequest( const string & url,
                               const string & host,
                               const string & contentType,
                               int contentLength,
                               Fast_HTTPConnection& conn,
                               Fast_InputStream& inputStream,
                               Fast_OutputStream& outputStream);

    /**
     * Callback for receiving all data from a PUT request, and writing
     * back a reply.
     * @param  url string that contains the URL given as parameter
     *             to the PUT request. This URL is used to indicate where the
     *             data should be placed.
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the PUT request.
     * @param contentType string containing the Content-Type: header as
     *                    given by the HTTP client
     * @param contentLength int value containing the Content-Length: header
     *                      as given by the HTTP client
     * @param conn A reference to the Fast_HTTPConnection.
     *             Call connection.Output() to output data back to
     *             the client.
     * @param inputStream Use this Fast_InputStream to read the PUT data
     * @param outputStream Use this Fast_OutputStream to output data back
     *                     to the client.
     */
    virtual void OnPutRequest( const string & url,
                              const string & host,
                              const string & contentType,
                              int contentLength,
                              Fast_HTTPConnection& conn,
                              Fast_InputStream& inputStream,
                              Fast_OutputStream& outputStream);

    /**
     * Callback for receiving all headers for a DELETE request, and writing
     * back a reply.
     * @param  url string that contains the URL given as parameter
     *             to the DELETE request. This URL should be deleted from
     *             the system in one way or another.
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the PUT request.
     * @param connection A reference to the Fast_HTTPConnection.
     *                   Call connection.Output() to output data back to
     *                   the client.
     */
    virtual void OnDeleteRequest(const string & url,
                                 const string & host,
                                 Fast_HTTPConnection& connection);

    /**
     * Callback for unknown requests.
     * @param  url string that contains the URL given as parameter
     *             to the DELETE request. This URL should be deleted from
     *             the system in one way or another.
     * @param host string that contains the HTTP/1.1 header "Host:" used
     *             as part of the PUT request.
     * @param connection A reference to the Fast_HTTPConnection.
     *                   Call connection.Output() to output data back to
     *                   the client.
     */
    virtual void OnUnsupportedRequest(const string & url,
                                      const string & host,
                                      Fast_HTTPConnection& connection);

    void AddConnection(Fast_HTTPConnection* connection);
    void RemoveConnection(Fast_HTTPConnection* connection);

};
