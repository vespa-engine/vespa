// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

type binaryEncoder struct {
	buf     []byte
	symbols map[string]uint32
}

func newBinaryEncoder() binaryEncoder {
	return binaryEncoder{
		buf:     make([]byte, 0, 128),
		symbols: make(map[string]uint32),
	}
}

func (out *binaryEncoder) pos() int {
	return len(out.buf)
}

func (out *binaryEncoder) put(b byte) {
	out.buf = append(out.buf, b)
}

func (out *binaryEncoder) putAt(pos int, b byte) {
	out.buf[pos] = b
}

func (out *binaryEncoder) putBytes(bytes []byte) {
	out.buf = append(out.buf, bytes...)
}

func (out *binaryEncoder) putString(value string) {
	out.buf = append(out.buf, value...)
}

func (out *binaryEncoder) encode_cmpr_uint(value uint32) {
	var next byte = byte(value & 0x7f)
	value = value >> 7
	for value != 0 {
		next = next | 0x80
		out.put(next)
		next = byte(value & 0x7f)
		value = value >> 7
	}
	out.put(next)
}

func (out *binaryEncoder) write_type_and_size(ty Type, size uint32) {
	if size <= 30 {
		out.put(encode_type_and_meta(ty, size+1))
	} else {
		out.put(encode_type_and_meta(ty, 0))
		out.encode_cmpr_uint(size)
	}
}

func (out *binaryEncoder) write_type_and_bytes_le(ty Type, bits uint64) {
	pos := out.pos()
	var val byte = 0
	out.put(val)
	var cnt uint32 = 0
	for bits != 0 {
		val = byte(bits & 0xff)
		bits = bits >> 8
		out.put(val)
		cnt++
	}
	val = encode_type_and_meta(ty, cnt)
	out.putAt(pos, val)
}

func (out *binaryEncoder) write_type_and_bytes_be(ty Type, bits uint64) {
	pos := out.pos()
	var val byte = 0
	out.put(val)
	var cnt uint32 = 0
	for bits != 0 {
		val = byte(bits >> 56)
		bits = bits << 8
		out.put(val)
		cnt++
	}
	val = encode_type_and_meta(ty, cnt)
	out.putAt(pos, val)
}

func (out *binaryEncoder) encodeNIX() {
	out.put(byte(NIX))
}

func (out *binaryEncoder) encodeValue(inspector Inspector) {
	switch inspector.Type() {
	case NIX:
		out.encodeNIX()
	case BOOL:
		out.encodeBOOL(inspector.AsBool())
	case LONG:
		out.encodeLONG(inspector.AsLong())
	case DOUBLE:
		out.encodeDOUBLE(inspector.AsDouble())
	case STRING:
		out.encodeSTRING(inspector.AsString())
	case DATA:
		out.encodeDATA(inspector.AsData())
	case ARRAY:
		out.encodeARRAY(inspector)
	case OBJECT:
		out.encodeOBJECT(inspector)
	default:
		panic("Should not be reached")
	}
}

func (out *binaryEncoder) encodeBOOL(value bool) {
	var meta uint32
	if value {
		meta = 1
	} else {
		meta = 0
	}
	out.put(encode_type_and_meta(BOOL, meta))
}

func (out *binaryEncoder) encodeLONG(value int64) {
	out.write_type_and_bytes_le(LONG, encode_zigzag(value))
}

func (out *binaryEncoder) encodeDOUBLE(value float64) {
	out.write_type_and_bytes_be(DOUBLE, encode_double(value))
}

func (out *binaryEncoder) encodeSTRING(value string) {
	out.write_type_and_size(STRING, uint32(len(value)))
	out.putString(value)
}

func (out *binaryEncoder) encodeDATA(value []byte) {
	out.write_type_and_size(DATA, uint32(len(value)))
	out.putBytes(value)
}

func (out *binaryEncoder) Entry(idx int, value Inspector) {
	out.encodeValue(value)
}

func (out *binaryEncoder) encodeARRAY(inspector Inspector) {
	out.write_type_and_size(ARRAY, uint32(inspector.Entries()))
	inspector.TraverseArray(out)
}

func (out *binaryEncoder) findSymbolId(name string) uint32 {
	if value, ok := out.symbols[name]; ok {
		return value
	}
	value := uint32(len(out.symbols))
	out.symbols[name] = value
	return value
}

func (out *binaryEncoder) Field(name string, value Inspector) {
	symbol := out.findSymbolId(name)
	out.encode_cmpr_uint(symbol)
	out.encodeValue(value)
}

func (out *binaryEncoder) encodeOBJECT(inspector Inspector) {
	out.write_type_and_size(OBJECT, uint32(inspector.Fields()))
	inspector.TraverseObject(out)
}

func (out *binaryEncoder) prependSymbolTable() []byte {
	numSymbols := len(out.symbols)
	sorted := make([]string, numSymbols)
	for name, id := range out.symbols {
		sorted[id] = name
	}
	tmp := newBinaryEncoder()
	tmp.encode_cmpr_uint(uint32(numSymbols))
	for _, name := range sorted {
		sz := uint32(len(name))
		tmp.encode_cmpr_uint(sz)
		tmp.putString(name)
	}
	res := make([]byte, len(out.buf)+len(tmp.buf))
	split := len(tmp.buf)
	copy(res[:split], tmp.buf)
	copy(res[split:], out.buf)
	return res
}
