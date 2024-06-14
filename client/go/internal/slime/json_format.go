// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package slime

import (
	"fmt"
	"io"
	"strings"
)

const (
	dblquote  byte = '"'
	backslash byte = '\\'
)

type jsonEncoder struct {
	out   io.Writer
	comma bool
}

func JsonEncode(s Slime) string {
	var buf strings.Builder
	codec := jsonEncoder{out: &buf}
	codec.encode(s.Get())
	return buf.String()
}

func JsonEncodeTo(s Slime, target io.Writer) {
	codec := jsonEncoder{out: target}
	codec.encode(s.Get())
}

func (e *jsonEncoder) Entry(idx int, value Inspector) {
	if e.comma {
		e.out.Write([]byte(", "))
	}
	e.encode(value)
	e.comma = true
}

func (e *jsonEncoder) Field(name string, value Inspector) {
	if e.comma {
		e.out.Write([]byte(", "))
	}
	e.encodeSTRING(name)
	e.out.Write([]byte(": "))
	e.encode(value)
	e.comma = true
}

func (e *jsonEncoder) encode(inspector Inspector) {
	switch inspector.Type() {
	case NIX:
		e.encodeNIX()
	case BOOL:
		e.encodeBOOL(inspector.AsBool())
	case LONG:
		e.encodeLONG(inspector.AsLong())
	case DOUBLE:
		e.encodeDOUBLE(inspector.AsDouble())
	case STRING:
		e.encodeSTRING(inspector.AsString())
	case DATA:
		e.encodeDATA(inspector.AsData())
	case ARRAY:
		e.encodeARRAY(inspector)
	case OBJECT:
		e.encodeOBJECT(inspector)
	}
}

func (e *jsonEncoder) encodeNIX() {
	fmt.Fprintf(e.out, "%s", "null")
}
func (e *jsonEncoder) encodeBOOL(value bool) {
	fmt.Fprintf(e.out, "%v", value)
}
func (e *jsonEncoder) encodeLONG(value int64) {
	fmt.Fprintf(e.out, "%d", value)
}

func (e *jsonEncoder) encodeDOUBLE(value float64) {
	fmt.Fprintf(e.out, "%f", value)
}

func (e *jsonEncoder) encodeSTRING(value string) {
	buf := make([]byte, 0, len(value)*2)
	buf = append(buf, dblquote)
	for i := 0; i < len(value); i++ {
		var c byte = value[i]
		switch c {
		case dblquote:
			buf = append(buf, backslash)
			buf = append(buf, dblquote)
		case backslash:
			buf = append(buf, backslash)
			buf = append(buf, backslash)
		case '\n':
			buf = append(buf, backslash)
			buf = append(buf, 'n')
		default:
			buf = append(buf, c)
		}
	}
	buf = append(buf, dblquote)
	e.out.Write(buf)
}

func hexDigit(digit byte) byte {
	if digit < 10 {
		return byte('0' + digit)
	}
	return byte('A' + digit - 10)
}

func (e *jsonEncoder) encodeDATA(value []byte) {
	buf := make([]byte, 0, len(value)*2+2)
	buf = append(buf, 'x')
	for _, b := range value {
		hv := (b >> 4) & 0xf
		buf = append(buf, hexDigit(hv))
		hv = b & 0xf
		buf = append(buf, hexDigit(hv))
	}
	e.out.Write(buf)
}

func (e *jsonEncoder) encodeARRAY(inspector Inspector) {
	e.out.Write([]byte("["))
	e.comma = false
	inspector.TraverseArray(e)
	e.out.Write([]byte("]"))
}

func (e *jsonEncoder) encodeOBJECT(inspector Inspector) {
	e.out.Write([]byte("{"))
	e.comma = false
	inspector.TraverseObject(e)
	e.out.Write([]byte("}"))
}
