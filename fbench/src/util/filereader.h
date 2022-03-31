// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <fstream>
#include <memory>
#include <vector>

/**
 * This is a wrapper class for std::ifstream that may be used when
 * reading line based text files. An internal buffer is used to
 * improve performance.
 **/
class FileReader
{
private:
  std::unique_ptr<std::ifstream> _backing;
  std::istream      *_file;
  int                _bufsize;
  std::vector<char>  _buf;
  uint64_t           _lastReadPos;
  uint64_t           _nextReadPos;
  int                _bufused;
  int                _bufpos;

  /**
   * Fill the internal buffer with data from the currently open file.
   **/
  void FillBuffer();

  FileReader(const FileReader &);
  FileReader &operator=(const FileReader &);

public:

  /**
   * Creates a  used for disk-access. An
   * internal buffer of 5120 bytes is also created.
   **/
  FileReader();

  /**
   * Frees memory used by the underlying file and the internal buffer.
   **/
  ~FileReader();

  /**
   * Read a single byte from the currently open input file. You should
   * call @ref Open before calling this method. The internal buffer is
   * used to reduce the number of reads performed on the underlying
   * file.
   *
   * @return the read byte or -1 if EOF was reached or an error occurred.
   **/
  int ReadByte()
  {
    if(_bufpos == _bufused)
      FillBuffer();
    return (_bufused > _bufpos) ? (_buf[_bufpos++] & 0x0ff) : -1;
  }

  /**
   * Open a file for reading.
   *
   * @return success(true)/failure(false)
   * @param filename the name of the file to open.
   **/
  bool Open(const char *filename);

  /**
   * Open the standard input for reading.
   *
   * @return success(true)/failure(false)
   **/
  bool OpenStdin();

  /**
   * Reset the file pointer and flush the internal buffer. The next
   * read operation will apply to the beginning of the file.
   *
   * @return success(true)/failure(false)
   **/
  bool Reset();

  /**
   * Works like Reset(), but sets the file pointer to 'pos
   **/
  bool SetFilePos(int64_t pos);

  /**
   * @return size of file in bytes
   **/
  int64_t GetFileSize();

  /**
   * @return current position in file
   **/
  uint64_t GetFilePos() const { return _lastReadPos + _bufpos; }

  /**
   * @return offset to start of next line from pos
   **/
  uint64_t FindNextLine(int64_t pos);

  /**
   * Read the next line of text from the the currently open file into
   * 'buf'. If the line is longer than ('bufsize' - 1), the first
   * ('bufsize' - 1) bytes will be placed in 'buf' and the true length
   * of the line will be returned. The string placed in 'buf' will be
   * terminated with a null character. Newline characters will be
   * discarded. A line is terminated by either '\n', '\r', "\r\n",
   * "\n\r" or EOF. This method uses @ref ReadByte to read single
   * bytes from the file.
   *
   * @return the actual length of the next line, or -1 if no line was read.
   * @param buf where to put the line.
   * @param bufsize the length of buf.
   **/
  ssize_t ReadLine(char *buf, size_t bufsize);

  /**
   * Close the file.
   **/
  void Close();
};

