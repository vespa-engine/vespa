package document

import (
	"io"
	"reflect"
	"strings"
	"testing"
)

func ptr[T any](v T) *T { return &v }

func mustParseId(s string) Id {
	id, err := ParseId(s)
	if err != nil {
		panic(err)
	}
	return id
}

func TestParseId(t *testing.T) {
	tests := []struct {
		in   string
		out  Id
		fail bool
	}{
		{"id:ns:type::user",
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type:n=123:user",
			Id{
				Namespace:    "ns",
				Type:         "type",
				Number:       ptr(int64(123)),
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type:g=foo:user",
			Id{
				Namespace:    "ns",
				Type:         "type",
				Group:        "foo",
				UserSpecific: "user",
			},
			false,
		},
		{"id:ns:type::user::specific",
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "user::specific",
			},
			false,
		},
		{"id:ns:type:::",
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: ":",
			},
			false,
		},
		{"id:ns:type::n=user-specific",
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "n=user-specific",
			},
			false,
		},
		{"id:ns:type::g=user-specific",
			Id{
				Namespace:    "ns",
				Type:         "type",
				UserSpecific: "g=user-specific",
			},
			false,
		},
		{"", Id{}, true},
		{"foobar", Id{}, true},
		{"idd:ns:type:user", Id{}, true},
		{"id:ns::user", Id{}, true},
		{"id::type:user", Id{}, true},
		{"id:ns:type:g=:user", Id{}, true},
		{"id:ns:type:n=:user", Id{}, true},
		{"id:ns:type:n=foo:user", Id{}, true},
		{"id:ns:type::", Id{}, true},
		{"id:ns:type:user-specific:foo-bar", Id{}, true},
	}
	for i, tt := range tests {
		parsed, err := ParseId(tt.in)
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
		{Id: mustParseId("id:ns:type::doc1"), Operation: OperationPut, Body: []byte(`{"fields":{"foo": "123"}}`)},
		{Id: mustParseId("id:ns:type::doc2"), Operation: OperationPut, Body: []byte(`{"fields":{"bar": "456"}}`)},
		{Id: mustParseId("id:ns:type::doc1"), Operation: OperationRemove},
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

	jsonLike := `
{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "123"}
}
{
  "put": "id:ns:type::doc1",
  "fields": {"foo": "invalid
}
`
	r := NewDecoder(strings.NewReader(jsonLike))
	_, err := r.Decode() // first object is valid
	if err != nil {
		t.Errorf("unexpected error: %s", err)
	}
	_, err = r.Decode()
	wantErr := "invalid json at byte offset 60: invalid character '\\n' in string literal"
	if err.Error() != wantErr {
		t.Errorf("want error %q, got %q", wantErr, err.Error())
	}
}
