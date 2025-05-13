// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"math"
	"strconv"
	"strings"
)

var (
	hex []byte = []byte("0123456789ABCDEF")
)

func fromHexDigit(digit byte) int {
	switch digit {
	case byte('0'):
		return 0
	case byte('1'):
		return 1
	case byte('2'):
		return 2
	case byte('3'):
		return 3
	case byte('4'):
		return 4
	case byte('5'):
		return 5
	case byte('6'):
		return 6
	case byte('7'):
		return 7
	case byte('8'):
		return 8
	case byte('9'):
		return 9
	case byte('a'), byte('A'):
		return 0xA
	case byte('b'), byte('B'):
		return 0xB
	case byte('c'), byte('C'):
		return 0xC
	case byte('d'), byte('D'):
		return 0xD
	case byte('e'), byte('E'):
		return 0xE
	case byte('f'), byte('F'):
		return 0xF
	default:
		return -1
	}
}

func ToJson(value Value, compact bool) string {
	var out strings.Builder
	err := EncodeJson(value, compact, &out)
	if err != nil {
		panic("error writing to string builder")
	}
	return out.String()
}

func EncodeJson(value Value, compact bool, output io.Writer) error {
	encoder := &jsonEncoder{output: output, compact: compact, first: true}
	encoder.encode(value)
	return encoder.err
}

type jsonEncoder struct {
	output  io.Writer
	compact bool
	level   int
	first   bool
	err     error
}

func (e *jsonEncoder) fmt(format string, stuff ...any) {
	if e.err == nil {
		_, e.err = fmt.Fprintf(e.output, format, stuff...)
	}
}

func (e *jsonEncoder) put(value byte) {
	if e.err == nil {
		_, e.err = e.output.Write([]byte{value})
	}
}

func (e *jsonEncoder) openScope(tag byte) {
	e.put(tag)
	e.level++
	e.first = true
}

func (e *jsonEncoder) separate(useComma bool) {
	if !e.first && useComma {
		e.put(byte(','))
	} else {
		e.first = false
	}
	if !e.compact {
		e.fmt("\n%*s", e.level*4, "")
	}
}

func (e *jsonEncoder) closeScope(tag byte) {
	e.level--
	e.separate(false)
	e.put(tag)
}

func (e *jsonEncoder) encode(value Value) {
	e.encodeValue(value)
	if !e.compact {
		e.put(byte('\n'))
	}
}

func (e *jsonEncoder) encodeValue(value Value) {
	switch value.Type() {
	case EMPTY:
		e.encodeEMPTY()
	case BOOL:
		e.encodeBOOL(value.AsBool())
	case LONG:
		e.encodeLONG(value.AsLong())
	case DOUBLE:
		e.encodeDOUBLE(value.AsDouble())
	case STRING:
		e.encodeSTRING(value.AsString())
	case DATA:
		e.encodeDATA(value.AsData())
	case ARRAY:
		e.encodeARRAY(value)
	case OBJECT:
		e.encodeOBJECT(value)
	}
}

func (e *jsonEncoder) encodeEMPTY() {
	e.fmt("null")
}

func (e *jsonEncoder) encodeBOOL(value bool) {
	if value {
		e.fmt("true")
	} else {
		e.fmt("false")
	}
}

func (e *jsonEncoder) encodeLONG(value int64) {
	e.fmt("%v", value)
}

func (e *jsonEncoder) encodeDOUBLE(value float64) {
	if math.IsNaN(value) || math.IsInf(value, 0) {
		e.fmt("null")
	} else {
		e.fmt("%v", value)
	}
}

func (e *jsonEncoder) encodeSTRING(value string) {
	e.put(byte('"'))
	for i := 0; i < len(value); i++ {
		switch c := value[i]; c {
		case '"':
			e.fmt("\\\"")
		case '\\':
			e.fmt("\\\\")
		case '\b':
			e.fmt("\\b")
		case '\f':
			e.fmt("\\f")
		case '\n':
			e.fmt("\\n")
		case '\r':
			e.fmt("\\r")
		case '\t':
			e.fmt("\\t")
		default:
			if c > 0x1f {
				e.put(c)
			} else {
				e.fmt("\\u00")
				e.put(hex[(c>>4)&0xf])
				e.put(hex[c&0xf])
			}
		}
	}
	e.put(byte('"'))
}

