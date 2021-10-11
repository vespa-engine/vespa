package vespa

import (
	"reflect"
	"testing"
)

func TestNewRankingFeaturesQuery(t *testing.T) {
	got := NewRankingFeaturesQuery()

	if got.Values == nil {
		t.Errorf("NewRankingFeaturesQuery = nil, want not nil")
	}
}

func TestRankingFeaturesQuery_Set(t *testing.T) {
	type fields struct {
		Values map[string]interface{}
	}
	type args struct {
		name  string
		value interface{}
	}
	tests := []struct {
		name   string
		fields fields
		args   args
		want   map[string]interface{}
	}{
		{
			name: "when setting a string we should get the value",
			fields: fields{
				Values: map[string]interface{}{},
			},
			args: args{
				name:  "test",
				value: "value",
			},
			want: map[string]interface{}{
				"test": "value",
			},
		},
		{
			name: "when setting a int we should get the value",
			fields: fields{
				Values: map[string]interface{}{},
			},
			args: args{
				name:  "test",
				value: 22,
			},
			want: map[string]interface{}{
				"test": 22,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rfq := &RankingFeaturesQuery{
				Values: tt.fields.Values,
			}
			rfq.Set(tt.args.name, tt.args.value)

			if !reflect.DeepEqual(tt.want, rfq.Values) {
				t.Errorf("RankingFeaturesQuery.Set() = %+v, want %+v", tt.want, rfq.Values)
			}
		})
	}
}
