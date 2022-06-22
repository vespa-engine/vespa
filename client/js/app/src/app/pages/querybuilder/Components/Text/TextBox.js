import React from "react";

export default function TextBox({id, className, children}) {
    return (
        <p className={className} id={id}>{children}</p>
    )
}