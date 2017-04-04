// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "url.h"
#include "httpchunkedinputstream.h"
#include "httpchunkedoutputstream.h"
#include "httpheaderparser.h"
#include "httpserver.h"
#include <vespa/fastlib/io/bufferedinputstream.h>
#include <vespa/fastlib/io/bufferedoutputstream.h>
#include <vespa/fastlib/util/base64.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastos/file.h>

/**
 * Helper class for hiding the details of HTTP entity encodings and
 * persistent connections from Fast_HTTPServer subclasses that read
 * client request entities.  (Headers have already been read by the
 * time this filter is used, and are therefore not handled here.)
 *
 * The filter decodes chunked transfer encoding if used, and returns
 * end of stream at the end of the entity to prevent subclasses from
 * reading past the end (and into the next request on a persistent
 * connection).
 */

class Fast_HTTPPersistentInputFilter : public Fast_FilterInputStream
{
  private:
    // Prevent use of:
    Fast_HTTPPersistentInputFilter(const Fast_HTTPPersistentInputFilter &);
    Fast_HTTPPersistentInputFilter & operator=(const Fast_HTTPPersistentInputFilter &);
  protected:
    Fast_HTTPChunkedInputStream *_chunkedInput;

    bool   _useChunkedInput;
    size_t _remainingBytes;

  public:
    Fast_HTTPPersistentInputFilter(Fast_InputStream &in)
      : Fast_FilterInputStream(in),
        _chunkedInput(NULL),
        _useChunkedInput(false),
        _remainingBytes(0)
    { }

    ~Fast_HTTPPersistentInputFilter() {
      delete _chunkedInput;
    }

    // Methods
    ssize_t Available() override;
    bool    Close() override;
    ssize_t Read(void *sourceBuffer, size_t length) override;
    ssize_t Skip(size_t skipNBytes) override;

    void SetEntityLength(size_t entityLength);
    void SetChunkedEncoding();
};


ssize_t Fast_HTTPPersistentInputFilter::Available()
{
  if (_useChunkedInput) {
    return _chunkedInput->Available();
  } else {
    ssize_t slaveAvailable = _in->Available();
    if (slaveAvailable < 0) {
      return slaveAvailable;
    } else {
      return ((static_cast<size_t>(slaveAvailable) < _remainingBytes))
              ? slaveAvailable : _remainingBytes;
    }
  }
}



bool Fast_HTTPPersistentInputFilter::Close()
{
  // Do nothing.
  return true;
}



ssize_t Fast_HTTPPersistentInputFilter::Read(void *targetBuffer, size_t length)
{
  if (_useChunkedInput) {
    return _chunkedInput->Read(targetBuffer, length);
  } else {
    if (_remainingBytes == 0) {
      return 0;
    } else {
      ssize_t numBytesRead;
      numBytesRead = _in->Read(targetBuffer, (length < _remainingBytes)
                                             ? length : _remainingBytes);
      if (numBytesRead > 0) {
        _remainingBytes -= numBytesRead;
      } else {
        _remainingBytes = 0;
      }
      return numBytesRead;
    }
  }
}



ssize_t Fast_HTTPPersistentInputFilter::Skip(size_t skipNBytes)
{
  if (_useChunkedInput) {
    return _chunkedInput->Skip(skipNBytes);
  } else {
    return _in->Skip((skipNBytes < _remainingBytes) ? skipNBytes : _remainingBytes);
  }
}




void Fast_HTTPPersistentInputFilter::SetEntityLength(size_t entityLength)
{
  _useChunkedInput = false;
  _remainingBytes = entityLength;
}



void Fast_HTTPPersistentInputFilter::SetChunkedEncoding()
{
  _useChunkedInput = true;
  // TODO: If input stream interface is expanded to enable resetting
  // streams, we can reuse a single chunked input stream instance
  // instead of allocating a new each time.
  delete _chunkedInput;
  _chunkedInput = new Fast_HTTPChunkedInputStream(*_in);
}



/**
 * Helper class for converting an entire HTTP server response into a
 * form suitable for persistent connections.
 *
 * The filter strips away headers that would interfere with a
 * persistent connections, adds neccessary headers, and encodes the
 * entity body into the chunked transfer encoding unless the
 * Content-Length header was given in the unfiltered response.
 */

