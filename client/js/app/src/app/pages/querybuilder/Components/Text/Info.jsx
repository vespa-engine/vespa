import React from 'react';
import { OverlayTrigger, Popover } from 'react-bootstrap';
import image from '../../assets/img/information.svg';

export default function Info({
  id,
  className = 'tip',
  height = 15,
  width = 15,
}) {
  //TODO: Make popver reflect tooltip for selected query type
  const popover = (
    <Popover id={`inf${id}`}>
      <Popover.Header as="h3">Popover right</Popover.Header>
      <Popover.Body>Content</Popover.Body>
    </Popover>
  );

  return (
    <>
      <OverlayTrigger
        placement="right"
        delay={{ show: 250, hide: 400 }}
        overlay={popover}
      >
        <span>
          <img
            src={image}
            height={height}
            width={width}
            className="information"
            alt="Missing"
          />
        </span>
      </OverlayTrigger>
    </>
  );
}
