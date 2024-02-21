// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type binaryDecoder struct {
	buf     []byte
	pos     int
	symbols []string
	failed  bool
	failure string
}

var emptyByteSlice = make([]byte, 0)

func newBinaryDecoder(input []byte) binaryDecoder {
	return binaryDecoder{
		buf:    input,
		pos:    0,
		failed: false,
	}
}

func (in *binaryDecoder) fail(reason string) {
	in.failed = true
	in.failure = reason
}

func (in *binaryDecoder) getByte() byte {
	var b byte = 0
	if !in.failed {
		if in.pos < len(in.buf) {
			b = in.buf[in.pos]
			in.pos++
		} else {
			in.fail("buffer underflow")
		}
	}
	return b
}

func (in *binaryDecoder) getBytes(sz int) []byte {
	if !in.failed {
		if in.pos+sz <= len(in.buf) {
			start := in.pos
			endp := start + sz
			in.pos = endp
			return in.buf[start:endp]
		}
		in.fail("buffer underflow")
	}
	return emptyByteSlice
}

func (in *binaryDecoder) read_bytes_le(sz uint32) uint64 {
	var value uint64 = 0
	shift := 0
	for i := uint32(0); i < sz; i++ {
		b := uint64(in.getByte())
		value = value | (b << shift)
		shift = shift + 8
	}
	return value
}

func (in *binaryDecoder) read_bytes_be(sz uint32) uint64 {
	var value uint64 = 0
	shift := 56
	for i := uint32(0); i < sz; i++ {
		b := uint64(in.getByte())
		value = value | (b << shift)
		shift = shift - 8
	}
	return value
}

func (in *binaryDecoder) read_cmpr_int() int {
	next := in.getByte()
	var value int64 = int64(next & 0x7f)
	shift := 0
	for (next & 0x80) != 0 {
		shift += 7
		next = in.getByte()
		value = value | ((int64(next) & 0x7f) << shift)
		if shift > 32 || value > 0x7fff_ffff {
			in.fail("compressed int overflow")
			value = 0
			next = 0
		}
	}
	return int(value)
}

func (in *binaryDecoder) read_size(meta uint32) int {
	if meta != 0 {
		return int(meta - 1)
	}
	return in.read_cmpr_int()
}

func (in *binaryDecoder) decodeSymbolTable() {
	numSymbols := in.read_cmpr_int()
	in.symbols = make([]string, numSymbols)
	for i := 0; i < numSymbols; i++ {
		sz := in.read_cmpr_int()
		buf := in.getBytes(sz)
		in.symbols[i] = string(buf)
	}
}

func (in *binaryDecoder) decodeValue() Cursor {
	b := in.getByte()
	ty := decode_type(b)
	meta := decode_meta(b)
	switch ty {
	case NIX:
		return ValidNix
	case BOOL:
		return in.decodeBOOL(meta)
	case LONG:
		return in.decodeLONG(meta)
	case DOUBLE:
		return in.decodeDOUBLE(meta)
	case STRING:
		return in.decodeSTRING(meta)
	case DATA:
		return in.decodeDATA(meta)
	case ARRAY:
		return in.decodeARRAY(meta)
	case OBJECT:
		return in.decodeOBJECT(meta)
	}
	panic("bad type")
	// return InvalidNix
}

func (in *binaryDecoder) decodeBOOL(meta uint32) Cursor {
	return newBoolValue(meta != 0)
}

func (in *binaryDecoder) decodeLONG(meta uint32) Cursor {
	var encoded uint64 = in.read_bytes_le(meta)
	return newLongValue(decode_zigzag(encoded))
}

func (in *binaryDecoder) decodeDOUBLE(meta uint32) Cursor {
	var encoded uint64 = in.read_bytes_be(meta)
	return newDoubleValue(decode_double(encoded))
}

func (in *binaryDecoder) decodeSTRING(meta uint32) Cursor {
	sz := in.read_size(meta)
	image := in.getBytes(sz)
	return newStringValue(string(image))
}

func (in *binaryDecoder) decodeDATA(meta uint32) Cursor {
	sz := in.read_size(meta)
	image := in.getBytes(sz)
	return newDataValue(image)
}

func (in *binaryDecoder) decodeARRAY(meta uint32) Cursor {
	res := newArrayValue()
	sz := in.read_size(meta)
	for i := 0; i < sz; i++ {
		elem := in.decodeValue()
		res.addElement(elem)
	}
	return res
}

func (in *binaryDecoder) decodeOBJECT(meta uint32) Cursor {
	res := newObjectValue()
	sz := in.read_size(meta)
	for i := 0; i < sz; i++ {
		symbol := in.read_cmpr_int()
		if symbol < len(in.symbols) {
			name := in.symbols[symbol]
			elem := in.decodeValue()
			res.addElement(name, elem)
		} else {
			in.fail("symbol id out of range decoding object")
			return res
		}
	}
	return res
}