class Fast_HTTPPersistentOutputFilter : public Fast_FilterOutputStream
{
  private:
    // Prevent use of:
    Fast_HTTPPersistentOutputFilter(const Fast_HTTPPersistentOutputFilter &);
    Fast_HTTPPersistentOutputFilter & operator=(const Fast_HTTPPersistentOutputFilter &);
  protected:
    Fast_HTTPChunkedOutputStream *_chunkedOutput;
    Fast_OutputStream            *_entityOutput;

    bool   _inHeaderRegion;     // Currently writing headers?
    bool   _cleanHeader;        // Can current header be written directly?
    bool   _useChunkedOutput;
    char   _line[1024];
    size_t _linePos;

    bool FlushHeader(void);
  public:
    Fast_HTTPPersistentOutputFilter(Fast_OutputStream &out) :
      Fast_FilterOutputStream(out),
      _chunkedOutput(NULL), _entityOutput(NULL),
      _inHeaderRegion(true), _cleanHeader(false), _useChunkedOutput(true),
      _linePos(0)
    { }

    ~Fast_HTTPPersistentOutputFilter() {
      delete _chunkedOutput;
    }

    // Methods
    bool     Close() override;
    ssize_t  Write(const void *sourceBuffer, size_t length) override;
    void     Flush() override;
};



bool Fast_HTTPPersistentOutputFilter::FlushHeader()
{
  assert(_inHeaderRegion);
  size_t i = 0;
  while (i < _linePos) {
    ssize_t numBytesWritten = Fast_FilterOutputStream::Write(_line, _linePos - i);
    if (numBytesWritten >= 0) {
      i += numBytesWritten;
    } else {
      return false;
    }
  }
  _linePos = 0;
  return true;
}



bool Fast_HTTPPersistentOutputFilter::Close()
{
  bool retVal = true;
  if (_inHeaderRegion) {
    FlushHeader();
  } else if (_useChunkedOutput) {
    retVal = _chunkedOutput->Close();
    delete _chunkedOutput;
    _chunkedOutput = NULL;
  } else {
    // Do nothing.  In particular, do not close _entityOutput, as that
    // would close the slave stream.
  }
  Fast_FilterOutputStream::Flush();
  _inHeaderRegion = true;
  _useChunkedOutput = true;
  return retVal;
}



ssize_t Fast_HTTPPersistentOutputFilter::Write(const void *sourceBuffer, size_t length)
{
  if (length == 0) {
    return 0;
  }

  ssize_t numBytesWritten = 0;
  const char *from = static_cast<const char*>(sourceBuffer);

  while (_inHeaderRegion && length > 0) {
    // Read a line at a time from the source buffer and check for
    // problematic headers and the end of the header region.

    bool endOfHeader = false;

    while (length > 0 && _linePos < sizeof(_line)) {
      _line[_linePos++] = *from++;
      length--;
      numBytesWritten++;
      if (_line[_linePos-1] == '\n') {
        endOfHeader = true;
        break;
      }
    }

    // We can safely flush if the header is known to be unproblematic
    // or is at least as long as the line buffer (the headers we are
    // scared of are never that long).
    if (_cleanHeader || _linePos == sizeof(_line)) {
      _cleanHeader = !endOfHeader;
      FlushHeader();
    } else if (endOfHeader) {
      // Check for suspicious headers and end of header region.
      if (_linePos == 1 || (_linePos == 2 && _line[0] == '\r')) {
        // Empty line, end of headers reached.
        if (_useChunkedOutput) {
          //Add chunking header.
          strcpy(_line, "Transfer-Encoding: chunked\r\n\r\n");
          _linePos = strlen(_line);
        }
        FlushHeader();
        sourceBuffer = from;
        _inHeaderRegion = false;
      } else {
        // Terminate header line.
        // (We know that _linepos < sizeof(_line), so this is safe.)
        _line[_linePos] = '\0';

        size_t headerLength = strcspn(_line, " \t\r\n:");
        if (headerLength == 10 && strncasecmp(_line, "connection", 10) == 0) {
          // Don't copy this header, just reset line buffer
          _linePos = 0;
        } else if (headerLength == 14 && strncasecmp(_line, "content-length", 14) == 0) {
          _useChunkedOutput = false;
          FlushHeader();
        } else if (strcasecmp(_line, "Transfer-Encoding: 8bit\n") == 0) {
          // Backward compatibility hack: older versions of
          // Fast_HTTPServer set this invalid header, and there might
          // exist subclasses that still do.  Just discard it.
          _linePos = 0;

        } else {
          FlushHeader();
        }
      }
    }

    if (!_inHeaderRegion) {
      // End of header region was found, set up entity output stream.
      if (_useChunkedOutput) {
        // Free old (if it exists)
        if (_chunkedOutput != NULL)
          delete _chunkedOutput;

        _chunkedOutput = new Fast_HTTPChunkedOutputStream(*_out);
        assert(_chunkedOutput != NULL);
        _entityOutput = _chunkedOutput;
      } else {
        _entityOutput = _out;
      }
    }
  }

  if (length > 0) {
    ssize_t slaveWritten = _entityOutput->Write(sourceBuffer, length);
    if (slaveWritten >= 0) {
      numBytesWritten += slaveWritten;
    } else {
      numBytesWritten = slaveWritten;
    }
  }

  return numBytesWritten;
}



