import React from 'react';
import {
  ACTION,
  dispatch,
} from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

export default function AddPropertyButton({ id }) {
  return (
    <button
      className="addpropsbutton"
      onClick={() => dispatch(ACTION.INPUT_ADD, id)}
    >
      + Add property
    </button>
  );
}
