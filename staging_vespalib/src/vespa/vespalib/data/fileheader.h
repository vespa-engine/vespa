// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exception.h>
#include <map>

class FastOS_FileInterface;

namespace vespalib {

class DataBuffer;
class asciistream;

/**
 * This exception can be thrown when serializing or deserializing header content.
 */
VESPA_DEFINE_EXCEPTION(IllegalHeaderException, vespalib::Exception);

/**
 * This class implements a collection of GenericHeader::Tag objects that can be set and retrieved by name. The
 * interfaces GenericHeader::IDataReader or GenericHeader::IDataWriter define the api to allow deserialization
 * and serialization of an instance of this class to any underlying data buffer.
 */
class GenericHeader {
public:
    static const uint32_t MAGIC;
    static const uint32_t VERSION;

    /**
     * This class implements a immutable named value of a specified type. This type can be any of
     * Tag::Type. There is no enforcement of type, so using asInteger() on a TYPE_STRING instance will simple
     * return the default integer value.
     */
    class Tag {
    public:
        enum Type {
            TYPE_EMPTY   = 'e',
            TYPE_FLOAT   = 'f',
            TYPE_INTEGER = 'i',
            TYPE_STRING  = 's'
        };

    private:
        Type             _type;
        vespalib::string _name;
        double           _fVal;
        int64_t          _iVal;
        vespalib::string _sVal;

    public:
        Tag();
        Tag(const Tag &);
        Tag & operator=(const Tag &);
        Tag(const vespalib::string &name, float val);
        Tag(const vespalib::string &name, double val);
        Tag(const vespalib::string &name, int8_t val);
        Tag(const vespalib::string &name, uint8_t val);
        Tag(const vespalib::string &name, int16_t val);
        Tag(const vespalib::string &name, uint16_t val);
        Tag(const vespalib::string &name, int32_t val);
        Tag(const vespalib::string &name, uint32_t val);
        Tag(const vespalib::string &name, int64_t val);
        Tag(const vespalib::string &name, uint64_t val);
        Tag(const vespalib::string &name, bool val);
        Tag(const vespalib::string &name, const char *val);
        Tag(const vespalib::string &name, const vespalib::string &val);
        ~Tag();

        size_t           read(DataBuffer &buf);
        size_t           write(DataBuffer &buf) const;
        size_t           getSize()   const;

        bool               isEmpty()   const { return _type == TYPE_EMPTY; }
        Type               getType()   const { return _type; }
        const vespalib::string &getName()   const { return _name; }

        double             asFloat()   const { return _fVal; }
        int64_t            asInteger() const { return _iVal; }
        const vespalib::string &asString()  const { return _sVal; }
        bool               asBool()    const { return _iVal != 0; }
    };

    /**
     * This class defines the interface used by GenericHeader to deserialize content. It has implementation
     * GenericHeader::BufferReader for reading from a buffer, and FileHeader::FileReader for reading from a
     * file.
     */
    class IDataReader {
    public:
        virtual ~IDataReader() { /* empty */ }
        virtual size_t getData(char *buf, size_t len) = 0;
    };

    /**
     * This class defines the interface used by GenericHeader to serialize content. It has implementation
     * GenericHeader::BufferWriter for reading from a buffer, and FileHeader::FileWriter for reading from a
     * file.
     */
    class IDataWriter {
    public:
        virtual ~IDataWriter() { /* empty */ }
        virtual size_t putData(const char *buf, size_t len) = 0;
    };

    /**
     * Implements the GenericHeader::IDataReader interface for deserializing header content from a
     * DataBuffer instance.
     */
    class BufferReader : public IDataReader {
    private:
        DataBuffer &_buf;

    public:
        BufferReader(DataBuffer &buf);
        size_t getData(char *buf, size_t len) override;
    };

    /**
     * Implements the GenericHeader::IDataWriter interface for serializing header content to a
     * DataBuffer instance.
     */
    class BufferWriter : public IDataWriter {
    private:
        DataBuffer &_buf;

    public:
        BufferWriter(DataBuffer &buf);
        size_t putData(const char *buf, size_t len) override;
    };

    class MMapReader : public IDataReader
    {
        const char *_buf;
        size_t _sz;

    public:
        MMapReader(const char *buf, size_t sz);

        size_t getData(char *buf, size_t len) override;
    };

private:
    static const Tag EMPTY;

    typedef std::map<vespalib::string, Tag> TagMap;
    TagMap _tags;

public:
    /**
     * Constructs a new instance of this class.
     */
    GenericHeader();

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~GenericHeader();

    /**
     * Returns the number of tags contained in this header.
     *
     * @return The size of the tag map.
     */
    size_t getNumTags() const { return _tags.size(); }

    /**
     * Returns the tag at the given index. This can be used along with getNumTags() to iterate over all the
     * tags in a header. This is not an efficient way of accessing tags, since the underlying map does not
     * support random access. If you are interested in a specific tag, use getTag() by name instead.
     *
     * @param idx The index of the tag to return.
     * @return The tag at the given index.
     */
    const Tag &getTag(size_t idx) const;