void Fast_HTTPPersistentOutputFilter::Flush(void)
{
  if (_inHeaderRegion) {
    FlushHeader();
    Fast_FilterOutputStream::Flush();
  } else {
    _entityOutput->Flush();
  }
}

Fast_HTTPServer::Fast_HTTPServer(int portNumber,
                                 const char* strictBindHostName,
                                 int backlog, bool decode,
                                 int stackSize, int maxThreads,
				 int clientReadTimeout /* = -1 no timeout */)
: _connections(10),
  _connectionCond(),
  _threadPool(NULL),
  _acceptThread(NULL),
  _isRunning(false),
  _isListening(false),
  _stopSignalled(false),
  _runningMutex(),
  _maxThreads(maxThreads),
  _baseDir(),
  _allowUpRelativePath(false),
  _serverSocket(portNumber, backlog,
		&_serverSocketFactory,
		strictBindHostName),
  _decode(decode),
  _keepAlive(true),
  _serverSocketFactory(clientReadTimeout),
  _inBufSize(FASTLIB_HTTPSERVER_INBUFSIZE),
  _outBufSize(FASTLIB_HTTPSERVER_OUTBUFSIZE)
{
  _threadPool = new FastOS_ThreadPool(stackSize, maxThreads);
}


int Fast_HTTPServer::Start(void)
{
  int retCode = FASTLIB_SUCCESS;

  _runningMutex.Lock();
  if (!_isRunning) {
    // Try listening
    retCode = Listen();

    // Start worker thread
    if (retCode == FASTLIB_SUCCESS) {
      _acceptThread = static_cast<FastOS_Thread *>(_threadPool->NewThread(this));
      if (_acceptThread == NULL) {
        retCode = FASTLIB_HTTPSERVER_NEWTHREADFAILED;
      }
    }
  } else {
    retCode = FASTLIB_HTTPSERVER_ALREADYSTARTED;
  }
  _runningMutex.Unlock();

  return retCode;
}


void
Fast_HTTPServer::Stop(void) {
  _runningMutex.Lock();
  _stopSignalled = true;
  if (_acceptThread) {
    _acceptThread->SetBreakFlag();
  }
  _runningMutex.Unlock();
  if (_acceptThread) {
    _acceptThread->Join();
  }
}



bool Fast_HTTPServer::StopSignalled(void)
{
  bool retVal;
  _runningMutex.Lock();
  retVal = _stopSignalled;
  _runningMutex.Unlock();
  return retVal;
}



Fast_HTTPServer::~Fast_HTTPServer(void)
{
  Stop();

  _connectionCond.Lock();

  for (Fast_BagIterator<Fast_HTTPConnection*> i(_connections); !i.End(); i.Next())
    i.GetCurrent()->Interrupt();

  while (_connections.NumberOfElements() > 0)
    _connectionCond.Wait();

  _connectionCond.Unlock();
  delete _threadPool;
}



int Fast_HTTPServer::Listen(void)
{
  int retCode = FASTLIB_SUCCESS;

  if(!_isListening) {
    if (_serverSocket.Listen()) {
      _isListening = true;
    } else {
      std::string errorMsg = FastOS_Socket::getErrorString(FastOS_Socket::GetLastError());
      retCode = FASTLIB_HTTPSERVER_BADLISTEN;
    }
  }

  return retCode;
}



