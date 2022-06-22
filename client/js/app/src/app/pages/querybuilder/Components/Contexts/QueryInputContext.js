import React, {useState, createContext} from "react";

export const QueryInputContext = createContext();

export const QueryInputProvider = (prop) => {
    const [inputs, setInputs] = useState([
        {
            id:1,
            type: "",
            input: ""
        }
    ])
    const [id, setId] = useState(1)

    return (
        <QueryInputContext.Provider value={{inputs, setInputs, id, setId}}>
            {prop.children}
        </QueryInputContext.Provider>
    )
}