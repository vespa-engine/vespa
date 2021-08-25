package vespa

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestCreateKeyPair(t *testing.T) {
	kp, err := CreateKeyPair()
	assert.Nil(t, err)
	assert.NotEmpty(t, kp.Certificate)
	assert.NotEmpty(t, kp.PrivateKey)
}
