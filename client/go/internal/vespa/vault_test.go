// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"encoding/json"
	"io"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/vespa-engine/vespa/client/go/internal/mock"
)

func TestEnsureVaultAccessForDevNonCloud(t *testing.T) {
	// Non-cloud targets are a no-op
	client := &mock.HTTPClient{}
	lt := LocalTarget(client, TLSOptions{}, 0)
	err := EnsureVaultAccessForDev(lt, []string{"my-vault"})
	assert.Nil(t, err)
	assert.Empty(t, client.Requests)
}

func TestEnsureVaultAccessForDevNoVaults(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)
	err := EnsureVaultAccessForDev(target, nil)
	assert.Nil(t, err)
	assert.Empty(t, client.Requests)
}

func TestEnsureVaultAccessForDevAlreadySet(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)
	// GET response: rule already present for a1 with SANDBOX context
	body, _ := json.Marshal(vaultRulesResponse{Rules: []vaultAccessRule{
		{Application: "a1", Contexts: []string{"SANDBOX"}, ID: 0},
	}})
	client.NextResponse(mock.HTTPResponse{Status: 200, Body: body})

	err := EnsureVaultAccessForDev(target, []string{"my-vault"})
	assert.Nil(t, err)
	require.Len(t, client.Requests, 1)
	assert.Equal(t, http.MethodGet, client.Requests[0].Method)
}

func TestEnsureVaultAccessForDevAddsRule(t *testing.T) {
	target, client := createCloudTargetControlPlane(t, io.Discard)
	client.ReadBody = true

	// GET: no existing rules
	getBody, _ := json.Marshal(vaultRulesResponse{Rules: []vaultAccessRule{}})
	client.NextResponse(mock.HTTPResponse{Status: 200, Body: getBody})

	// CSRF GET
	csrfBody, _ := json.Marshal(map[string]string{"token": "test-csrf"})
	client.NextResponse(mock.HTTPResponse{Status: 200, Body: csrfBody})

	// PUT response with confirmation message
	putRespBody, _ := json.Marshal(map[string]string{"message": "Set access rules for tenant t1, vault my-vault"})
	client.NextResponse(mock.HTTPResponse{Status: 200, Body: putRespBody})

	err := EnsureVaultAccessForDev(target, []string{"my-vault"})
	assert.Nil(t, err)
	require.Len(t, client.Requests, 3)
	assert.Equal(t, http.MethodGet, client.Requests[0].Method)
	assert.Equal(t, http.MethodGet, client.Requests[1].Method) // CSRF
	assert.Equal(t, http.MethodPut, client.Requests[2].Method)
	assert.Equal(t, "test-csrf", client.Requests[2].Header.Get("vespa-csrf-token"))
	assert.Equal(t, "application/json", client.Requests[2].Header.Get("Content-Type"))
}

func TestEnsureVaultAccessForDevGetError(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)
	client.NextResponseError(io.EOF)
	err := EnsureVaultAccessForDev(target, []string{"my-vault"})
	assert.NotNil(t, err)
}

func TestEnsureVaultAccessForDevAPIKeyFails(t *testing.T) {
	target, client := createCloudTarget(t, io.Discard)

	// GET: no existing rules, so we reach the API key check
	getBody, _ := json.Marshal(vaultRulesResponse{Rules: []vaultAccessRule{}})
	client.NextResponse(mock.HTTPResponse{Status: 200, Body: getBody})

	err := EnsureVaultAccessForDev(target, []string{"my-vault"})
	assert.ErrorContains(t, err, "API key")
	require.Len(t, client.Requests, 1) // only the GET, no PUT
}
