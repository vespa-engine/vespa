package feed

import (
	"encoding/json"
	"io"
	"reflect"
	"strings"
	"testing"
)

func ptr[T any](v T) *T { return &v }

func TestParseDocumentId(t *testing.T) {
	tests := []struct {
		in   string
		out  DocumentId
		fail bool
	}{
		{"id:ns:type::user",
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type:n=123:user",
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				Number:       ptr(int64(123)),
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type:g=foo:user",
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				Group:        "foo",
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type::user::specific",
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user::specific",
			},
			false,
		},
		{"id:ns:type:::",
			DocumentId{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: ":",
			},
			false,
		},
		{"", DocumentId{}, true},
		{"foobar", DocumentId{}, true},
		{"idd:ns:type:user", DocumentId{}, true},
		{"id:ns::user", DocumentId{}, true},
		{"id::type:user", DocumentId{}, true},
		{"id:ns:type:g=:user", DocumentId{}, true},
		{"id:ns:type:n=:user", DocumentId{}, true},
		{"id:ns:type:n=foo:user", DocumentId{}, true},
		{"id:ns:type::", DocumentId{}, true},
	}
	for i, tt := range tests {
		parsed, err := ParseDocumentId(tt.in)
		if err == nil && tt.fail {
			t.Errorf("#%d: expected error for ParseDocumentId(%q), but got none", i, tt.in)
		}
		if err != nil && !tt.fail {
			t.Errorf("#%d: got unexpected error for ParseDocumentId(%q) = (_, %v)", i, tt.in, err)
		}
		if !parsed.Equal(tt.out) {
			t.Errorf("#%d: ParseDocumentId(%q) = (%s, _), want %s", i, tt.in, parsed, tt.out)
		}
	}
}

func feedInput(jsonl bool) string {
	operations := []string{
		`
{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "123"}
}`,
		`
{
  "put": "id:ns:type::doc2",
  "fields": {"bar": "456"}
}`,
		`
{
  "remove": "id:ns:type::doc1"
}
`}
	if jsonl {
		return strings.Join(operations, "\n")
	}
	return "   \n[" + strings.Join(operations, ",") + "]"
}

func testDocumentDecoder(t *testing.T, jsonLike string) {
	t.Helper()
	r := NewDecoder(strings.NewReader(jsonLike))
	want := []Document{
		{PutId: "id:ns:type::doc1", Fields: json.RawMessage(`{"foo": "123"}`)},
		{PutId: "id:ns:type::doc2", Fields: json.RawMessage(`{"bar": "456"}`)},
		{RemoveId: "id:ns:type::doc1", Fields: json.RawMessage(nil)},
	}
	got := []Document{}
	for {
		doc, err := r.Decode()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		got = append(got, doc)
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %+v, want %+v", got, want)
	}
}

func TestDocumentDecoder(t *testing.T) {
	testDocumentDecoder(t, feedInput(false))
	testDocumentDecoder(t, feedInput(true))
}
