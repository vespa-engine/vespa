package vespa

import (
	"net/http"
	"reflect"
	"testing"
)

func TestNewCreateBatchRequest(t *testing.T) {

	want := &CreateBatchRequest{}
	got := NewCreateBatchRequest()

	if !reflect.DeepEqual(want, got) {
		t.Errorf(" expected NewCreateBatchRequest() to return %+v, got %+v", want, got)
	}
}

func TestNewCreateBatchRequest_Namespace(t *testing.T) {
	type args struct {
		name string
	}
	tests := []struct {
		name string
		args args
		want *CreateBatchRequest
	}{
		{
			name: " with default value",
			args: args{
				name: "default",
			},
			want: &CreateBatchRequest{
				namespace: "default",
			},
		},
		{
			name: " with specific value",
			args: args{
				name: "specific",
			},
			want: &CreateBatchRequest{
				namespace: "specific",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateBatchRequest{}
			if got := s.Namespace(tt.args.name); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("NewCreateBatchRequest.Namespace() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateBatchRequest_Scheme(t *testing.T) {
	type args struct {
		scheme string
	}
	tests := []struct {
		name string
		args args
		want *CreateBatchRequest
	}{
		{
			name: " with cars value",
			args: args{
				scheme: "cars",
			},
			want: &CreateBatchRequest{
				scheme: "cars",
			},
		},
		{
			name: " with places value",
			args: args{
				scheme: "places",
			},
			want: &CreateBatchRequest{
				scheme: "places",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateBatchRequest{}
			if got := s.Scheme(tt.args.scheme); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateBatchRequest.Scheme() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateBatchRequest_ID(t *testing.T) {

	type args struct {
		id string
	}
	tests := []struct {
		name string
		args args
		want *CreateBatchRequest
	}{
		{
			name: " with an id value value",
			args: args{
				id: "aaaa",
			},
			want: &CreateBatchRequest{
				id: "aaaa",
			},
		},
		{
			name: " with another id value",
			args: args{
				id: "bbbb",
			},
			want: &CreateBatchRequest{
				id: "bbbb",
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateBatchRequest{}
			if got := s.ID(tt.args.id); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateBatchRequest.ID() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateBatchRequest_Body(t *testing.T) {

	type args struct {
		body interface{}
	}

	object := struct {
		field string
	}{
		field: "value",
	}

	tests := []struct {
		name string
		args args
		want *CreateBatchRequest
	}{
		{
			name: " with an string value",
			args: args{
				body: "string",
			},
			want: &CreateBatchRequest{
				data: "string",
			},
		},
		{
			name: " with int value",
			args: args{
				body: 23,
			},
			want: &CreateBatchRequest{
				data: 23,
			},
		},
		{
			name: " with struct value",
			args: args{
				body: object,
			},
			want: &CreateBatchRequest{
				data: object,
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateBatchRequest{}
			if got := s.Body(tt.args.body); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("CreateBatchRequest.Body() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestCreateBatchRequest_Validate(t *testing.T) {
	type fields struct {
		namespace string
		scheme    string
		id        string
		data      interface{}

		headers http.Header
	}
	tests := []struct {
		name    string
		fields  fields
		wantErr bool
	}{
		{
			name:    "missing required fields should return error",
			fields:  fields{},
			wantErr: true,
		},
		{
			name: "missing one required field should return error",
			fields: fields{
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			wantErr: true,
		},
		{
			name: "all fields set should return error nil",
			fields: fields{
				id:        "1",
				scheme:    "posts",
				data:      "data",
				namespace: "default",
			},
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := &CreateBatchRequest{
				namespace: tt.fields.namespace,
				scheme:    tt.fields.scheme,
				id:        tt.fields.id,
				data:      tt.fields.data,
				headers:   tt.fields.headers,
			}
			if err := s.Validate(); (err != nil) != tt.wantErr {
				t.Errorf("CreateBatchRequest.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
