rank-profile outside_schema1 inherits in_schema1 {

    function fo1() {
        expression: local1 + 2
    }

    function local1() {
        expression: local12 + local3 + local12 + a && b || c >= d
    }

    function local12() {
        expression: now
    }

    function local3() {
        expression: local12 + local12
    }

}