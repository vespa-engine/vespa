package feed

import (
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
