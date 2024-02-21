// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package slime

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
)

func u64(i int64) uint64 {
	return uint64(i)
}

func i64(i int64) int64 {
	return i
}

func TestZigZag(t *testing.T) {
	assert.Equal(t, u64(0), encode_zigzag(0))
	assert.Equal(t, i64(0), decode_zigzag(encode_zigzag(0)))

	assert.Equal(t, u64(1), encode_zigzag(-1))
	assert.Equal(t, i64(-1), decode_zigzag(encode_zigzag(-1)))

	assert.Equal(t, u64(2), encode_zigzag(1))
	assert.Equal(t, i64(1), decode_zigzag(encode_zigzag(1)))

	assert.Equal(t, u64(3), encode_zigzag(-2))
	assert.Equal(t, i64(-2), decode_zigzag(encode_zigzag(-2)))

	assert.Equal(t, u64(4), encode_zigzag(2))
	assert.Equal(t, i64(2), decode_zigzag(encode_zigzag(2)))

	assert.Equal(t, u64(1999), encode_zigzag(-1000))
	assert.Equal(t, i64(-1000), decode_zigzag(encode_zigzag(-1000)))

	assert.Equal(t, u64(2000), encode_zigzag(1000))
	assert.Equal(t, i64(1000), decode_zigzag(encode_zigzag(1000)))

	assert.Equal(t, u64(-1), encode_zigzag(-0x8000000000000000))
	assert.Equal(t, i64(-0x8000000000000000), decode_zigzag(encode_zigzag(-0x8000000000000000)))

	assert.Equal(t, u64(-2), encode_zigzag(0x7fffffffffffffff))
	assert.Equal(t, i64(0x7fffffffffffffff), decode_zigzag(encode_zigzag(0x7fffffffffffffff)))
}

func TestDoubleConversion(t *testing.T) {
	assert.Equal(t, uint64(0), encode_double(0.0))
	assert.Equal(t, 0.0, decode_double(encode_double(0.0)), 0.0)

	assert.Equal(t, uint64(0x3ff0000000000000), encode_double(1.0))
	assert.Equal(t, 1.0, decode_double(encode_double(1.0)), 0.0)

	assert.Equal(t, uint64(0xbff0000000000000), encode_double(-1.0))
	assert.Equal(t, -1.0, decode_double(encode_double(-1.0)), 0.0)

	assert.Equal(t, uint64(0x4000000000000000), encode_double(2.0))
	assert.Equal(t, 2.0, decode_double(encode_double(2.0)), 0.0)

	assert.Equal(t, uint64(0xc000000000000000), encode_double(-2.0))
	assert.Equal(t, -2.0, decode_double(encode_double(-2.0)), 0.0)

	// go is not IEEE 754 compliant
	negZero := 0.0
	negZero *= -1
	assert.Equal(t, uint64(0x8000000000000000), encode_double(negZero))
	assert.Equal(t, negZero, decode_double(encode_double(negZero)), 0.0)

	assert.Equal(t, uint64(0x400c000000000000), encode_double(3.5))
	assert.Equal(t, 3.5, decode_double(encode_double(3.5)), 0.0)

	assert.Equal(t, uint64(0x40EFFFFC00000000), encode_double(65535.875))
	assert.Equal(t, 65535.875, decode_double(encode_double(65535.875)), 0.0)
}

const (
	TYPE_LIMIT   = 8
	META_LIMIT   = 32
	MAX_NUM_SIZE = 8
)

func TestTypeAndMetaMangling(t *testing.T) {
	var ty Type
	for ty = 0; ty < TYPE_LIMIT; ty++ {
		var meta uint32
		for meta = 0; meta < META_LIMIT; meta++ {
			mangled := encode_type_and_meta(ty, meta)
			assert.Equal(t, ty, decode_type(mangled))
			assert.Equal(t, meta, decode_meta(mangled))
		}
	}
}

func verify_cmpr_int(t *testing.T, value int, expect []byte) {
	out := newBinaryEncoder()
	out.encode_cmpr_uint(uint32(value))
	assert.Equal(t, expect, out.buf)

	in := newBinaryDecoder(out.buf)
	got := in.read_cmpr_int()
	assert.Equal(t, value, got)
	assert.Equal(t, false, in.failed)
	assert.Equal(t, len(out.buf), in.pos)
	fmt.Println("verify_cmpr_int OK:", value)
}

