// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

func TestWord(t *testing.T) {
	assert.Equal(t, "child", word(1, "child", "children"))
	assert.Equal(t, "children", word(2, "child", "children"))
	assert.Equal(t, "children", word(0, "child", "children"))
}

func TestSuffix(t *testing.T) {
	assert.Equal(t, "", suffix(1, "s"))
	assert.Equal(t, "s", suffix(2, "s"))
	assert.Equal(t, "s", suffix(0, "s"))
}

func TestRenderSlimeAsNestedTable(t *testing.T) {
	root := slime.MakeObject(func(obj slime.Value) {
		obj.Set("name", slime.String("test"))
		obj.Set("count", slime.Long(42))
		obj.Set("price", slime.Double(3.5))
		obj.Set("enabled", slime.Bool(true))
		obj.Set("disabled", slime.Bool(false))
		obj.Set("empty", slime.Empty)
		obj.Set("data", slime.Data([]byte("binary data")))
		obj.Set("items", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.Long(1))
			arr.Add(slime.Long(2))
			arr.Add(slime.Long(3))
		}))
		obj.Set("metadata", slime.MakeObject(func(meta slime.Value) {
			meta.Set("version", slime.String("1.0"))
			meta.Set("author", slime.String("vespa"))
		}))
	})

	var actual bytes.Buffer
	slimeValueAsTable(root).render(&output{out: &actual})

	var expected bytes.Buffer
	expectedTab := newTable()
	// Scalars first (alphabetically)
	expectedTab.str("count").str("42").commit()
	expectedTab.str("data").str("0x62696e6172792064617461").commit()
	expectedTab.str("disabled").str("false").commit()
	expectedTab.str("empty").str("null").commit()
	expectedTab.str("enabled").str("true").commit()
	expectedTab.str("name").str("test").commit()
	expectedTab.str("price").str("3.5").commit()
	// Objects next
	metadataTab := newTable()
	metadataTab.str("author").str("vespa").commit()
	metadataTab.str("version").str("1.0").commit()
	expectedTab.str("metadata").tab(metadataTab).commit()
	// Arrays last
	itemsTab := newTable()
	itemsTab.str("1").commit()
	itemsTab.str("2").commit()
	itemsTab.str("3").commit()
	expectedTab.str("items").tab(itemsTab).commit()
	expectedTab.render(&output{out: &expected})

	assert.Equal(t, expected.String(), actual.String())
}
