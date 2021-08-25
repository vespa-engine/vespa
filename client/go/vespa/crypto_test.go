package vespa

import (
	"testing"
	"github.com/stretchr/testify/assert"
)

func TestCreateKeyPair(t *testing.T) {
	kp, err := CreateKeyPair()
	assert.Nil(t, err)
	assert.NotEmpty(t, kp.Certificate)
	assert.NotEmpty(t, kp.PrivateKey)
}
