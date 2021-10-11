package vespa

import (
	"net/http"
	"net/url"
	"reflect"
	"testing"
)

func TestNewUpdateBatchRequest(t *testing.T) {
	want := &UpdateBatchRequest{}
	got := NewUpdateBatchRequest()

	if !reflect.DeepEqual(want, got) {
		t.Errorf("NewUpdateBatchRequest() = %v, want %v", got, want)
	}
}

func TestUpdateBatchRequest_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *UpdateBatchRequest
	}{
		{
			name: "Set a default value",
			args: args{
				name: "default",
			},
			want: &UpdateBatchRequest{
				namespace: "default",
			},
		},
		{
			name: "Set a specific value",
			args: args{
				name: "specific",
			},
			want: &UpdateBatchRequest{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{}

			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestUpdateBatchRequest_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *UpdateBatchRequest
	}{
		{
			name: "Set cars value",
			args: args{
				scheme: "cars",
			},
			want: &UpdateBatchRequest{
				scheme: "cars",
			},
		},
		{
			name: "Set places value",
			args: args{
				scheme: "places",
			},
			want: &UpdateBatchRequest{
				scheme: "places",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{}

			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestUpdateBatchRequest_ID(t *testing.T) {
	type args struct {
		id string
	}
	tests := []struct {
		name string
		args args
		want *UpdateBatchRequest
	}{
		{
			name: "Set an id value",
			args: args{
				id: "aaaa",
			},
			want: &UpdateBatchRequest{
				id: "aaaa",
			},
		},
		{
			name: "Set a different id value",
			args: args{
				id: "bbb55",
			},
			want: &UpdateBatchRequest{
				id: "bbb55",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{}

			if got := s.ID(tt.args.id); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.ID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestUpdateBatchRequest_Field(t *testing.T) {
	type args struct {
		key   string
		value interface{}
	}
	tests := []struct {
		name string
		args args
		want *UpdateBatchRequest
	}{
		{
			name: "when assigning a field, we should have data in the data field",
			args: args{
				key:   "key",
				value: "value",
			},
			want: &UpdateBatchRequest{
				data: map[string]PartialUpdateItem{
					"key": {
						Assign: "value",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{}

			if got := s.Field(tt.args.key, tt.args.value); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.Field() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestUpdateBatchRequest_Fields(t *testing.T) {
	type args struct {
		items []PartialUpdateField
	}
	tests := []struct {
		name string
		args args
		want *UpdateBatchRequest
	}{
		{
			name: "when assigning a field, we should have data in the data field",
			args: args{
				items: []PartialUpdateField{
					{Key: "key", Value: "value"},
				},
			},
			want: &UpdateBatchRequest{
				data: map[string]PartialUpdateItem{
					"key": {
						Assign: "value",
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{}

			if got := s.Fields(tt.args.items...); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.Fields() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestUpdateBatchRequest_Validate(t *testing.T) {
	type fields struct {
		BatchableRequest BatchableRequest
		namespace        string
		scheme           string
		id               string
		data             map[string]PartialUpdateItem
		headers          http.Header
	}
	tests := []struct {
		name    string
		fields  fields
		wantErr bool
	}{
		{
			name:    "Missing required fields should return error",
			fields:  fields{},
			wantErr: true,
		},
		{
			name: "Missing one required field should return error",
			fields: fields{
				scheme: "posts",
				data: map[string]PartialUpdateItem{
					"key": {Assign: "value"},
				},
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "all fields set should return error nil",
			fields: fields{
				id:     "1",
				scheme: "posts",
				data: map[string]PartialUpdateItem{
					"key": {Assign: "value"},
				},
				namespace: "default",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{
				BatchableRequest: tt.fields.BatchableRequest,
				namespace:        tt.fields.namespace,
				scheme:           tt.fields.scheme,
				id:               tt.fields.id,
				data:             tt.fields.data,
				headers:          tt.fields.headers,
			}
			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("UpdateBatchRequest.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestUpdateBatchRequest_BuildURL(t *testing.T) {
	type fields struct {
		BatchableRequest BatchableRequest
		namespace        string
		scheme           string
		id               string
		data             map[string]PartialUpdateItem
		headers          http.Header
	}
	tests := []struct {
		name   string
		fields fields
		want   string
		want1  string
		want2  url.Values
	}{
		{
			name: "when called, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				id:        "aaa",
			},
			want:  "PUT",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{},
		},
		{
			name: "when called with timeout, it should return the expected values",
			fields: fields{
				namespace: "default",
				scheme:    "posts",
				id:        "aaa",
			},
			want:  "PUT",
			want1: "/document/v1/default/posts/docid/aaa",
			want2: url.Values{},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{
				BatchableRequest: tt.fields.BatchableRequest,
				namespace:        tt.fields.namespace,
				scheme:           tt.fields.scheme,
				id:               tt.fields.id,
				data:             tt.fields.data,
				headers:          tt.fields.headers,
			}
			got, got1, got2 := s.BuildURL()
			if got != tt.want {
				t.Errorf("UpdateBatchRequest.BuildURL() got = %v, want %v", got, tt.want)
			}
			if got1 != tt.want1 {
				t.Errorf("UpdateBatchRequest.BuildURL() got1 = %v, want %v", got1, tt.want1)
			}
			if !reflect.DeepEqual(got2, tt.want2) {
				t.Errorf("UpdateBatchRequest.BuildURL() got2 = %v, want %v", got2, tt.want2)
			}
		})
	}
}

func TestUpdateBatchRequest_Source(t *testing.T) {
	data := map[string]PartialUpdateItem{
		"id": {Assign: "aaa"},
	}

	type fields struct {
		data map[string]PartialUpdateItem
	}
	tests := []struct {
		name   string
		fields fields
		want   interface{}
	}{
		{
			name: "when called, the fields should be under a fields key",
			fields: fields{
				data: data,
			},
			want: map[string]interface{}{
				"fields": data,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &UpdateBatchRequest{
				data: tt.fields.data,
			}

			if got := s.Source(); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("UpdateBatchRequest.Source() = %v, want %v", got, tt.want)
			}
		})
	}
}
