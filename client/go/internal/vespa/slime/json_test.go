// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package slime

import (
	"errors"
	"github.com/stretchr/testify/assert"
	"math"
	"strings"
	"testing"
)

func checkJson(t *testing.T, value Value, compact bool, expect ...string) {
	actual := ToJson(value, compact)
	if len(expect) == 1 {
		assert.Equal(t, expect[0], actual)
	} else {
		for _, e := range expect {
			if actual == e {
				return
			}
		}
		t.Errorf("result '%s' did not match expectations: \n%s\n",
			actual, strings.Join(expect, "\n OR \n"))
	}
}

type verifyValue func(t *testing.T, actual Value)

func verifyInvalid() verifyValue {
	return func(t *testing.T, actual Value) {
		assert.False(t, actual.Valid())
		assert.Equal(t, EMPTY, actual.Type())
	}
}

func verifyEmpty() verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, EMPTY, actual.Type())
	}
}

func verifyBool(expect bool) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, BOOL, actual.Type())
		assert.Equal(t, expect, actual.AsBool())
	}
}

func verifyLong(expect int64) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, LONG, actual.Type())
		assert.Equal(t, expect, actual.AsLong())
		assert.Equal(t, float64(expect), actual.AsDouble())
	}
}

func verifyDouble(expect float64) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, DOUBLE, actual.Type())
		assert.Equal(t, expect, actual.AsDouble())
		assert.Equal(t, int64(expect), actual.AsLong())
	}
}

func verifyString(expect string) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, STRING, actual.Type())
		assert.Equal(t, expect, actual.AsString())
	}
}

func verifyData(expect []byte) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, DATA, actual.Type())
		assert.Equal(t, expect, actual.AsData())
	}
}

func verifyArray(expect []verifyValue) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, ARRAY, actual.Type())
		assert.Equal(t, len(expect), actual.NumEntries())
		for i, e := range expect {
			e(t, actual.Entry(i))
		}
	}
}

func verifyObject(expect map[string]verifyValue) verifyValue {
	return func(t *testing.T, actual Value) {
		assert.True(t, actual.Valid())
		assert.Equal(t, OBJECT, actual.Type())
		assert.Equal(t, len(expect), actual.NumFields())
		for n, e := range expect {
			e(t, actual.Field(n))
		}
	}
}

func verifyFromValue(value Value) verifyValue {
	switch value.Type() {
	case EMPTY:
		return verifyEmpty()
	case BOOL:
		return verifyBool(value.AsBool())
	case LONG:
		return verifyLong(value.AsLong())
	case DOUBLE:
		return verifyDouble(value.AsDouble())
	case STRING:
		return verifyString(value.AsString())
	case DATA:
		return verifyData(value.AsData())
	case ARRAY:
		var entries []verifyValue
		value.EachEntry(func(idx int, val Value) {
			entries = append(entries, verifyFromValue(val))
		})
		return verifyArray(entries)
	case OBJECT:
		fields := make(map[string]verifyValue)
		value.EachField(func(name string, val Value) {
			fields[name] = verifyFromValue(val)
		})
		return verifyObject(fields)
	}
	return func(t *testing.T, actual Value) {
		assert.Fail(t, "verifyValue using non-existing type")
	}
}

func verifyFromJson(json string) verifyValue {
	expect := FromJson(json)
	if !expect.Valid() {
		return func(t *testing.T, actual Value) {
			assert.Fail(t, "verifyValue created from invalid json")
		}
	}
	return verifyFromValue(expect)
}

func TestJsonEncodeEmpty(t *testing.T) {
	checkJson(t, Empty, true, "null")
	checkJson(t, Invalid, true, "null")
	checkJson(t, Empty, false, "null\n")
}

func TestJsonEncodeBool(t *testing.T) {
	checkJson(t, Bool(false), true, "false")
	checkJson(t, Bool(true), true, "true")
	checkJson(t, Bool(false), false, "false\n")
}

func TestJsonEncodeLong(t *testing.T) {
	checkJson(t, Long(0), true, "0")
	checkJson(t, Long(5), true, "5")
	checkJson(t, Long(7), true, "7")
	checkJson(t, Long(7), false, "7\n")
}