void Fast_HTTPServer::Run(FastOS_ThreadInterface *thisThread, void *params)
{
  (void) thisThread;
  (void) params;
  Fast_Socket *mySocket;


  _runningMutex.Lock();
  _isRunning = true;
  _stopSignalled = false;
  _runningMutex.Unlock();

  if (Listen() == FASTLIB_SUCCESS) {
    FastOS_SocketEvent socketEvent;
    if (_serverSocket.SetSocketEvent(&socketEvent)) {
      _serverSocket.EnableReadEvent(true);
      while (!thisThread->GetBreakFlag()) {
        bool waitError;
        if (!socketEvent.Wait(waitError, 500)) {
          if (waitError) {
//            fprintf(stderr, "HTTPServer: ERROR: Wait on server socket failed.\n");
          }
          continue;
        }

        // If number of threads is limited, ensure that the limit is
        // not breached.  Starvation is impossible, since threads are
        // only allocated here.
        if (_maxThreads != 0) {
          while (_threadPool->GetNumActiveThreads() == _maxThreads) {
            FastOS_Thread::Sleep(50);
          }
        }

        mySocket = static_cast<Fast_Socket *>(_serverSocket.Accept());

        if (mySocket != NULL) {
          mySocket->SetNoDelay(true);
          Fast_HTTPConnection *connectionHandler =
            new Fast_HTTPConnection(mySocket, // Takes ownership
                                    _decode,
                                    _inBufSize,
                                    _outBufSize);

          if (_keepAlive == false) {
            connectionHandler->SetKeepAlive(false);
          }

          auto* thread = _threadPool->NewThread(connectionHandler, this);
          if (thread == nullptr) {
              // Thread pool has been shut down; cannot service request.
              delete connectionHandler;
          }
        } else {
          FastOS_Thread::Sleep(1000);
          // fprintf(stderr, "HTTPServer: ERROR: Accept did not return a valid socket. Terminating\n");
        }
      }
      _serverSocket.EnableReadEvent(false);
    } else {
//      fprintf(stderr, "HTTPServer: ERROR: Unable to set socket event handler.\n");
    }
    _serverSocket.SetSocketEvent(NULL);
  }

  _runningMutex.Lock();
  _isRunning = false;
  _runningMutex.Unlock();
}

