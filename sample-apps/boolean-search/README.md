Boolean Search
==================

Boolean Search and how to feed and query is described in 
[boolean search](https://git.corp.yahoo.com/pages/vespa/documentation/documentation/boolean-search.html).

Adding boolean search to an application is easy. Just add a field of
type predicate to the .sd-file. (Remember to set the arity parameter.)


### Feed and search
1. **Feed** the data that is to be searched:
    ```sh
    curl -X POST --data-binary @adsdata.xml <endpoint url>/document
    ```

2. **Search** using yql expressions, e.g. `select * from sources * where predicate(target, {"name":"Wile E. Coyote"},{});`
    ```sh
    curl "<endpoint url>/search/?query=sddocname:ad&yql=select%20*%20from%20sources%20*%20where%20predicate(target%2C%20%7B%22name%22%3A%22Wile%20E.%20Coyote%22%7D%2C%7B%7D)%3B"
    ```
