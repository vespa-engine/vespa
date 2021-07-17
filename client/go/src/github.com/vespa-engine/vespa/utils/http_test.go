package utils

import (
    "bytes"
    "github.com/stretchr/testify/assert"
	"io/ioutil"
    "net/http"
    "testing"
)

type mockHttpClient struct {}

func (c mockHttpClient) get(url string) (response *http.Response, error error) {
    var status int
    var body string
    if (url == "http://host/path") {
        status = 200
        body = "OK"
    } else {
        status = 500
        body = "Unexpected url"
    }

    return &http.Response{
        StatusCode: status,
        Body:       ioutil.NopCloser(bytes.NewBufferString(body)),
        Header:     make(http.Header),
    },
    nil
}

func TestHttpRequest(t *testing.T) {
    ActiveHttpClient = mockHttpClient{}
    response := HttpRequest("http://host", "/path", "description")
    assert.Equal(t, 200, response.StatusCode)
}