void Fast_HTTPConnection::Run(FastOS_ThreadInterface *thisThread, void *params)
{
  (void) thisThread;
  vespalib::string fLine, contentType;
  vespalib::string url, host;

  enum request_type { HTTPSERVER_UNSUPPORTEDREQUEST,
                      HTTPSERVER_GETREQUEST,
                      HTTPSERVER_POSTREQUEST,
                      HTTPSERVER_PUTREQUEST,
                      HTTPSERVER_DELETEREQUEST
                    } requestType = HTTPSERVER_UNSUPPORTEDREQUEST;


  _server = static_cast<Fast_HTTPServer *>(params);
  _server->AddConnection(this);

  Fast_InputStream                *originalInput    = _input;
  Fast_HTTPPersistentInputFilter  *persistentInput  = NULL;
  Fast_OutputStream               *originalOutput   = _output;
  Fast_HTTPPersistentOutputFilter *persistentOutput = NULL;

  Fast_HTTPHeaderParser headerParser(static_cast<Fast_BufferedInputStream &>(*_input));

  do {

    bool printContinue = false; // RFC2616, 8.2.3
    bool chunkedInput  = false;
    int  contentLength = 0;

    // Get request line.

    const char *requestCStr, *urlCStr;
    int versionMajor, versionMinor;

    if (! headerParser.ReadRequestLine(requestCStr, urlCStr, versionMajor, versionMinor))
      break;

    if (strcmp(requestCStr, "POST") == 0) {
      requestType = HTTPSERVER_POSTREQUEST;
    } else if (strcmp(requestCStr, "GET") == 0) {
      requestType = HTTPSERVER_GETREQUEST;
    } else if (strcmp(requestCStr, "PUT") == 0) {
      requestType = HTTPSERVER_PUTREQUEST;
    } else if (strcmp(requestCStr, "DELETE") == 0) {
      requestType = HTTPSERVER_DELETEREQUEST;
    } else {
      requestType = HTTPSERVER_UNSUPPORTEDREQUEST;
    }

    if (_decode) {
      vespalib::string encodedURL(urlCStr);
      int bufferLength = encodedURL.length()+10;
      char *decodedURL = new char[bufferLength];
      Fast_URL urlCodec;
      urlCodec.decode(encodedURL.c_str(), decodedURL, bufferLength);
      urlCodec.DecodeQueryString(decodedURL);
      url = decodedURL;
      delete [] decodedURL;
    } else {
      url = urlCStr;
    }

    if (versionMajor != 1) {
      requestType = HTTPSERVER_UNSUPPORTEDREQUEST;
    }
    if (versionMinor < 1) {
      _keepAlive = false;   // No keepAlive for HTTP/1.0 and HTTP/0.9
    }
    _versionMajor = versionMajor;
    _versionMinor = versionMinor;
    _httpVersion = vespalib::make_string("HTTP/%d.%d", _versionMajor, _versionMinor);

    const char *headerName, *headerValue;
    while(headerParser.ReadHeader(headerName, headerValue)) {
      if (strcasecmp(headerName, "content-length") == 0) {
        contentLength = atoi(headerValue);
        //printf("Found content length: %i\n", contentLength);
      }

      if (strcasecmp(headerName, "content-type") == 0) {
        contentType = headerValue;
      }

      if (strcasecmp(headerName, "connection") == 0) {
        if (strcasecmp(headerValue, "close") == 0) {
          _keepAlive = false;
        }
      }

      if (strcasecmp(headerName, "host") == 0) {
        host = headerValue;
      }

      if (strcasecmp(headerName, "cookie") == 0) {
        _cookies = headerValue;
      }

      if (strcasecmp(headerName, "expect") == 0) {
        if (strcasecmp(headerValue, "100-continue") == 0) {
          printContinue = true;
        } else {
          // TODO: Return reponse code 417 instead of 505.
          requestType = HTTPSERVER_UNSUPPORTEDREQUEST;
        }
      }

      if (strcasecmp(headerName, "transfer-encoding") == 0) {
        if (strcasecmp(headerValue, "chunked") == 0) {
          chunkedInput = true;
        } else {
          // Only chunked is supported so far.
          requestType = HTTPSERVER_UNSUPPORTEDREQUEST;
        }
      }

      if (strcasecmp(headerName, "authorization") == 0) {
        if (strncasecmp(headerValue, "basic",5) == 0) {
          char *auth = new char[strlen(headerValue)-4];
          int len = Fast_Base64::Decode(headerValue+5,strlen(headerValue)-5,auth);
          if(len>=0) auth[len]=0;
          char *pass=strchr(auth,':');
          if(pass!=NULL){
            *pass++=0;
            _auth_user=auth;
            _auth_pass=pass;
          }
          delete[] auth;
        }
      }
    }


    if (_keepAlive) {
      // Set up filters for persistent input and output.

      if (persistentInput == NULL) {
        persistentInput = new Fast_HTTPPersistentInputFilter(*originalInput);
        assert(persistentInput != NULL);
      }
      if (chunkedInput) {
        // If chunked input is specified in headers, ignore content-length
        // and use chunked encoding, according to RFC.
        persistentInput->SetChunkedEncoding();
      } else {
        // Works as intended if content length == 0 (i.e. no entity body).
        persistentInput->SetEntityLength(contentLength);
      }
      _input = persistentInput;

      if (persistentOutput == NULL) {
        persistentOutput = new Fast_HTTPPersistentOutputFilter(*originalOutput);
        assert(persistentOutput != NULL);
      }
      _output = persistentOutput;
    } else {
      _input  = originalInput;
      _output = originalOutput;
    }

    if (printContinue) {
      Output(_httpVersion.c_str());
      Output(" 100 Continue\r\n");
    }


    switch(requestType) {
      case HTTPSERVER_GETREQUEST:
      {
        _server->onGetRequest(url, host, *this);
        break;
      }

      case HTTPSERVER_POSTREQUEST:
      {
        _server->OnPostRequest(url, host, contentType, contentLength,
                               *this, *_input, *_output);
        break;
      }

      case HTTPSERVER_PUTREQUEST:
      {
        _server->OnPutRequest(url, host, contentType, contentLength,
                              *this, *_input, *_output);
        break;
      }

      case HTTPSERVER_DELETEREQUEST:
      {
        _server->OnDeleteRequest(url, host, *this);
        break;
      }

      case HTTPSERVER_UNSUPPORTEDREQUEST:
      default:
      {
        _server->OnUnsupportedRequest(url, host, *this);
        _keepAlive = false;
        break;
      }
    }

    // Ensure all of request entity body is read if connection is
    // persistent.
    if (_keepAlive) {
      ssize_t numBytesRead;
      char buffer[1024];
      while ((numBytesRead = _input->Read(buffer, sizeof(buffer))) > 0) {
        // Keep reading
      }
      _keepAlive = (numBytesRead == 0);
      _input = originalInput;
    }

    _output->Flush();
    _output->Close();

  } while (_keepAlive && !_server->StopSignalled());

  // Close connection
  _socket->Close();

  _input = originalInput; // To be deleted by destructor
  delete persistentInput;

  _output = originalOutput; // To be deleted by destructor
  delete persistentOutput;

  delete this;
}



