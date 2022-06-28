import React, { useContext, useState } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import SimpleButton from './SimpleButton';

export default function AddPropertyButton({ id }) {
  const { inputs, setInputs } = useContext(QueryInputContext);
  const [childId, setChildId] = useState(1);

  const addChildProperty = () => {
    const newInputs = inputs.slice();
    //this is needed because substring() is exclusive the last parameter
    const iterId = id + '.';
    //TODO: the id can be of type 1.1.2, need to iterate over it to go through the tree of children.
    let currentId = iterId.substring(0, 1);
    let index = newInputs.findIndex((element) => element.id === currentId); //get the index of the root parent
    let children = newInputs[index].children;
    for (let i = 3; i < iterId.length; i += 2) {
      currentId = iterId.substring(0, i);
      index = children.findIndex((element) => element.id === currentId);
      children = children[index].children;
    }
    children.push({
      id: id + '.' + childId,
      type: newInputs[index].type,
      input: '',
      hasChildren: false,
      children: [],
    });
    setInputs(newInputs);
    setChildId((childId) => childId + 1);
    console.log('BUTTON CLICK');
  };

  return (
    <SimpleButton
      id={`propb${id}`}
      className={'addpropsbutton'}
      onClick={addChildProperty}
    >
      + Add property
    </SimpleButton>
  );
}
