rank-profile outside_schema1 inherits in_schema1 {

    function fo1() {
        expression: local1 + 2
    }

    function local1() {
        expression: local2 + local3
    }

    function local2() {
        expression: now
    }

    function local3() {
        expression: local2
    }

}