void Fast_HTTPServer::onGetRequest(const string & tmpurl, const string & host, Fast_HTTPConnection& conn)
{
  // Trim leading / if it exists
  string url(tmpurl);
  if (url.length()>0) {
    if (url[0]=='/') {
        url = url.substr(1);
    }
  }

  if (IsFileRequest(url)) {
    HandleFileRequest(url, conn);
  } else {
    // Output html content header
    conn.Output(conn.GetHTTPVersion().c_str());
    conn.Output(" 200 OK\r\n");
    conn.Output("Server: FAST-HTTP-Server/1.0 (Fast Generic HTTP server)\r\n");
    if (conn.GetKeepAlive() == false)
      conn.Output("Connection: close\r\n");
    conn.Output("Content-Type: text/html\r\n\r\n");

    // Output user specific body
    OnWriteBody(url, host, conn);
  }
}



void Fast_HTTPServer::OnPostRequest(const string & url,
                                    const string & host,
                                    const string & contentType,
                                    int contentLength,
                                    Fast_HTTPConnection& conn,
                                    Fast_InputStream& inputStream,
                                    Fast_OutputStream& outputStream)
{

  // Ignore all parameters
  (void) url;
  (void) host;
  (void) contentType;
  (void) contentLength;
  (void) inputStream;
  (void) outputStream;
  (void) contentLength;

  // Output html content header
  conn.Output(conn.GetHTTPVersion().c_str());
  conn.Output(" 200 OK\r\n");
  conn.Output("Server: FAST-HTTP-Server/1.0 (Fast Generic HTTP server)\r\n");
  if (conn.GetKeepAlive() == false)
    conn.Output("Connection: close\r\n");
  conn.Output("Content-Type: text/html\r\n\r\n");

  // Body output example
  conn.Output("<html> \r\n");
  conn.Output("<head> \r\n");
  conn.Output("<title>Test title</title> \r\n");
  conn.Output("</head> \r\n");
  conn.Output("<body> \r\n");
  conn.Output("<p>Implement the virtual function 'OnPostRequest()' to change this page!</p>\r\n");
  conn.Output("</body> \r\n");
  conn.Output("</html> \r\n\r\n");

}



void Fast_HTTPServer::OnPutRequest(const string & url,
                                   const string & host,
                                   const string & contentType,
                                   int contentLength,
                                   Fast_HTTPConnection& conn,
                                   Fast_InputStream& inputStream,
                                   Fast_OutputStream& outputStream)
{

  // Ignore parameters not passed on to OnUnsupportedRequest()
  (void) contentType;
  (void) contentLength;
  (void) inputStream;
  (void) outputStream;
  (void) contentLength;

  OnUnsupportedRequest(url, host, conn);
}



void Fast_HTTPServer::OnDeleteRequest(const string & url, const string & host, Fast_HTTPConnection& conn)
{
  OnUnsupportedRequest(url, host, conn);
}



void Fast_HTTPServer::OnUnsupportedRequest(const string & url, const string & host, Fast_HTTPConnection& conn)
{
  (void) url;
  (void) host;

  conn.Output(conn.GetHTTPVersion().c_str());
  conn.Output(" 501 Not Implemented\r\n\r\n");
}



void Fast_HTTPServer::OnWriteBody(const string & url, const string & host, Fast_HTTPConnection& conn)
{
   (void) url;
   (void) host;

   // Body output example
   conn.Output("<html> \r\n");
   conn.Output("<head> \r\n");
   conn.Output("<title>Test title</title> \r\n");
   conn.Output("</head> \r\n");
   conn.Output("<body> \r\n");
   conn.Output("<p>Implement the virtual function 'OnWriteBody()' to change this page!</p>\r\n");
   conn.Output("</body> \r\n");
   conn.Output("</html> \r\n\r\n");
}


void Fast_HTTPConnection::OutputData(const void *data, size_t len)
{
  const char *dataPosition = static_cast<const char *>(data);

  while (len > 0) {
    ssize_t numBytesWritten = _output->Write(dataPosition, len);
    if (numBytesWritten >= 0) {
      dataPosition += numBytesWritten;
      len          -= numBytesWritten;
    } else {
      break;
    }
  }
}

void Fast_HTTPConnection::Output(const char *text)
{
   size_t length = strlen(text);

   while (length > 0) {
     ssize_t numBytesWritten = _output->Write(text, length);
     if (numBytesWritten >= 0) {
       text   += numBytesWritten;
       length -= numBytesWritten;
     } else {
       break;
     }
   }
}



