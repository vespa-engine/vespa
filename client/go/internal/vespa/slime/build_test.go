package slime

import (
	"testing"
)

func setTestLeafs(obj Value) {
	values := leafTestValues()
	names := dummyFieldNames(len(values))
	for i, name := range names {
		obj.Set(name, values[i])
	}
}

func addTestLeafs(arr Value) {
	for _, v := range leafTestValues() {
		arr.Add(v)
	}
}

func TestBuildValue(t *testing.T) {
	root := MakeObject(func(obj Value) {
		setTestLeafs(obj)
		obj.Set("obj", MakeObject(setTestLeafs))
		obj.Set("arr", MakeArray(addTestLeafs))
		obj.Set("nested", MakeArray(func(arr Value) {
			arr.Add(MakeObject(func(obj Value) {
				setTestLeafs(obj)
			}))
		}))
	})
	var builder Builder
	root.Emit(&builder)
	verifyFromValue(root)(t, builder.Result())
}

func TestEmptyBuilder(t *testing.T) {
	var builder Builder
	verifyEmpty()(t, builder.Result())
}
