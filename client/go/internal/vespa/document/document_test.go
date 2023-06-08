package document

import (
	"errors"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"
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
  "fields":    {  "foo"  : "123", "bar": {"a": [1, 2, 3]}}
}`,
		`

     {
  "put": "id:ns:type::doc2",
  "create": false,
  "condition": "foo",
  "fields": {"bar": "456"}
}`,
		`
{
  "remove": "id:ns:type::doc3"
}
`,
		`
{
  "fields": {"qux": "789"},
  "put": "id:ns:type::doc4",
  "create": true
}`,
		`
{
  "remove": "id:ns:type::doc5"
}`}
	if jsonl {
		return strings.Join(operations, "\n")
	}
	return "   \n[" + strings.Join(operations, ",") + "]"
}

func testDocumentDecoder(t *testing.T, jsonLike string) {
	t.Helper()
	dec := NewDecoder(strings.NewReader(jsonLike))
	docs := []Document{
		{Id: mustParseId("id:ns:type::doc1"), Operation: OperationPut, Body: []byte(`{"fields":{  "foo"  : "123", "bar": {"a": [1, 2, 3]}}}`)},
		{Id: mustParseId("id:ns:type::doc2"), Operation: OperationPut, Condition: "foo", Body: []byte(`{"fields":{"bar": "456"}}`)},
		{Id: mustParseId("id:ns:type::doc3"), Operation: OperationRemove},
		{Id: mustParseId("id:ns:type::doc4"), Operation: OperationPut, Create: true, Body: []byte(`{"fields":{"qux": "789"}}`)},
		{Id: mustParseId("id:ns:type::doc5"), Operation: OperationRemove},
	}
	result := []Document{}
	for {
		doc, err := dec.Decode()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		result = append(result, doc)
	}
	wantBufLen := 0
	if dec.array {
		wantBufLen = 1
	}
	if l := dec.buf.Len(); l != wantBufLen {
		t.Errorf("got dec.buf.Len() = %d, want %d", l, wantBufLen)
	}
	if len(docs) != len(result) {
		t.Errorf("len(result) = %d, want %d", len(result), len(docs))
	}
	for i := 0; i < len(docs); i++ {
		got := result[i]
		want := docs[i]
		if !got.Equal(want) {
			t.Errorf("got %+v, want %+v", got, want)
		}
	}
}

func TestDocumentDecoderArray(t *testing.T) { testDocumentDecoder(t, feedInput(false)) }

func TestDocumentDecoderJSONL(t *testing.T) { testDocumentDecoder(t, feedInput(true)) }

func TestDocumentDecoderInvalid(t *testing.T) {
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
	dec := NewDecoder(strings.NewReader(jsonLike))
	_, err := dec.Decode() // first object is valid
	if err != nil {
		t.Errorf("unexpected error: %s", err)
	}
	_, err = dec.Decode()
	wantErr := "invalid operation at byte offset 110: json: invalid character '\\n' within string (expecting non-control character)"
	if err.Error() != wantErr {
		t.Errorf("want error %q, got %q", wantErr, err.Error())
	}

	dec = NewDecoder(strings.NewReader(`{}`))
	_, err = dec.Decode()
	wantErr = "invalid operation at byte offset 2: no id specified"
	if !errors.Is(err, ErrMissingId) {
		t.Errorf("want error %q, got %q", ErrMissingId, err.Error())
	}
}

func benchmarkDocumentDecoder(b *testing.B, size int) {
	b.Helper()
	input := fmt.Sprintf(`{"put": "id:ns:type::doc1", "fields": {"foo": "%s"}}`, strings.Repeat("s", size))
	r := strings.NewReader(input)
	dec := NewDecoder(r)
	b.ResetTimer() // ignore setup time

	for n := 0; n < b.N; n++ {
		_, err := dec.Decode()
		if err != nil {
			b.Fatal(err)
		}
		r.Seek(0, 0)
	}
}

func BenchmarkDocumentDecoderSmall(b *testing.B) { benchmarkDocumentDecoder(b, 10) }

func BenchmarkDocumentDecoderLarge(b *testing.B) { benchmarkDocumentDecoder(b, 10000) }

func TestGenerator(t *testing.T) {
	now := time.Now()
	steps := 10
	step := time.Second
	gen := Generator{
		Size:     10,
		Deadline: now.Add(time.Duration(steps) * step),
		nowFunc:  func() time.Time { return now },
	}
	dec := NewDecoder(&gen)
	var docs []Document
	for {
		doc, err := dec.Decode()
		if err == io.EOF {
			break
		} else if err != nil {
			t.Fatal(err)
		}
		docs = append(docs, doc)
		now = now.Add(step)
	}
	if got := len(docs); got != steps {
		t.Errorf("got %d docs, want %d", got, steps)
	}
}