func (e *jsonEncoder) encodeDATA(value []byte) {
	e.fmt("\"0x")
	for i := 0; i < len(value); i++ {
		c := value[i]
		e.put(hex[(c>>4)&0xf])
		e.put(hex[c&0xf])
	}
	e.put(byte('"'))
}

func (e *jsonEncoder) encodeARRAY(value Value) {
	e.openScope(byte('['))
	value.EachEntry(func(idx int, val Value) {
		e.separate(true)
		e.encodeValue(val)
	})
	e.closeScope(byte(']'))
}

func (e *jsonEncoder) encodeOBJECT(value Value) {
	e.openScope(byte('{'))
	value.EachField(func(name string, val Value) {
		e.separate(true)
		e.encodeSTRING(name)
		if e.compact {
			e.put(byte(':'))
		} else {
			e.fmt(": ")
		}
		e.encodeValue(val)
	})
	e.closeScope(byte('}'))
}

func FromJson(input string) Value {
	return DecodeJson(strings.NewReader(input))
}

func DecodeJson(input io.Reader) Value {
	decoder := &jsonDecoder{input: input, buf: make([]byte, 1)}
	return decoder.decode()
}

type jsonDecoder struct {
	input io.Reader
	buf   []byte
	c     byte
	key   bytes.Buffer
	val   bytes.Buffer
	err   error
}

func (d *jsonDecoder) next() {
	if d.err == nil {
		len, e := d.input.Read(d.buf)
		if len == 1 {
			d.c = d.buf[0]
		} else {
			if e != nil {
				d.err = e
			} else {
				d.err = errors.New("read failed without error")
			}
			d.c = 0
		}
	} else if d.err == io.EOF {
		d.err = errors.New("input underflow")
	}
}

func (d *jsonDecoder) fail(msg string) {
	if d.err == nil || d.err == io.EOF {
		d.err = errors.New(msg)
		d.c = 0
	}
}

func (d *jsonDecoder) failWithError(err error) {
	if d.err == nil {
		d.err = err
		d.c = 0
	}
}

func (d *jsonDecoder) skip(x byte) bool {
	if d.c != x {
		return false
	}
	d.next()
	return true
}

func (d *jsonDecoder) expect(str string) {
	for i := 0; i < len(str); i++ {
		if !d.skip(str[i]) {
			d.fail("unexpected character")
			return
		}
	}
}

func (d *jsonDecoder) skipWhiteSpace() {
	for {
		switch d.c {
		case byte(' '), byte('\t'), byte('\n'), byte('\r'):
			d.next()
		default:
			return
		}
	}
}

func (d *jsonDecoder) decode() Value {
	var result Value
	d.next()
	d.decodeValue(InsertRoot(&result))
	if result != nil && errors.Is(d.err, io.EOF) {
		d.err = nil
	}
	if d.err != nil {
		return Error(d.err)
	}
	if result == nil {
		return ErrorMsg("missing value")
	}
	return result
}

func (d *jsonDecoder) decodeValue(inserter Inserter) {
	d.skipWhiteSpace()
	switch d.c {
	case byte('"'), byte('\''):
		d.decodeString(inserter)
	case byte('{'):
		d.decodeObject(inserter)
	case byte('['):
		d.decodeArray(inserter)
	case byte('t'):
		d.expect("true")
		inserter.Insert(Bool(true))
	case byte('f'):
		d.expect("false")
		inserter.Insert(Bool(false))
	case byte('n'):
		d.expect("null")
		inserter.Insert(Empty)
	case byte('x'):
		d.decodeData(inserter)
	case byte('-'), byte('0'), byte('1'), byte('2'), byte('3'), byte('4'), byte('5'), byte('6'), byte('7'), byte('8'), byte('9'):
		d.decodeNumber(inserter)
	default:
		d.fail("invalid initial character for value")
	}
}

func (d *jsonDecoder) readHexRune() rune {
	var value rune
	for i := 0; i < 4; i++ {
		x := fromHexDigit(d.c)
		if x < 0 {
			d.fail("invalid hex character")
			return 0
		}
		value = (value << 4) | rune(x)
		d.next()
	}
	return value
}

func (d *jsonDecoder) unescapeUtf16() rune {
	d.expect("u")
	codepoint := d.readHexRune()
	if codepoint >= 0xd800 {
		if codepoint < 0xdc00 { // high
			d.expect("\\u")
			low := d.readHexRune()
			if low >= 0xdc00 && low < 0xe000 {
				codepoint = 0x10000 + ((codepoint - 0xd800) << 10) + (low - 0xdc00)
			} else {
				d.fail("missing low surrogate")
			}
		} else if codepoint < 0xe000 { // low
			d.fail("unexpected low surrogate")
		}
	}
	return codepoint
}

