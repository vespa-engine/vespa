// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/mock"
)

func TestListSampleApps(t *testing.T) {
	c := &mock.HTTPClient{}
	c.NextResponseString(200, readTestData(t, "sample-apps-contents.json"))
	c.NextResponseString(200, readTestData(t, "sample-apps-news.json"))
	c.NextResponseString(200, readTestData(t, "sample-apps-operations.json"))
	c.NextResponseString(200, readTestData(t, "sample-apps-vespa-cloud.json"))

	apps, err := listSampleApps(c)
	assert.Nil(t, err)
	expected := []string{
		"album-recommendation-monitoring",
		"album-recommendation-selfhosted",
		"basic-search-on-gke",
		"boolean-search",
		"dense-passage-retrieval-with-ann",
		"generic-request-processing",
		"http-api-using-request-handlers-and-processors",
		"incremental-search",
		"model-evaluation",
		"msmarco-ranking",
		"multiple-bundles",
		"multiple-bundles-lib",
		"news/app-1-getting-started",
		"news/app-2-feed-and-query",
		"news/app-3-searching",
		"news/app-5-recommendation",
		"news/app-6-recommendation-with-searchers",
		"news/app-7-parent-child",
		"operations/multinode",
		"part-purchases-demo",
		"secure-vespa-with-mtls",
		"semantic-qa-retrieval",
		"tensor-playground",
		"text-search",
		"transformers",
		"use-case-shopping",
		"vespa-chinese-linguistics",
		"vespa-cloud/album-recommendation",
		"vespa-cloud/album-recommendation-docproc",
		"vespa-cloud/album-recommendation-prod",
		"vespa-cloud/album-recommendation-searcher",
		"vespa-cloud/cord-19-search",
		"vespa-cloud/joins",
		"vespa-cloud/vespa-documentation-search",
	}
	assert.Equal(t, expected, apps)
}

func readTestData(t *testing.T, name string) string {
	contents, err := os.ReadFile(filepath.Join("testdata", name))
	if err != nil {
		t.Fatal(err)
	}
	return string(contents)
}
