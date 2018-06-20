// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "binary_format.h"
#include "slime.h"
#include <vespa/vespalib/data/memory_input.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.data.slime.binary_format");

namespace vespalib {
namespace slime {

namespace binary_format {

struct BinaryEncoder : public ArrayTraverser,
                       public ObjectSymbolTraverser
{
    OutputWriter &out;
    BinaryEncoder(OutputWriter &out_in) : out(out_in) {}
    void encodeNix() {
        out.write(NIX::ID);
    }
    void encodeBool(bool value) {
        out.write(encode_type_and_meta(BOOL::ID, value ? 1 : 0));
    }
    void encodeLong(int64_t value) {
        write_type_and_bytes<false>(out, LONG::ID, encode_zigzag(value));
    }
    void encodeDouble(double value) {
        write_type_and_bytes<true>(out, DOUBLE::ID, encode_double(value));
    }
    void encodeString(const Memory &memory) {
        write_type_and_size(out, STRING::ID, memory.size);
        out.write(memory.data, memory.size);
    }
    void encodeData(const Memory &memory) {
        write_type_and_size(out, DATA::ID, memory.size);
        out.write(memory.data, memory.size);
    }
    void encodeArray(const Inspector &inspector) {
        ArrayTraverser &array_traverser = *this;
        write_type_and_size(out, ARRAY::ID, inspector.children());
        inspector.traverse(array_traverser);
    }
    void encodeObject(const Inspector &inspector) {
        ObjectSymbolTraverser &object_traverser = *this;
        write_type_and_size(out, OBJECT::ID, inspector.children());
        inspector.traverse(object_traverser);
    }
    void encodeValue(const Inspector &inspector) {
        switch (inspector.type().getId()) {
        case NIX::ID:    return encodeNix();
        case BOOL::ID:   return encodeBool(inspector.asBool());
        case LONG::ID:   return encodeLong(inspector.asLong());
        case DOUBLE::ID: return encodeDouble(inspector.asDouble());
        case STRING::ID: return encodeString(inspector.asString());
        case DATA::ID:   return encodeData(inspector.asData());
        case ARRAY::ID:  return encodeArray(inspector);
        case OBJECT::ID: return encodeObject(inspector);
        }
        LOG_ABORT("should not be reached");
    }
    void encodeSymbolTable(const Slime &slime) {
        size_t numSymbols = slime.symbols();
        write_cmpr_ulong(out, numSymbols);
        for (size_t i = 0; i < numSymbols; ++i) {
            Memory image = slime.inspect(Symbol(i));
            write_cmpr_ulong(out, image.size);
            out.write(image.data, image.size);
        }
    }
    void entry(size_t, const Inspector &inspector) override;
    void field(const Symbol &symbol, const Inspector &inspector) override;
};

void
BinaryEncoder::entry(size_t, const Inspector &inspector)
{
    encodeValue(inspector);
}

void
BinaryEncoder::field(const Symbol &symbol, const Inspector &inspector)
{
    write_cmpr_ulong(out, symbol.getValue());
    encodeValue(inspector);
}

//-----------------------------------------------------------------------------

struct DirectSymbols {
    void hint_symbol_count(size_t) {}
    bool add_symbol(Symbol symbol, size_t i, InputReader &in) {
        if (symbol.getValue() != i) {
            in.fail("duplicate symbols in symbol table");
            return false;
        }
        return true;
    }
    Symbol map_symbol(Symbol symbol) const { return symbol; }
};

struct MappedSymbols {
    std::vector<Symbol> symbol_mapping;
    void hint_symbol_count(size_t n) {
        symbol_mapping.reserve(n);
    }
    bool add_symbol(Symbol symbol, size_t, InputReader &) {
        symbol_mapping.push_back(symbol);
        return true;
    }
    Symbol map_symbol(Symbol symbol) const {
        return (symbol.getValue() < symbol_mapping.size())
            ? symbol_mapping[symbol.getValue()]
            : symbol;
    }
};

template <bool remap_symbols>
struct SymbolHandler {
    typedef typename std::conditional<remap_symbols, MappedSymbols, DirectSymbols>::type type;
};

template <bool remap_symbols>
struct BinaryDecoder : SymbolHandler<remap_symbols>::type {

    InputReader &in;

    using SymbolHandler<remap_symbols>::type::hint_symbol_count;
    using SymbolHandler<remap_symbols>::type::add_symbol;
    using SymbolHandler<remap_symbols>::type::map_symbol;

    BinaryDecoder(InputReader &input) : in(input) {}

    Cursor &decodeNix(const Inserter &inserter) {
        return inserter.insertNix();
    }

    Cursor &decodeBool(const Inserter &inserter, uint32_t meta) {
        return inserter.insertBool(meta != 0);
    }

    Cursor &decodeLong(const Inserter &inserter, uint32_t meta) {
        return inserter.insertLong(decode_zigzag(read_bytes<false>(in, meta)));
    }