func (d *jsonDecoder) readString(str *bytes.Buffer) {
	str.Reset()
	quote := d.c
	d.next()
	for {
		switch d.c {
		case byte('\\'):
			d.next()
			switch d.c {
			case byte('"'), byte('\\'), byte('/'), byte('\''):
				str.WriteByte(d.c)
			case byte('b'):
				str.WriteByte(byte('\b'))
			case byte('f'):
				str.WriteByte(byte('\f'))
			case byte('n'):
				str.WriteByte(byte('\n'))
			case byte('r'):
				str.WriteByte(byte('\r'))
			case byte('t'):
				str.WriteByte(byte('\t'))
			case byte('u'):
				str.WriteRune(d.unescapeUtf16())
				continue
			default:
				d.fail("invalid quoted char")
			}
			d.next()
		case byte('"'), byte('\''):
			if d.c == quote {
				d.next()
				return
			} else {
				str.WriteByte(d.c)
				d.next()
			}
		case 0:
			d.fail("unterminated string")
			return
		default:
			str.WriteByte(d.c)
			d.next()
		}
	}
}

func (d *jsonDecoder) readKey() {
	switch d.c {
	case byte('"'), byte('\''):
		d.readString(&d.key)
		return
	default:
		d.key.Reset()
		for {
			switch d.c {
			case byte(':'), byte(' '), byte('\t'), byte('\n'), byte('\r'), 0:
				return
			default:
				d.key.WriteByte(d.c)
				d.next()
			}
		}
	}
}

func (d *jsonDecoder) decodeString(inserter Inserter) {
	d.readString(&d.val)
	inserter.Insert(String(d.val.String()))
}

func (d *jsonDecoder) decodeObject(inserter Inserter) {
	obj := inserter.Insert(Object())
	d.expect("{")
	d.skipWhiteSpace()
	if d.c != byte('}') {
		for again := true; again; again = d.skip(byte(',')) {
			d.skipWhiteSpace()
			d.readKey()
			d.skipWhiteSpace()
			d.expect(":")
			d.decodeValue(InsertField(obj, d.key.String()))
			d.skipWhiteSpace()
		}
	}
	d.expect("}")
}

func (d *jsonDecoder) decodeArray(inserter Inserter) {
	arr := inserter.Insert(Array())
	arrInserter := InsertEntry(arr)
	d.expect("[")
	d.skipWhiteSpace()
	if d.c != byte(']') {
		for again := true; again; again = d.skip(byte(',')) {
			d.decodeValue(arrInserter)
			d.skipWhiteSpace()
		}
	}
	d.expect("]")
}

func (d *jsonDecoder) decodeData(inserter Inserter) {
	d.val.Reset()
	for {
		d.next()
		hi := fromHexDigit(d.c)
		if hi < 0 {
			tmp := make([]byte, d.val.Len())
			copy(tmp, d.val.Bytes())
			inserter.Insert(Data(tmp))
			return
		}
		d.next()
		lo := fromHexDigit(d.c)
		if lo < 0 {
			d.fail("invalid hex dump")
			return
		}
		d.val.WriteByte(byte((hi << 4) | lo))
	}
}

func (d *jsonDecoder) decodeNumber(inserter Inserter) {
	isLong := true
	d.val.Reset()
	d.val.WriteByte(d.c)
	d.next()
	for {
		switch d.c {
		case byte('+'), byte('-'), byte('.'), byte('e'), byte('E'):
			isLong = false
			d.val.WriteByte(d.c)
			d.next()
		case byte('0'), byte('1'), byte('2'), byte('3'), byte('4'),
			byte('5'), byte('6'), byte('7'), byte('8'), byte('9'):
			d.val.WriteByte(d.c)
			d.next()
		default:
			if isLong {
				x, err := strconv.ParseInt(d.val.String(), 10, 64)
				if err != nil {
					d.failWithError(err)
				} else {
					inserter.Insert(Long(x))
				}
			} else {
				x, err := strconv.ParseFloat(d.val.String(), 64)
				if err != nil {
					d.failWithError(err)
				} else {
					inserter.Insert(Double(x))
				}
			}
			return
		}
	}
}
