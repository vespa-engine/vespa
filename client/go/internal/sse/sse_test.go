package sse

import (
	"errors"
	"io"
	"strings"
	"testing"
)

func TestDecoder(t *testing.T) {
	r := strings.NewReader(`
event: foo
id: 42
data: data 1

event: bar
ignored
: ignored
data: data 2


event: baz
data: data 3
data: data 4

event: bax
data: data 5

data: data 6

event: end
`)
	dec := NewDecoder(r)

	assertDecode(&Event{Name: "foo", ID: "42", Data: "data 1"}, dec, t)
	assertDecode(&Event{Name: "bar", Data: "data 2"}, dec, t)
	assertDecode(&Event{Name: "baz", Data: "data 3 data 4"}, dec, t)
	assertDecode(&Event{Name: "bax", Data: "data 5"}, dec, t)
	assertDecode(&Event{Data: "data 6"}, dec, t)
	assertDecode(&Event{Name: "end"}, dec, t)
	assertDecodeErr(io.EOF, dec, t)
}

func TestDecoderLarge(t *testing.T) {
	data := strings.Repeat("c", (256*1024)-50)
	r := strings.NewReader("event: foo\nid: 42\ndata: " + data + "\n")
	dec := NewDecoderSize(r, 256*1024)
	assertDecode(&Event{Name: "foo", ID: "42", Data: data}, dec, t)
}

func TestDecoderInvalid(t *testing.T) {
	r := strings.NewReader(`
event: foo
event: bar

event: foo
id: 42

foo

bad: field
`)
	dec := NewDecoder(r)
	assertDecodeErrString(`got more than one event line: last read "event: bar"`, dec, t)
	assertDecodeErrString(`no data line found for event: last read "id: 42"`, dec, t)
	assertDecodeErrString(`no data line found for event: last read "foo"`, dec, t)
	assertDecodeErrString(`invalid field name "bad": last read "bad: field"`, dec, t)
}

func TestString(t *testing.T) {
	assertString(t, "event: foo\ndata: bar\n", Event{Name: "foo", Data: "bar"})
	assertString(t, "event: foo\nid: 42\ndata: bar\n", Event{Name: "foo", ID: "42", Data: "bar"})
}

func assertString(t *testing.T, want string, event Event) {
	t.Helper()
	got := event.String()
	if got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func assertDecode(want *Event, dec *Decoder, t *testing.T) {
	t.Helper()
	got, err := dec.Decode()
	if err != nil {
		t.Fatalf("got error %v, want %+v", err, want)
	}
	if got.Name != want.Name {
		t.Errorf("got Name=%q, want %q", got.Name, want.Name)
	}
	if got.ID != want.ID {
		t.Errorf("got ID=%q, want %q", got.ID, want.ID)
	}
	if got.Data != want.Data {
		t.Errorf("got Data=%q, want %q", got.Data, want.Data)
	}
}

func assertDecodeErrString(errMsg string, dec *Decoder, t *testing.T) {
	t.Helper()
	assertDecodeErr(errors.New(errMsg), dec, t)
}

func assertDecodeErr(wantErr error, dec *Decoder, t *testing.T) {
	t.Helper()
	_, err := dec.Decode()
	if err == nil {
		t.Fatal("expected error")
	}
	if err.Error() != wantErr.Error() {
		t.Errorf("got error %q, want %q", err, wantErr)
	}
}