bool Fast_HTTPServer::IsFileRequest(const string & url)
{
   bool retVal = false;

   // Check if the request is for a file (stupid test now)
   if (url.length() > 4) {
      if (url[url.length()-4]=='.') retVal = true;

      if (url.length() > 5) {
         if (url[url.length()-5]=='.') retVal = true;

         if (url.length() > 6) {
            if (url[url.length()-6]=='.') retVal = true;
         }
      }
   }

   return retVal;
}



void Fast_HTTPServer::PushHtml(const string & url, Fast_HTTPConnection& conn)
{
   // Add base dir to relative path and file name
   string fileName = _baseDir + url;

   FastOS_File file;

   if (file.OpenReadOnly(fileName.c_str())) {
      conn.OutputFile(&file);
   } else {
      OutputNotFound(conn, &url, false);
   }

   file.Close();
}



void Fast_HTTPServer::HandleFileRequest(const string & url, Fast_HTTPConnection& conn)
{
   string status403;
   vespalib::string upRelative(FastOS_File::GetPathSeparator());
   upRelative += "..";
   upRelative += FastOS_File::GetPathSeparator();
   vespalib::string upRelative2("/../");

   bool isUpRelative =
     _allowUpRelativePath == false &&
     (vespalib::contains(url, upRelative) ||
      vespalib::contains(url, upRelative2) ||
      vespalib::starts_with(url, "../") ||
      vespalib::starts_with(url, "..\\"));

   // Security policy:
   //   Do not allow file requests if _baseDir is not set.
   //   Do not allow UpRelative paths if not explicitly enabled with
   //     SetAllowUpRelativePath(true);
   if (_baseDir.length() == 0 || isUpRelative ) {
     conn.Output(conn.GetHTTPVersion().c_str());
     conn.Output(" 403 FORBIDDEN\r\n");
     conn.Output("Server: FAST-HTTP-Server/1.0 (Fast Generic HTTP server)\r\n");
     conn.Output("Content-Type: text/html\r\n");
     conn.Output("Connection: close\r\n");

     status403.append("<html> \r\n");
     status403.append("<head> \r\n");
     status403.append("<title>Error 403</title> \r\n");
     status403.append("</head> \r\n");
     status403.append("<body> \r\n");
     status403.append("<h2>HTTP Error 403</h2>\r\n");
     status403.append("<p><strong>403 Forbidden</strong></p>\r\n");
     status403.append("</body></html>\r\n\r\n");

     conn.Output(vespalib::make_string("Content-Length: %ld\r\n\r\n", status403.length()).c_str());
     conn.Output(status403.c_str());
     return;
   }

   // Add base dir to relative path and file name
   string fileName = _baseDir + url;

   FastOS_File file;

   if (file.OpenReadOnly(fileName.c_str())) {
      bool contentTypeKnown = false;

      conn.Output(conn.GetHTTPVersion().c_str());
      conn.Output(" 200 OK\r\n");
      conn.Output("Server: FAST-HTTP-Server/1.0 (Fast Generic HTTP server)\r\n");
      conn.Output("Content-Length: ");
      conn.Output(vespalib::make_string("%ld", file.GetSize()).c_str());
      conn.Output("\r\n");

      if (conn.GetKeepAlive() == false)
        conn.Output("Connection: close\r\n");

      if (ends_with(url, ".gif")) {
         conn.Output("Content-Type: image/gif\r\n");
         contentTypeKnown = true;
      }

      if (ends_with(url, ".html") || ends_with(url, ".htm")) {
         conn.Output("Content-Type: text/html\r\n");
         contentTypeKnown = true;
      }

      if (ends_with(url, ".jpeg") || ends_with(url, ".jpg")) {
         conn.Output("Content-Type: image/jpeg\r\n");
         contentTypeKnown = true;
      }

      if (!contentTypeKnown) {
         conn.Output("Content-Type: application/octet-stream\r\n");
      }

      conn.Output("\r\n");
      conn.OutputFile(&file);

   } else {
      OutputNotFound(conn, &url);
   }

   file.Close();
}



void Fast_HTTPServer::SetBaseDir(const char *baseDir)
{
  _runningMutex.Lock();
  if (!_isRunning) {
    _baseDir = baseDir;

    if (_baseDir.length() > 0) {
      // Add '/' if it was not supplied
      if (_baseDir[_baseDir.length()-1] != '/') {
        _baseDir.append("/");
      }
    }
  } else {
    fprintf(stderr, "HTTPServer: Tried to set base dir after the server had been started. Request denied.\r\n");
  }
  _runningMutex.Unlock();
}