func TestCompressedInt(t *testing.T) {
	var value int
	var wanted []byte

	value = 0
	wanted = []byte{0}
	verify_cmpr_int(t, value, wanted)

	value = 127
	wanted = []byte{127}
	verify_cmpr_int(t, value, wanted)

	value = 128
	wanted = []byte{0x80, 1}
	verify_cmpr_int(t, value, wanted)

	value = 16383
	wanted = []byte{0xff, 127}
	verify_cmpr_int(t, value, wanted)

	value = 16384
	wanted = []byte{0x80, 0x80, 1}
	verify_cmpr_int(t, value, wanted)

	value = 2097151
	wanted = []byte{0xff, 0xff, 127}
	verify_cmpr_int(t, value, wanted)

	value = 2097152
	wanted = []byte{0x80, 0x80, 0x80, 1}
	verify_cmpr_int(t, value, wanted)

	value = 268435455
	wanted = []byte{0xff, 0xff, 0xff, 127}
	verify_cmpr_int(t, value, wanted)

	value = 268435456
	wanted = []byte{0x80, 0x80, 0x80, 0x80, 1}
	verify_cmpr_int(t, value, wanted)

	value = 0x7fff_ffff
	wanted = []byte{0xff, 0xff, 0xff, 0xff, 7}
	verify_cmpr_int(t, value, wanted)
}

func TestEncodingEmptySlime(t *testing.T) {
	slime := NewSlime()
	assert.Equal(t, false, slime.Get().Valid())
	expect := []byte{
		0, // num symbols
		0, // nix
	}
	actual := BinaryEncode(slime)
	assert.Equal(t, expect, actual)
	decoded := BinaryDecode(actual)
	root := decoded.Get()
	assert.Equal(t, true, root.Valid())
	assert.Equal(t, NIX, root.Type())
}

func verifyEncoding(t *testing.T, slime Slime, expect []byte) {
	assert.Equal(t, expect, BinaryEncode(slime))
	orig := JsonEncode(slime)
	fmt.Println("orig:", orig)
	decoded := BinaryDecode(expect)
	got := JsonEncode(decoded)
	fmt.Println(" got:", got)
	assert.Equal(t, orig, got)
}

func TestEncodingSlimeHoldingASingleBasicValue(t *testing.T) {
	var slime Slime
	var expect []byte

	slime = NewSlime()
	slime.SetBool(false)
	expect = []byte{0, byte(BOOL)}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetBool(true)
	expect = []byte{0, byte(BOOL) | 8}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetLong(0)
	expect = []byte{0, byte(LONG)}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetLong(13)
	expect = []byte{0, byte(LONG) | 8, 13 * 2}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetLong(-123456789)
	var ev uint64 = (2 * 123456789) - 1
	b1 := byte(ev)
	b2 := byte(ev >> 8)
	b3 := byte(ev >> 16)
	b4 := byte(ev >> 24)

	expect = []byte{0, byte(LONG) | 32, b1, b2, b3, b4}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetDouble(0.0)
	expect = []byte{0, byte(DOUBLE)}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetDouble(1.0)
	expect = []byte{0, byte(DOUBLE) | 16, 0x3f, 0xf0}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetString("")
	expect = []byte{0, byte(STRING), 0}

	slime = NewSlime()
	slime.SetString("fo")
	expect = []byte{0, byte(STRING) | 24, 'f', 'o'}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
	expect = []byte{0, byte(STRING), 26 * 2,
		'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
		'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
		'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
		'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F',
		'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
		'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
		'W', 'X', 'Y', 'Z'}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetData(emptyByteSlice)
	expect = []byte{0, byte(DATA) | 8}
	verifyEncoding(t, slime, expect)

	slime = NewSlime()
	slime.SetData([]byte{42, 133})
	expect = []byte{0, byte(DATA) | 24, 42, 133}
	verifyEncoding(t, slime, expect)
}

func TestEncodingSlimeArray(t *testing.T) {
	slime := NewSlime()
	cursor := slime.SetArray()
	cursor.AddNix()
	cursor.AddBool(true)
	cursor.AddLong(42)
	cursor.AddDouble(3.5)
	cursor.AddString("string")
	cursor.AddData([]byte{'d', 'a', 't', 'a'})
	expect := []byte{
		0,                 // num symbols
		byte(ARRAY) | 7*8, // value type and size
		0,                 // nix
		byte(BOOL) | 8,
		byte(LONG) | 8, 42 * 2,
		byte(DOUBLE) | 16, 0x40, 0x0c, // 3.5
		byte(STRING) | 7*8, 's', 't', 'r', 'i', 'n', 'g',
		byte(DATA) | 5*8, 'd', 'a', 't', 'a',
	}
	verifyEncoding(t, slime, expect)
}

