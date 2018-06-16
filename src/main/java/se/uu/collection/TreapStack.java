package se.uu.collection;

import se.uu.collection.ImmutableTreapMap.ImmutableTreapValue;

class TreapStack {
    private int stackSize;
    private int stackPos = 0;
    private ImmutableTreapValue[] stackArray;
    
    public TreapStack(int stackSize){
        this.stackSize = stackSize;
        stackArray = new ImmutableTreapValue[stackSize];
    }

    public TreapStack(){
        this(32);
    }
    public void push(ImmutableTreapValue node){
        if(stackPos == stackSize){
            int newStackSize = stackSize*4;
            ImmutableTreapValue[] newStackArray = new ImmutableTreapValue[newStackSize];
            for(int i = 0; i < stackSize;i++){
                newStackArray[i] = stackArray[i];
            }
            stackSize = newStackSize;
            stackArray = newStackArray;
        }
        stackArray[stackPos] = node;
        stackPos = stackPos + 1;
    }
    @SuppressWarnings("unchecked")
	public ImmutableTreapValue pop(){
        if(stackPos == 0){
            return null;
        }
        stackPos = stackPos - 1;
        return stackArray[stackPos];
    }
    @SuppressWarnings("unchecked")
	public ImmutableTreapValue top(){
        if(stackPos == 0){
            return null;
        }
        return stackArray[stackPos - 1];
    }
    
    public void reverseStack(){
    	for(int i = 0; i < stackPos / 2; i++)
    	{
    		ImmutableTreapValue temp = stackArray[i];
    	    stackArray[i] = stackArray[stackPos - i - 1];
    	    stackArray[stackPos - i - 1] = temp;
    	}
    }
    
    public void resetStack(){
        stackPos = 0;
    }
    public int size(){
        return stackPos;
    }
    
	public ImmutableTreapValue[] getStackArray(){
        return stackArray;
    };
    public int getStackPos(){
        return stackPos;
    }
    public void setStackPos(int stackPos){
        this.stackPos = stackPos;
    }

    public void copyStateFrom(TreapStack stack){
        if(stack.stackSize > stackSize){
            this.stackSize = stack.stackSize;
            stackArray = new ImmutableTreapValue[this.stackSize];
        }
        this.stackPos = stack.stackPos;
        for(int i = 0; i < this.stackPos; i++){
            this.stackArray[i] = stack.stackArray[i];
        }
    }

}