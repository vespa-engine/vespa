package vespa

import (
	"reflect"
	"testing"
)

func TestNewPartialUpdateField(t *testing.T) {
	type args struct {
		key   string
		value interface{}
	}
	tests := []struct {
		name string
		args args
		want *PartialUpdateField
	}{
		{
			name: "when called I should get the expected result",
			args: args{
				key:   "key",
				value: "value",
			},
			want: &PartialUpdateField{
				Key:   "key",
				Value: "value",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := NewPartialUpdateField(tt.args.key, tt.args.value); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("NewPartialUpdateField() = %v, want %v", got, tt.want)
			}
		})
	}
}
