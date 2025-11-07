rank-profile outside_schema1 inherits in_schema1 {

    function fo1() {
        expression: now
    }

    rank-profile child inherits outside_schema1 {

        function fo2() {
            expression: 5
        }
    }

}