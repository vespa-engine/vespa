// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <ostream>
#include <memory>
#include <vespa/vespalib/net/sync_crypto_socket.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/net/socket_spec.h>

/**
 * This class implements a HTTP client that may be used to fetch
 * documents from a HTTP server. It uses the HTTP 1.1 protocol, but in
 * order to keep the external interface simple, it does not support
 * request pipelining.
 **/
class HTTPClient
{
private:
  HTTPClient(const HTTPClient &);
  HTTPClient &operator=(const HTTPClient &);

protected:

  /**
   * abstract superclass of classes used to handle reading of URL
   * content depending on how the content length may be determined.
   **/
  class ReaderInterface
  {
  public:
    ReaderInterface() {}
    virtual ~ReaderInterface() {}

    /**
     * This method is called by the @ref HTTPClient::Read(char *,
     * size_t) method in order to read from the URL in the appropriate
     * way.
     *
     * @return bytes read or -1 on failure.
     * @param client the client object doing the read.
     * @param buf where to store the incoming data.
     * @param len length of buf.
     **/
    virtual ssize_t Read(HTTPClient &client, void *buf, size_t len) = 0;
  };
  friend class HTTPClient::ReaderInterface;

  /**
   * Class used to handle reading of URL content when content length
   * is indicated by the server closing the connection.
   **/
  class ConnCloseReader : public ReaderInterface
  {
  private:
    static ConnCloseReader _instance;
  public:
    ConnCloseReader() {}
    static ReaderInterface *GetInstance() { return &_instance; }
    ssize_t Read(HTTPClient &client, void *buf, size_t len) override;
  };
  friend class HTTPClient::ConnCloseReader;

  /**
   * Class used to handle reading of URL content when content length
   * is given by a Content-Length header value.
   **/
  class ContentLengthReader : public ReaderInterface
  {
  private:
    static ContentLengthReader _instance;
  public:
    ContentLengthReader() {}
    static ReaderInterface *GetInstance() { return &_instance; }
    ssize_t Read(HTTPClient &client, void *buf, size_t len) override;
  };
  friend class HTTPClient::ContentLengthReader;

  /**
   * Class used to handle reading of URL content sent with chunked
   * transfer encoding.
   **/
  class ChunkedReader : public ReaderInterface
  {
  private:
    static ChunkedReader _instance;
  public:
    ChunkedReader() {}
    static ReaderInterface *GetInstance() { return &_instance; }
    ssize_t Read(HTTPClient &client, void *buf, size_t len) override;
  };
  friend class HTTPClient::ChunkedReader;

    vespalib::CryptoEngine::SP     _engine;
    vespalib::SocketAddress        _address;
    vespalib::SyncCryptoSocket::UP _socket;

  const std::string    _hostname;
  int                  _port;
  bool                 _keepAlive;
  bool                 _headerBenchmarkdataCoverage;
  const std::string    _extraHeaders;
  vespalib::SocketSpec _sni_spec;
  std::string          _host_header_value;
  uint64_t             _reuseCount;

  size_t           _bufsize;
  char            *_buf;
  ssize_t          _bufused;
  ssize_t          _bufpos;

  bool             _isOpen;
  unsigned int     _httpVersion;
  unsigned int     _requestStatus;
  int              _totalHitCount;
  bool             _connectionCloseGiven;
  bool             _contentLengthGiven;
  bool             _chunkedEncodingGiven;
  bool             _keepAliveGiven;
  unsigned int     _contentLength;

  unsigned int     _chunkSeq;      // chunk sequence number
  unsigned int     _chunkLeft;     // bytes left of current chunk
  unsigned int     _dataRead;      // total bytes read from URL
  bool             _dataDone;      // all URL content read ?
  ReaderInterface *_reader;        // handles core URL reading


  /**
   * Discard all data currently present in the internal buffer.
   **/
  void ResetBuffer()
  {
    _bufpos  = 0;
    _bufused = 0;
  }

    /**
     * (re)connects the socket to the host/port specified in the
     * constructor. The hostname is not resolved again; the resolve
     * result is cached by the constructor. Also sets tcp nodelay flag
     * and disables lingering. Note to servers: This is a no-nonsense
     * socket that will be closed in your face in very ungraceful
     * ways. Do not expect half-close niceties or tls session
     * termination packets.
     **/
    bool connect_socket();

  /**
   * Fill the internal buffer with data from the url we are connected
   * to.
   *
   * @return the number of bytes put into the buffer or -1 on fail.
   **/
  ssize_t FillBuffer();

  /**
   * Return the next byte from the data stream we are reading.
   *
   * @return next byte from the data stream or -1 on EOF/ERROR
   **/
  int ReadByte()
  {
    if (_bufpos == _bufused)
      FillBuffer();
    return (_bufused > _bufpos) ? _buf[_bufpos++] & 0x0ff : -1;
  }

  /**
   * Connect to the given url.
   *
   * @return success(true)/failure(false)
   * @param url the url you want to connect to
   **/
  bool Connect(const char *url, bool usePost = false,
               const char *content = NULL, int contentLen = 0);