    /**
     * Returns a reference to the named tag. If hasTag() returned false for the same key, this method returns
     * a tag that is of type Tag::TYPE_EMPTY and returns true for Tag::isEmpty().
     *
     * @param key The name of the tag to return.
     * @return A reference to the named tag.
     */
    const Tag &getTag(const vespalib::string &key) const;

    /**
     * Returns whether or not there exists a tag with the given name.
     *
     * @param key The name of the tag to look for.
     * @return True if the named tag exists.
     */
    bool hasTag(const vespalib::string &key) const;

    /**
     * Adds the given tag to this header. If a tag already exists with the given name, this method replaces
     * that tag and returns false.
     *
     * @param tag The tag to add.
     * @return True if no tag was overwritten.
     */
    bool putTag(const Tag &tag);

    /**
     * Removes a named tag. If no tag exists with the given name, this method returns false.
     *
     * @param key The name of the tag to remove.
     * @return True if a tag was removed.
     */
    bool removeTag(const vespalib::string &key);

    /**
     * Returns whether or not this header contains any data. The current implementation only checks for tags,
     * but as this class evolves it might include other data as well.
     *
     * @return True if this header has no data.
     */
    bool isEmpty() const { return _tags.empty(); }

    static size_t
    getMinSize(void);

    /**
     * Returns the number of bytes required to hold the content of this when calling write().
     *
     * @return The number of bytes.
     */
    virtual size_t getSize() const;

    static size_t
    readSize(IDataReader &reader);

    /**
     * Deserializes header content from the given provider into this.
     *
     * @param reader The provider to read from.
     * @return The number of bytes read.
     */
    size_t read(IDataReader &reader);

    /**
     * Serializes the content of this into the given consumer.
     *
     * @param writer The consumer to write to.
     * @return The number of bytes written.
     */
    size_t write(IDataWriter &writer) const;
};

/**
 * This class adds file-specific functionality to the GenericHeader class. This includes alignment of size to
 * some set number of bytes, as well as the ability to update a header in-place (see FileHeader::rewrite()).
 */
class FileHeader : public GenericHeader {
public:
    /**
     * Implements the GenericHeader::IDataReader interface for deserializing header content from a
     * FastOS_FileInterface instance.
     */
    class FileReader : public IDataReader {
    private:
        FastOS_FileInterface &_file;

    public:
        FileReader(FastOS_FileInterface &file);
        size_t getData(char *buf, size_t len) override;
    };

    /**
     * Implements the GenericHeader::IDataWriter interface for serializing header content to a
     * FastOS_FileInterface instance.
     */
    class FileWriter : public IDataWriter {
    private:
        FastOS_FileInterface &_file;

    public:
        FileWriter(FastOS_FileInterface &file);
        size_t putData(const char *buf, size_t len) override;
    };

private:
    size_t _alignTo;
    size_t _minSize;
    size_t _fileSize;

public:
    /**
     * Constructs a new instance of this class.
     *
     * @param alignTo The number of bytes to which the serialized size must be aligned to.
     * @param minSize The minimum number of bytes of the serialized size.
     */
    FileHeader(size_t alignTo = 8u, size_t minSize = 0u);

    /**
     * This function overrides GenericHeader::getSize() to align header size to the number of bytes supplied
     * to the constructor of this class. Furthermore, it will attempt to keep the header size constant after
     * an initial read() or write() so that one can later rewrite() it.
     *
     * @return The number of bytes required to hold the content of this.
     */
    size_t getSize() const override;

    /**
     * Deserializes header content from the given file into this. This requires that the file is open in read
     * mode, and that it is positioned at the start of the file.
     *
     * @param file The file to read from.
     * @return The number of bytes read.
     */
    size_t readFile(FastOS_FileInterface &file);

    /**
     * Serializes the content of this into the given file. This requires that the file is open in write mode,
     * and that it is positioned at the start of the file.
     *
     * @param file The file to write to.
     * @return The number of bytes written.
     */
    size_t writeFile(FastOS_FileInterface &file) const;

    /**
     * Serializes the content of this into the given file. This requires that the file is open in read-write
     * mode. This method reads the first 64 bits of the file to ensure that it is compatible with this, then
     * write its content to it. Finally, it moves the file position back to where it was before.
     *
     * @param file The file to write to.
     * @return The number of bytes written.
     */
    size_t rewriteFile(FastOS_FileInterface &file);
};

/**
 * Implements ostream operator for GenericHeader::Tag class. This method will only output the actual value of
 * the tag, not its name or type. Without this operator you would have to switch on tag type to decide which
 * value accessor to use.
 */
vespalib::asciistream &operator<<(vespalib::asciistream &out, const GenericHeader::Tag &tag);

} // namespace

