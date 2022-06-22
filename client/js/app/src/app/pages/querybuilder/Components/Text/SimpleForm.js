import React from "react";
import { useState } from "react";

export default function SimpleForm({id, className="propvalue", initial, size="20"}) {
    const [input, setValue] = useState(initial);

    return (
        <form className={className}>
            <input
                size={size}
                type="text"
                id={id}
                className={className}
                value={input}
                onChange={(e) => setValue(e.target.value)}
            />
        </form>
    );
}