void
Fast_HTTPServer::SetAllowUpRelativePath(bool allowUpRelativePath) {
  _allowUpRelativePath = allowUpRelativePath;
}

bool
Fast_HTTPServer::GetAllowUpRelativePath() {
  return _allowUpRelativePath;
}


Fast_HTTPConnection::Fast_HTTPConnection(Fast_Socket *sock,
                                         bool decode,
                                         size_t inBufSize,
                                         size_t outBufSize)
  : _decode(decode),
    _socket(sock),
    _input(NULL),
    _output(NULL),
    _server(NULL),
    _chunkedInput(false),
    _chunkedOutput(false),
    _keepAlive(true),  // Per default, keepalive is true for HTTP/1.1
    _auth_user(),
    _auth_pass(),
    _versionMajor(1),	 // Default HTTP version is 1.1
    _versionMinor(1),
    _httpVersion(),
    _cookies()
{
  _input  = new Fast_BufferedInputStream(*_socket, inBufSize);
  _output = new Fast_BufferedOutputStream(*_socket, outBufSize);

  _httpVersion = vespalib::make_string("HTTP/%d.%d", _versionMajor, _versionMinor);
}



Fast_HTTPConnection::~Fast_HTTPConnection(void)
{
  if (_server) {
    _server->RemoveConnection(this);
  }

  delete _input;
  delete _output;
  delete _socket;
}



void Fast_HTTPConnection::OutputFile(FastOS_FileInterface *file)
{
   const int bufferSize = 2048;
   char buffer[bufferSize];
   int bytesRead;

   file->SetPosition (0); // Try to read from start of file

   while ((bytesRead=file->Read(buffer, 2048)) > 0) {
      ssize_t bytesLeft = bytesRead;
      char * bufferPos = buffer;

      while (bytesLeft > 0) {
         ssize_t numBytesWritten = _output->Write(bufferPos, bytesLeft);
         if (numBytesWritten >= 0) {
            bufferPos += numBytesWritten;
            bytesLeft -= numBytesWritten;
         } else {
            return;
         }
      }
   }
}



void Fast_HTTPServer::OutputNotFound(Fast_HTTPConnection& conn,
                                     const string *url /* = NULL */,
                                     bool addHeaders /* = true */)
{
  string status404;

  if (addHeaders) {
   conn.Output(conn.GetHTTPVersion().c_str());
   conn.Output(" 404 Not Found\r\n");
   conn.Output("Server: FAST-HTTP-Server/1.0 (Fast Generic HTTP server)\r\n");
   conn.Output("Content-Type: text/html\r\n");

   status404.append("<html> \r\n");
   status404.append("<head> \r\n");
   status404.append("<title>Error 404</title> \r\n");
   status404.append("<meta name=\"robots\" content=\"noindex\">\r\n");
   status404.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=iso-8859-1\">\r\n");
   status404.append("</head> \r\n");
   status404.append("<body> \r\n");

   status404.append("<h2>HTTP Error 404</h2>\r\n");
   status404.append("<p><strong>404 Not Found</strong></p>\r\n");

  }

  if (url == NULL)
    status404.append("<p>The Web server cannot find the file or script you asked for.</p>\r\n");
  else
    status404.append(vespalib::make_string("<p>The Web server cannot find %s.</p>\r\n", url->c_str()));

  status404.append("<p>Please check the URL to ensure that the path is correct.</p>\r\n");
  status404.append("<p>Contact the server's administrator if this problem persists.</p>\r\n");

  if (addHeaders) {
    status404.append("</body> \r\n");
    status404.append("</html> \r\n\r\n");

    conn.Output(vespalib::make_string("Content-Length: %ld\r\n\r\n", status404.length()).c_str());
  }

  conn.Output(status404.c_str());
}

void
Fast_HTTPServer::AddConnection(Fast_HTTPConnection* connection)
{
  _connectionCond.Lock();
  _connections.Insert(connection);
  _connectionCond.Unlock();
}

void
Fast_HTTPServer::RemoveConnection(Fast_HTTPConnection* connection)
{
  _connectionCond.Lock();
  _connections.RemoveElement(connection);
  _connectionCond.Signal();
  _connectionCond.Unlock();
}

void
Fast_HTTPConnection::Interrupt()
{
  _socket->Interrupt();
}
