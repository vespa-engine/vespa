import React from "react";


export default function ImageButton({onClick, children, className, id, image, height="15", width="15", style}) {
        return (
            <button
            id={id}       
            className={className}
            onClick={onClick}>
                <img src={image} height={height} width={width} style={style} alt="Missing"/>
            {children}
        </button>
        );
}