func TestJsonEncodeDouble(t *testing.T) {
	checkJson(t, Double(0.0), true, "0")
	checkJson(t, Double(5.0), true, "5")
	checkJson(t, Double(7.5), true, "7.5")
	checkJson(t, Double(math.NaN()), true, "null")
	checkJson(t, Double(math.Inf(1)), true, "null")
	checkJson(t, Double(math.Inf(-1)), true, "null")
	checkJson(t, Double(7.5), false, "7.5\n")
}

func TestJsonEncodeString(t *testing.T) {
	checkJson(t, String(""), true, "\"\"")
	checkJson(t, String("foo"), true, "\"foo\"")
	checkJson(t, String("bar"), true, "\"bar\"")
	checkJson(t, String("\""), true, "\"\\\"\"")
	checkJson(t, String("\\"), true, "\"\\\\\"")
	checkJson(t, String("\b"), true, "\"\\b\"")
	checkJson(t, String("\f"), true, "\"\\f\"")
	checkJson(t, String("\n"), true, "\"\\n\"")
	checkJson(t, String("\r"), true, "\"\\r\"")
	checkJson(t, String("\t"), true, "\"\\t\"")
	checkJson(t, String("\x1f"), true, "\"\\u001F\"")
	checkJson(t, String("\x20"), true, "\" \"")
	checkJson(t, String("%"), true, "\"%\"")
	checkJson(t, String("bar"), false, "\"bar\"\n")
}

func TestJsonEncodeData(t *testing.T) {
	checkJson(t, Data(emptyBytes), true, "\"0x\"")
	buf := make([]byte, 8)
	for i := 0; i < 8; i++ {
		buf[i] = byte(((i * 2) << 4) | (i*2 + 1))
	}
	checkJson(t, Data(buf), true, "\"0x0123456789ABCDEF\"")
	checkJson(t, Data(buf), false, "\"0x0123456789ABCDEF\"\n")
}

func TestJsonEncodeArray(t *testing.T) {
	arr := Array()
	arr.Add(Bool(true))
	arr.Add(String("foo"))
	checkJson(t, arr, true, "[true,\"foo\"]")
	checkJson(t, arr, false, "[\n    true,\n    \"foo\"\n]\n")
}

func TestJsonEncodeObject(t *testing.T) {
	obj := Object()
	obj.Set("foo", Bool(true))
	obj.Set("bar", String("foo"))
	checkJson(t, obj, true, "{\"foo\":true,\"bar\":\"foo\"}",
		"{\"bar\":\"foo\",\"foo\":true}")
	checkJson(t, obj, false, "{\n    \"foo\": true,\n    \"bar\": \"foo\"\n}\n",
		"{\n    \"bar\": \"foo\",\n    \"foo\": true\n}\n")
}

func TestJsonEncodeNesting(t *testing.T) {
	obj := Object()
	arr1 := obj.Set("foo", Array())
	arr2 := arr1.Add(Array())
	arr1.Add(Long(3))
	arr2.Add(Long(5))
	arr2.Add(Long(7))
	checkJson(t, obj, true, "{\"foo\":[[5,7],3]}")
	checkJson(t, obj, false, "{\n"+
		"    \"foo\": [\n"+
		"        [\n"+
		"            5,\n"+
		"            7\n"+
		"        ],\n"+
		"        3\n"+
		"    ]\n"+
		"}\n")
}

type brokenWriter struct{}

func (*brokenWriter) Write(p []byte) (n int, err error) {
	return 0, errors.New("bad writer")
}

func TestJsonEncodeErrorPropagation(t *testing.T) {
	err := EncodeJson(Bool(true), true, &brokenWriter{})
	assert.Equal(t, "bad writer", err.Error())
}

func TestJsonDecodeNull(t *testing.T) {
	verifyEmpty()(t, FromJson("null"))
}

func TestJsonDecodeBool(t *testing.T) {
	verifyBool(false)(t, FromJson("false"))
	verifyBool(true)(t, FromJson("true"))
}

