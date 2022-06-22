import React from 'react';
import image from '../../assets/img/information.svg';

export default function Info({
  id,
  className = 'tip',
  height = 15,
  width = 15,
}) {
  //const image = require("../../assets/img/information.svg").default;
  return (
    <>
      <a
        href="#"
        className={className}
        id={`inf${id}`}
        style={{ visibility: 'visible' }}
      >
        <img
          src={image}
          height={height}
          width={width}
          className="information"
          alt="Missing"
        />
        <span id={`span${id}`}></span>
      </a>
    </>
    //TODO: Swap <a> with a bootstrap Overlay
  );
}