func TestEncodingSlimeObject(t *testing.T) {
	slime := NewSlime()
	cursor := slime.SetObject()
	cursor.SetNix("a")
	cursor.SetBool("bar", true)
	cursor.SetLong("c", 42)
	cursor.SetDouble("dblval", 3.5)
	cursor.SetString("e", "string")
	cursor.SetData("f", []byte{'d', 'a', 't', 'a'})
	expect := []byte{
		6,                                // num symbols
		1, 'a', 3, 'b', 'a', 'r', 1, 'c', // symbol table
		6, 'd', 'b', 'l', 'v', 'a', 'l',
		1, 'e', 1, 'f',
		byte(OBJECT) | 7*8, // value type and size
		0, byte(NIX),
		1, byte(BOOL) | 8,
		2, byte(LONG) | 8, 42 * 2,
		3, byte(DOUBLE) | 16, 0x40, 0x0c, // 3.5
		4, byte(STRING) | 7*8, 's', 't', 'r', 'i', 'n', 'g',
		5, byte(DATA) | 5*8, 'd', 'a', 't', 'a',
	}
	verifyEncoding(t, slime, expect)
	slime = BinaryDecode(expect)
	root := slime.Get()
	assert.Equal(t, true, root.Field("a").Valid())
}

func TestEncodingComplexSlimeStructure(t *testing.T) {
	slime := NewSlime()
	c1 := slime.SetObject()
	c1.SetLong("bar", 10)
	c2 := c1.SetArray("foo")
	c2.AddLong(20)
	c3 := c2.AddObject()
	c3.SetLong("answer", 42)
	expect := []byte{
		3, // num symbols
		3, 'b', 'a', 'r',
		3, 'f', 'o', 'o',
		6, 'a', 'n', 's', 'w', 'e', 'r',
		byte(OBJECT) | 3*8, // value type, size 2
		0, byte(LONG) | 8, 10 * 2,
		1, byte(ARRAY) | 3*8, // nested value type, size 2
		byte(LONG) | 8, 20 * 2,
		byte(OBJECT) | 2*8, // doubly nested value, size 1
		2, byte(LONG) | 8, 42 * 2,
	}
	verifyEncoding(t, slime, expect)
}

func TestEncodingSlimeReusingSymbols(t *testing.T) {
	slime := NewSlime()
	c1 := slime.SetArray()
	c2a := c1.AddObject()
	c2a.SetLong("foo", 10)
	c2a.SetLong("bar", 20)
	c2b := c1.AddObject()
	c2b.SetLong("foo", 100)
	c2b.SetLong("bar", 200)
	expect := []byte{
		2, // num symbols
		3, 'b', 'a', 'r',
		3, 'f', 'o', 'o',
		byte(ARRAY) | 3*8,           // value type and size
		byte(OBJECT) | 3*8,          // nested value
		0, byte(LONG) | 1*8, 20 * 2, // bar
		1, byte(LONG) | 1*8, 10 * 2, // foo
		byte(OBJECT) | 3*8,          // nested value
		0, byte(LONG) | 2*8, 144, 1, // bar: 2*200 = 400 = 256 + 144
		1, byte(LONG) | 1*8, 100 * 2, // foo
	}
	verifyEncoding(t, slime, expect)
}

func TestDecodingSlimeWithDifferentSymbolOrder(t *testing.T) {
	data := []byte{
		5,                                      // num symbols
		1, 'd', 1, 'e', 1, 'f', 1, 'b', 1, 'c', // symbol table
		byte(OBJECT) | 6*8,  // value type and size
		3, byte(BOOL) | 1*8, // b
		1, byte(STRING) | 7*8, // e
		's', 't', 'r', 'i', 'n', 'g',
		4, byte(LONG) | 1*8, 5 * 2, // c
		0, byte(DOUBLE) | 2*8, 0x40, 0x0c, // d
		2, byte(DATA) | 5*8, // f
		'd', 'a', 't', 'a',
	}
	slime := BinaryDecode(data)
	fmt.Println(" got:", JsonEncode(slime))
	c := slime.Get()
	assert.Equal(t, true, c.Valid())
	assert.Equal(t, OBJECT, c.Type())
	assert.Equal(t, 5, c.Fields())
	assert.Equal(t, true, c.Field("b").AsBool())
	assert.Equal(t, int64(5), c.Field("c").AsLong())
	assert.Equal(t, 3.5, c.Field("d").AsDouble())
	assert.Equal(t, "string", c.Field("e").AsString())
	expd := []byte{'d', 'a', 't', 'a'}
	assert.Equal(t, expd, c.Field("f").AsData())
	assert.Equal(t, false, c.Entry(5).Valid()) // not ARRAY
}
