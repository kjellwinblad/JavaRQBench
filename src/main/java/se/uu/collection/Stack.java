package se.uu.collection;

class Stack<T> {
    private int stackSize;
    private int stackPos = 0;
    private Object[] stackArray;
    
    public Stack(int stackSize){
        this.stackSize = stackSize;
        stackArray = new Object[stackSize];
    }

    public Stack(){
        this(32);
    }
    public void push(T node){
        if(stackPos == stackSize){
            int newStackSize = stackSize*4;
        	Object[] newStackArray = new Object[newStackSize];
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
	public T pop(){
        if(stackPos == 0){
            return null;
        }
        stackPos = stackPos - 1;
        return (T)stackArray[stackPos];
    }
    @SuppressWarnings("unchecked")
	public T top(){
        if(stackPos == 0){
            return null;
        }
        return (T) stackArray[stackPos - 1];
    }
    
    public void reverseStack(){
    	for(int i = 0; i < stackPos / 2; i++)
    	{
    	    Object temp = stackArray[i];
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
    
	public Object[] getStackArray(){
        return stackArray;
    };
    public int getStackPos(){
        return stackPos;
    }
    public void setStackPos(int stackPos){
        this.stackPos = stackPos;
    }

    public void copyStateFrom(Stack<T> stack){
        if(stack.stackSize > stackSize){
            this.stackSize = stack.stackSize;
            stackArray = new Object[this.stackSize];
        }
        this.stackPos = stack.stackPos;
        for(int i = 0; i < this.stackPos; i++){
            this.stackArray[i] = stack.stackArray[i];
        }
    }

}