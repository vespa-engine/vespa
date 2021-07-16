package utils

import (
    "bytes"
	"io/ioutil"
    "net/http"
    "testing"
)

type mockHttpClient struct {}

func (c mockHttpClient) get(url string) (response *http.Response, error error) {
    return &http.Response{
        StatusCode: 200,
        Body:       ioutil.NopCloser(bytes.NewBufferString(`OK`)),
	    Header:     make(http.Header),
    },
    nil
}

func TestHttpRequest(t *testing.T) {
    httpClient = mockHttpClient{}
    response := HttpRequest("http://host", "/path", "description")
    if (response.StatusCode != 200) {
        t.Errorf("Status is not 200")
    }
}

