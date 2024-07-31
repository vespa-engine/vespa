// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

import (
	"math"
)

func BinaryEncode(s Slime) []byte {
	codec := newBinaryEncoder()
	codec.encodeValue(s.Get())
	return codec.prependSymbolTable()
}

func BinaryDecode(input []byte) Slime {
	codec := newBinaryDecoder(input)
	codec.decodeSymbolTable()
	root := codec.decodeValue()
	s := Slime{root}
	if codec.failed {
		s.Wrap("partial_result")
		s.Get().SetString("error_message", codec.failure)
		s.Get().SetLong("buffer_position", int64(codec.pos))
	}
	return s
}

func encode_zigzag(x int64) uint64 {
	bot63 := x << 1
	inv := x >> 63 // note: ASR because x is signed
	return uint64(bot63 ^ inv)
}

func decode_zigzag(x uint64) int64 {
	top63 := int64(x >> 1) // note: LSR because x is unsigned
	inv := -int64(x & 1)
	return top63 ^ inv
}

func encode_double(x float64) uint64 {
	return math.Float64bits(x)
}

func decode_double(x uint64) float64 {
	return math.Float64frombits(x)
}

func encode_type_and_meta(t Type, meta uint32) byte {
	return byte((meta << 3) | (uint32(t) & 0x7))
}

func decode_type(type_and_meta byte) Type {
	return Type(type_and_meta & 0x7)
}

func decode_meta(type_and_meta byte) uint32 {
	return uint32(type_and_meta >> 3)
}