func TestJsonDecodeNumber(t *testing.T) {
	verifyLong(0)(t, FromJson("0"))
	verifyLong(1)(t, FromJson("1"))
	verifyLong(2)(t, FromJson("2"))
	verifyLong(3)(t, FromJson("3"))
	verifyLong(4)(t, FromJson("4"))
	verifyLong(5)(t, FromJson("5"))
	verifyLong(6)(t, FromJson("6"))
	verifyLong(7)(t, FromJson("7"))
	verifyLong(8)(t, FromJson("8"))
	verifyLong(9)(t, FromJson("9"))
	verifyLong(-9)(t, FromJson("-9"))
	verifyDouble(5.5)(t, FromJson("5.5"))
	verifyDouble(5e7)(t, FromJson("5e7"))
	verifyLong(9223372036854775807)(t, FromJson("9223372036854775807"))
}

func json_string(str string) string {
	val := FromJson("\"" + str + "\"")
	if !val.Valid() {
		return "<invalid>"
	}
	return val.AsString()
}

func TestJsonDecodeString(t *testing.T) {
	assert.Equal(t, "", json_string(""))
	assert.Equal(t, "foo", json_string("foo"))
	assert.Equal(t, "\"", json_string("\\\""))
	assert.Equal(t, "\b", json_string("\\b"))
	assert.Equal(t, "\f", json_string("\\f"))
	assert.Equal(t, "\n", json_string("\\n"))
	assert.Equal(t, "\r", json_string("\\r"))
	assert.Equal(t, "\t", json_string("\\t"))
	assert.Equal(t, "A", json_string("\\u0041"))
	assert.Equal(t, "\x0f", json_string("\\u000f"))
	assert.Equal(t, "\x18", json_string("\\u0018"))
	assert.Equal(t, "\x29", json_string("\\u0029"))
	assert.Equal(t, "\x3a", json_string("\\u003a"))
	assert.Equal(t, "\x4b", json_string("\\u004b"))
	assert.Equal(t, "\x5c", json_string("\\u005c"))
	assert.Equal(t, "\x6d", json_string("\\u006d"))
	assert.Equal(t, "\x7e", json_string("\\u007e"))
	assert.Equal(t, "\x7f", json_string("\\u007f"))
	assert.Equal(t, "\xc2\x80", json_string("\\u0080"))
	assert.Equal(t, "\xdf\xbf", json_string("\\u07ff"))
	assert.Equal(t, "\xe0\xa0\x80", json_string("\\u0800"))
	assert.Equal(t, "\xed\x9f\xbf", json_string("\\ud7ff"))
	assert.Equal(t, "\xee\x80\x80", json_string("\\ue000"))
	assert.Equal(t, "\xef\xbf\xbf", json_string("\\uffff"))
	assert.Equal(t, "\xf0\x90\x80\x80", json_string("\\ud800\\udc00"))
	assert.Equal(t, "\xf4\x8f\xbf\xbf", json_string("\\udbff\\udfff"))
}

func TestJsonDecodeData(t *testing.T) {
	verifyData([]byte{})(t, FromJson("x"))
	verifyData([]byte{0, 0})(t, FromJson("x0000"))
	verifyData([]byte{0x12, 0x34, 0x56, 0x78, 0x90, 0xab, 0xcd, 0xef, 0xab, 0xcd, 0xef})(t, FromJson("x1234567890abcdefABCDEF"))
}

func TestJsonDecodeArray(t *testing.T) {
	verifyArray([]verifyValue{})(t, FromJson("[]"))
	verify := verifyArray([]verifyValue{
		verifyLong(123),
		verifyDouble(0.5),
		verifyString("foo"),
		verifyBool(true),
	})
	verify(t, FromJson("[123,0.5,\"foo\",true]"))
}

func TestJsonDecodeObject(t *testing.T) {
	verifyObject(map[string]verifyValue{})(t, FromJson("{}"))
	verify := verifyObject(map[string]verifyValue{
		"a": verifyLong(123),
		"b": verifyDouble(0.5),
		"c": verifyString("foo"),
		"d": verifyBool(true),
		"e": verifyData([]byte{0xff, 0x00, 0x11}),
	})
	verify(t, FromJson("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true,\"e\":xff0011}"))
}