  /**
   * Read the next line of text from the data stream into 'buf'. If
   * the line is longer than ('bufsize' - 1), the first ('bufsize' -
   * 1) bytes will be placed in buf (the rest of the line will be
   * discarded), and the true length of the line will be returned. The
   * string placed in buf will be terminated with a null
   * character. Newline characters will be discarded. A line is
   * terminated by either '\n', "\r\n" or EOF (EOF - connection
   * closed)
   *
   * @return the actual length of the next line, or -1 if no line was read.
   * @param buf where to put the line.
   * @param bufsize the length of buf.
   **/
  ssize_t ReadLine(char *buf, size_t bufsize);

  /**
   * Split a string into parts by inserting null characters into the
   * string and index the parts by putting pointers to them in the
   * argument array given. Only non-empty parts will be indexed in the
   * argument array.
   *
   * @return NULL(complete split)/rest of string(incomplete split)
   * @param input the null-terminated input string.
   * @param argc the number of parts found.
   * @param argv the argument array.
   * @param maxargs the size of 'argv'.
   **/
  static char *SplitString(char *input, int &argc, char **argv, int maxargs);

  /**
   * Read and parse the HTTP Header.
   *
   * @return success(true)/failure(fail)
   **/
  bool ReadHTTPHeader(std::string & headerinfo);

  /**
   * Read and parse a chunk header. Only used with chunked encoding.
   *
   * @return success(true)/failure(fail)
   **/
  bool ReadChunkHeader();

  /**
   * Connect to the given url and read the response HTTP header. Note
   * that this method will fail if the host returns a status code
   * other than 200. This is done in order to make the interface as
   * simple as possible.
   *
   * @return success(true)/failure(false)
   * @param url the url you want to connect to
   * @param usePost whether to use POST in the request
   * @param content if usePost is true, the content to post
   * @param cLen length of content in bytes
   **/
  bool Open(std::string & headerinfo, const char *url, bool usePost = false, const char *content = 0, int cLen = 0);

  /**
   * Close the connection to the url we are currently reading
   * from. Will also close the physical connection if keepAlive is not
   * enabled or if all the url content was not read. This is done
   * because skipping will probably be more expencive than creating a
   * new connection.
   *
   * @return success(true)/failure(false)
   **/
  bool Close();

  /**
   * Read data from the url we are currently connected to. This method
   * should be called repeatedly until it returns 0 in order to
   * completely read the URL content. If @ref Close is called before
   * all URL content is read the physical connection will be closed
   * even if keepAlive is enabled.
   *
   * @return bytes read or -1 on failure.
   * @param buf where to store the incoming data.
   * @param len length of buf.
   **/
  ssize_t Read(void *buf, size_t len);
public:

  /**
   * Create a HTTP client that may be used to fetch documents from the
   * given host.
   *
   * @param hostname the host you want to fetch documents from.
   * @param port the TCP port to use when contacting the host.
   * @param keepAlive flag indicating if keep-alive should be enabled.
   **/
    HTTPClient(vespalib::CryptoEngine::SP engine, const char *hostname, int port, bool keepAlive,
               bool headerBenchmarkdataCoverage, const std::string & extraHeaders="", const std::string &authority = "");

  /**
   * Disconnect from server and free memory.
   **/
  ~HTTPClient();

  /**
   * This method may be used to obtain information about how many
   * times a physical connection has been reused to send an additional
   * HTTP request. Note that connections may only be reused if
   * keep-alive is enabled.
   *
   * @return connection reuse count
   **/
  uint64_t GetReuseCount() const
  {
    return _reuseCount;
  }

  /**
   * Class that provides status about the executed fetch method.
  **/
  class FetchStatus final
  {
  public:
      /**
       *   Create a status for the executed fetch.
       *
       * @param requestStatus The status from the HTTP server.
       * @param totalHitCount The total number of hits.
       * @param resultSize The number of bytes in result.
      **/
      FetchStatus(bool ok, uint32_t requestStatus, int32_t totalHitCount, int32_t resultSize) :
          _ok(ok),
          _requestStatus(requestStatus),
          _totalHitCount(totalHitCount),
          _resultSize(resultSize)
      {}
      /**
       * Query if the operation was successful.
       * @return Status of operation.
      **/
      auto Ok() const { return _ok; }
      /**
         Query HTTP request status.
         @return HTTP request status.
      **/
      auto RequestStatus() const { return _requestStatus; }
      /**
       * Query total hit count. Returns -1 if the total hit count
       * could not be found.
       * @return Total hit count for query.
      **/
      auto TotalHitCount() const { return _totalHitCount; }
      /**
       * Query the number of bytes in the result buffer.
       * @return Number of bytes in result buffer.
       **/
      auto ResultSize() const { return _resultSize; }

  private:
      bool _ok;
      uint32_t _requestStatus;
      int32_t _totalHitCount;
      int32_t _resultSize;
  };

  /**
   * High-level method that may be used to fetch a document in a
   * single method call and save the content to the given file.
   *
   * @return FetchStatus object which can be queried for status.
   * @param url the url to fetch.
   * @param file where to save the fetched document. If this parameter
   *             is NULL, the content will be read and then discarded.
   * @param usePost whether to use POST in the request
   * @param content if usePost is true, the content to post
   * @param contentLen length of content in bytes
   **/
  FetchStatus Fetch(const char *url, std::ostream *file = NULL,
                    bool usePost = false, const char *content = NULL, int contentLen = 0);
};
