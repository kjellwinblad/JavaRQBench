package se.uu.collection;

class LongStack {
    private int stackSize;
    private int stackPos = 0;
    private long[] stackArray;
    
    public LongStack(int stackSize){
        this.stackSize = stackSize;
        stackArray = new long[stackSize];
    }

    public LongStack(){
        this(32);
    }
    public void push(long node){
        if(stackPos == stackSize){
            int newStackSize = stackSize*4;
        	long[] newStackArray = new long[newStackSize];
            for(int i = 0; i < stackSize;i++){
                newStackArray[i] = stackArray[i];
            }
            stackSize = newStackSize;
            stackArray = newStackArray;
        }
        stackArray[stackPos] = node;
        stackPos = stackPos + 1;
    }

	public long pop(){
        if(stackPos == 0){
            throw new RuntimeException("Attempt to pop empty long stack");
        }
        stackPos = stackPos - 1;
        return stackArray[stackPos];
    }

	public long top(){
        if(stackPos == 0){
        	throw new RuntimeException("Attempt to look at top of empty long stack");
        }
        return stackArray[stackPos - 1];
    }
    public void resetStack(){
        stackPos = 0;
    }
    public int size(){
        return stackPos;
    }
    
	public long[] getStackArray(){
        return stackArray;
    };
    public int getStackPos(){
        return stackPos;
    }
    public void setStackPos(int stackPos){
        this.stackPos = stackPos;
    }

    public void copyStateFrom(LongStack stack){
        if(stack.stackSize > stackSize){
            this.stackSize = stack.stackSize;
            stackArray = new long[this.stackSize];
        }
        this.stackPos = stack.stackPos;
        for(int i = 0; i < this.stackPos; i++){
            this.stackArray[i] = stack.stackArray[i];
        }
    }

}