func TestJsonDecodeNested(t *testing.T) {
	verify := verifyObject(map[string]verifyValue{
		"a": verifyObject(map[string]verifyValue{
			"b": verifyArray([]verifyValue{
				verifyArray([]verifyValue{
					verifyLong(1),
					verifyLong(2),
					verifyLong(3),
				}),
			}),
			"c": verifyArray([]verifyValue{
				verifyArray([]verifyValue{
					verifyLong(4),
				}),
			}),
		}),
	})
	verify(t, FromJson("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}"))
}

func TestJsonDecodeWhitespace(t *testing.T) {
	verifyFromJson("true")(t, FromJson("\n\r\t true"))
	verifyFromJson("true")(t, FromJson(" true "))
	verifyFromJson("false")(t, FromJson(" false "))
	verifyFromJson("null")(t, FromJson(" null "))
	verifyFromJson("\"foo\"")(t, FromJson(" \"foo\" "))
	verifyFromJson("{}")(t, FromJson(" { } "))
	verifyFromJson("[]")(t, FromJson(" [ ] "))
	verifyFromJson("5")(t, FromJson(" 5 "))
	verifyFromJson("[1]")(t, FromJson(" [ 1 ] "))
	verifyFromJson("[1,2,3]")(t, FromJson(" [ 1 , 2 , 3 ] "))
	verifyFromJson("{\"a\":1}")(t, FromJson(" { \"a\" : 1 } "))
	verifyFromJson("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}")(t,
		FromJson(" { \"a\" : { \"b\" : [ [ 1 , 2 , 3 ] ] , \"c\" : [ [ 4 ] ] } } "))
}

func TestJsonDecodeInvalidInput(t *testing.T) {
	verifyInvalid()(t, FromJson(""))
	verifyInvalid()(t, FromJson("["))
	verifyInvalid()(t, FromJson("{"))
	verifyInvalid()(t, FromJson("]"))
	verifyInvalid()(t, FromJson("}"))
	verifyInvalid()(t, FromJson("{]"))
	verifyInvalid()(t, FromJson("[}"))
	verifyInvalid()(t, FromJson("+5"))
	verifyInvalid()(t, FromJson("fals"))
	verifyInvalid()(t, FromJson("tru"))
	verifyInvalid()(t, FromJson("nul"))
	verifyInvalid()(t, FromJson("bar"))
	verifyInvalid()(t, FromJson("\"bar"))
	verifyInvalid()(t, FromJson("bar\""))
	verifyInvalid()(t, FromJson("'bar\""))
	verifyInvalid()(t, FromJson("\"bar'"))
	verifyInvalid()(t, FromJson("{\"foo"))
}

func TestJsonDecodeSimplifiedForm(t *testing.T) {
	verifyFromJson("\"foo\"")(t, FromJson("'foo'"))
	verifyFromJson("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true}")(t, FromJson("{a:123,b:0.5,c:'foo',d:true}"))
	verifyFromJson("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}")(t, FromJson("{a:{b:[[1,2,3]],c:[[4]]}}"))
}

func TestJsonDecodeMultipleValues(t *testing.T) {
	data := "true {} false [] null \"foo\" 'bar' 1.5 null"
	input := strings.NewReader(data)
	verifyFromJson("true")(t, DecodeJson(input))
	verifyFromJson("{}")(t, DecodeJson(input))
	verifyFromJson("false")(t, DecodeJson(input))
	verifyFromJson("[]")(t, DecodeJson(input))
	verifyFromJson("null")(t, DecodeJson(input))
	verifyFromJson("\"foo\"")(t, DecodeJson(input))
	verifyFromJson("\"bar\"")(t, DecodeJson(input))
	verifyFromJson("1.5")(t, DecodeJson(input))
	verifyFromJson("null")(t, DecodeJson(input))
	// Note that one extra byte is always looked at when parsing
	// json. Since io.Reader does not support peek/unread this
	// byte will be lost
	bad_data := "true{}"
	bad_input := strings.NewReader(bad_data)
	verifyFromJson("true")(t, DecodeJson(bad_input))
	verifyInvalid()(t, DecodeJson(bad_input))
}