    Cursor &decodeDouble(const Inserter &inserter, uint32_t meta) {
        return inserter.insertDouble(decode_double(read_bytes<true>(in, meta)));
    }

    Cursor &decodeString(const Inserter &inserter, uint32_t meta) {
        uint64_t size = read_size(in, meta);
        return inserter.insertString(in.read(size));
    }

    Cursor &decodeData(const Inserter &inserter, uint32_t meta) {
        uint64_t size = read_size(in, meta);
        return inserter.insertData(in.read(size));
    }

    Cursor &decodeArray(const Inserter &inserter, uint32_t meta);

    Cursor &decodeObject(const Inserter &inserter, uint32_t meta);

    Cursor &decodeValue(const Inserter &inserter, uint32_t type, uint32_t meta) {
        switch (type) {
        case NIX::ID:    return decodeNix(inserter);
        case BOOL::ID:   return decodeBool(inserter, meta);
        case LONG::ID:   return decodeLong(inserter, meta);
        case DOUBLE::ID: return decodeDouble(inserter, meta);
        case STRING::ID: return decodeString(inserter, meta);
        case DATA::ID:   return decodeData(inserter, meta);
        case ARRAY::ID:  return decodeArray(inserter, meta);
        case OBJECT::ID: return decodeObject(inserter, meta);
        }
        LOG_ABORT("should not be reached");
    }

    void decodeValue(const Inserter &inserter) {
        char byte = in.read();
        Cursor &cursor = decodeValue(inserter,
                                    decode_type(byte),
                                    decode_meta(byte));
        if (!cursor.valid()) {
            in.fail("failed to decode value");
        }
    }

    void decodeSymbolTable(Slime &slime) {
        uint64_t numSymbols = read_cmpr_ulong(in);
        hint_symbol_count(numSymbols);
        for (size_t i = 0; i < numSymbols; ++i) {
            uint64_t size = read_cmpr_ulong(in);
            Memory image = in.read(size);
            Symbol symbol = slime.insert(image);
            if (!add_symbol(symbol, i, in)) {
                return;
            }
        }
    }
};

template <bool remap_symbols>
Cursor &
BinaryDecoder<remap_symbols>::decodeArray(const Inserter &inserter, uint32_t meta)
{
    Cursor &cursor = inserter.insertArray();
    ArrayInserter childInserter(cursor);
    uint64_t size = read_size(in, meta);
    for (size_t i = 0; i < size; ++i) {
        decodeValue(childInserter);
    }
    return cursor;
}

template <bool remap_symbols>
Cursor &
BinaryDecoder<remap_symbols>::decodeObject(const Inserter &inserter, uint32_t meta)
{
    Cursor &cursor = inserter.insertObject();
    uint64_t size = read_size(in, meta);
    for (size_t i = 0; i < size; ++i) {
        Symbol symbol(map_symbol(read_cmpr_ulong(in)));
        ObjectSymbolInserter childInserter(cursor, symbol);
        decodeValue(childInserter);
    }
    return cursor;
}

template <bool remap_symbols>
size_t decode(const Memory &memory, Slime &slime, const Inserter &inserter) {
    MemoryInput memory_input(memory);
    InputReader input(memory_input);
    binary_format::BinaryDecoder<remap_symbols> decoder(input);
    decoder.decodeSymbolTable(slime);
    decoder.decodeValue(inserter);
    if (input.failed() && !remap_symbols) {
        slime.wrap("partial_result");
        slime.get().setLong("offending_offset", input.get_offset());
        slime.get().setString("error_message", input.get_error_message());
    }
    return input.failed() ? 0 : input.get_offset();
}

} // namespace vespalib::slime::binary_format

void
BinaryFormat::encode(const Slime &slime, Output &output)
{
    size_t chunk_size = 8000;
    OutputWriter out(output, chunk_size);
    binary_format::BinaryEncoder encoder(out);
    encoder.encodeSymbolTable(slime);
    encoder.encodeValue(slime.get());
}

size_t
BinaryFormat::decode(const Memory &memory, Slime &slime)
{
    return binary_format::decode<false>(memory, slime, SlimeInserter(slime));
}

size_t
BinaryFormat::decode_into(const Memory &memory, Slime &slime, const Inserter &inserter)
{
    return binary_format::decode<true>(memory, slime, inserter);
}

namespace binary_format {

void
write_cmpr_ulong(OutputWriter &out, uint64_t value) {
    out.commit(encode_cmpr_ulong(out.reserve(10), value));
}

void
write_type_and_size(OutputWriter &out, uint32_t type, uint64_t size) {
    char *start = out.reserve(11); // max size
    char *pos = start;
    if (size <= 30) {
        *pos++ = encode_type_and_meta(type, size + 1);
    } else {
        *pos++ = encode_type_and_meta(type, 0);
        pos += encode_cmpr_ulong(pos, size);
    }
    out.commit(pos - start);
}

}

} // namespace vespalib::slime
} // namespace vespalib
