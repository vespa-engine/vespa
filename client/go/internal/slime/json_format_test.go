// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package slime

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPrintJson(t *testing.T) {
	slime := NewSlime()
	o := slime.SetObject()
	o.SetBool("a", true)
	o.SetBool("b", false)
	o.SetLong("c", 42)
	o.SetLong("d", 0)
	o.SetLong("e", -123456)
	o.SetDouble("f", 2.75)
	o.SetString("g", "x with\nembedded \"stuff\" like \\n")
	got := o.Field("g").AsString()
	assert.Equal(t,
		`x with
embedded "stuff" like \n`, got)
	a := o.SetArray("h")
	assert.Equal(t, 0, a.Entries())
	a.AddLong(17)
	o2 := o.SetObject("j")
	assert.Equal(t, 0, o2.Fields())
	o2.SetLong("jj", 987654321)
	os.Stderr.Write([]byte("test JsonEncodeTo:\n>>>\n"))
	JsonEncodeTo(slime, os.Stderr)
	os.Stderr.Write([]byte("\n<<<\ntest JsonEncodeTo\n"))
}
