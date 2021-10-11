package vespa

import (
	"reflect"
	"testing"
)

func TestNewWeakAnd(t *testing.T) {
	want := &WeakAnd{
		Hits: 10,
	}

	got := NewWeakAnd(10)

	if !reflect.DeepEqual(want, got) {
		t.Errorf("NewCreateService() =  %+v, want %+v", got, want)
	}
}

func TestWeakAnd_Source(t *testing.T) {
	type fields struct {
		hits    int
		entries []WeakAndEntry
	}
	tests := []struct {
		name   string
		fields fields
		want   string
	}{
		{
			name: "source should form the right output with 2 entries",
			fields: fields{
				hits: 10,
				entries: []WeakAndEntry{
					{Field: "title", Value: "software"},
					{Field: "description", Value: "engineer"},
				},
			},
			want: `([{"targetHits":10}] weakAnd(title contains "software", description contains "engineer"))`,
		},
		{
			name: "source should form the right output with 3 entries",
			fields: fields{
				hits: 10,
				entries: []WeakAndEntry{
					{Field: "default", Value: "software"},
					{Field: "default", Value: "engineer"},
					{Field: "default", Value: "senior"},
				},
			},
			want: `([{"targetHits":10}] weakAnd(default contains "software", default contains "engineer", default contains "senior"))`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			wa := &WeakAnd{
				Hits:    tt.fields.hits,
				Entries: tt.fields.entries,
			}
			if got := wa.Source(); got != tt.want {
				t.Errorf("WeakAnd.Source() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestWeakAnd_AddEntry(t *testing.T) {
	type fields struct {
		Hits    int
		Entries []WeakAndEntry
	}
	type args struct {
		field string
		value string
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   *WeakAnd
	}{
		{
			name: "Adding a new entry should add to the entries array",
			fields: fields{
				Hits: 10,
			},
			args: args{
				field: "default",
				value: "software",
			},
			want: &WeakAnd{
				Hits: 10,
				Entries: []WeakAndEntry{
					{Field: "default", Value: "software"},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			wa := &WeakAnd{
				Hits:    tt.fields.Hits,
				Entries: tt.fields.Entries,
			}
			wa.AddEntry(tt.args.field, tt.args.value)
		})
	}
}
