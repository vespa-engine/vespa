package slime

import "testing"

func TestStructuredDisclose(t *testing.T) {
	expect := MakeObject(func(obj Value) {
		obj.Set("foo", MakeArray(func(arr Value) {
			arr.Add(MakeArray(func(arr2 Value) {
				arr2.Add(Empty)
				arr2.Add(Bool(true))
				arr2.Add(Long(5))
				arr2.Add(Double(5.5))
				arr2.Add(String("foo"))
				arr2.Add(Data([]byte{1, 2, 3}))
			}))
		}))
		obj.Set("bar", MakeObject(func(obj2 Value) {
			obj2.Set("a", Empty)
			obj2.Set("b", Bool(true))
			obj2.Set("c", Long(5))
			obj2.Set("d", Double(5.5))
			obj2.Set("e", String("foo"))
			obj2.Set("f", Data([]byte{1, 2, 3}))
		}))
	})
	var builder Builder
	EmitObject(&builder, func(obj DataSink) {
		EmitNamedArray(obj, "foo", func(arr DataSink) {
			EmitArray(arr, func(arr2 DataSink) {
				EmitEmpty(arr2)
				EmitBool(arr2, true)
				EmitLong(arr2, 5)
				EmitDouble(arr2, 5.5)
				EmitString(arr2, "foo")
				EmitData(arr2, []byte{1, 2, 3})
			})
		})
		EmitNamedObject(obj, "bar", func(obj2 DataSink) {
			EmitNamedEmpty(obj2, "a")
			EmitNamedBool(obj2, "b", true)
			EmitNamedLong(obj2, "c", 5)
			EmitNamedDouble(obj2, "d", 5.5)
			EmitNamedString(obj2, "e", "foo")
			EmitNamedData(obj2, "f", []byte{1, 2, 3})
		})
	})
	verifyFromValue(expect)(t, builder.Result())
}
