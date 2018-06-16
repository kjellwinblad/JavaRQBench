package se.uu.collection;
import java.util.Random;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.*;
import java.io.*;
import java.util.concurrent.locks.*;

public class AVLTreeMapOpt<K, V> extends AbstractMap<K,V> implements SplitableAndJoinableMap<K, V>, Invalidatable, AnyKeyProviding<K>{


    private static final Unsafe unsafe;
    private static final long lockWordOffset;
    private volatile long lockWord = Long.MIN_VALUE+2;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);

            lockWordOffset = unsafe.objectFieldOffset
                (AVLTreeMapOpt.class.getDeclaredField("lockWord"));
        } catch (Exception ex) { 
            throw new Error(ex);
        }
    }
    
    private int statLockStatistics = 0;
    private boolean valid = true;
    private int size = 0;
    private AVLNode root = null;
    private Object parent = null;
    private final Comparator<? super K> comparator;

    private static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
    private static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
    private static final int STAT_LOCK_FAILURE_CONTRIB = 250;
    private static final int STAT_LOCK_SUCCESS_CONTRIB = 1;

    // ====== FOR DEBUGING ======
    private final static boolean DEBUG = false;
    // ==========================

    private final class AVLNode{
        AVLNode left;
        AVLNode right;
        K key;
        V value;
        int balance = 0;
        AVLNode parent = null;
        public AVLNode(K key, V value){
            this.key = key;
            this.value = value;
        }
        public String toString(){
            return "NODE(" + key + ", " + balance + ")";
        }
    }

    //==== Functions for debuging and testing
    
    public String toString(){
        return "B(" + root + ", " + isValid() + "," + getStatistics() + "," + getParent() + ","+size()+")";
    }

    final private int avlValidateP(AVLNode toTest){
        
        if(toTest != null && toTest.parent != null){
            System.out.println("Parent should be null\n");
            printDot(root, "parent_should_be_null");
            throw new RuntimeException();
        }
        return avlValidate(toTest);
    }
    final private int avlValidate(AVLNode toTest){
        if(toTest == null){
            return 0;
        }else{
            int hl = avlValidate(toTest.left);
            if(toTest.left != null && toTest.left.parent != toTest){
                System.out.println("WRONG PARENT\n");
                printDot(root, "wrong_parent");
                throw new RuntimeException();
            }
            int hr = avlValidate(toTest.right);
            if(toTest.right != null && toTest.right.parent != toTest){
                System.out.println("WRONG PARENT\n");
                printDot(root, "wrong_parent");
                throw new RuntimeException();
            }
            if(toTest.balance == 0 && hl != hr){
                System.out.println("FAIL 1 "+hl+" " +hr+"\n");
                printDot(root, "fail1");
                throw new RuntimeException();
            }else if(toTest.balance == -1 && (hr - hl) != -1){
                System.out.println("FAIL 2\n");
                printDot(root, "fail2");
                throw new RuntimeException();
            }else if(toTest.balance == 1 && (hr - hl) != 1){
                System.out.println("FAIL 3 "+(hr - hl)+"\n");
                printDot(root, "fail3");
                throw new RuntimeException();
            }else if(toTest.balance > 1 || toTest.balance < -1){
                System.out.println("FAIL 4\n");
                printDot(root, "fail4");
                throw new RuntimeException();
            }
            if(hl > hr){
                return hl + 1;
            }else{
                return hr + 1;
            }
        }
    }

    void printDotHelper(AVLNode node, PrintStream writeTo){
        Random rand = new Random();
        try{
            if(node!=null){
                if(node.left !=null){
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + node.left.value + ", " + node.left.balance + ", " + (node.left.parent != null ? node.left.parent.key : null) + " \"");
                    writeTo.println(";");
                }else{
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + null+ ", " + rand.nextInt() + " \"");
                    writeTo.println(";");
                }
                if(node.right !=null){
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + node.right.value + ", " + node.right.balance + ", " + (node.right.parent != null ? node.right.parent.key : null) + " \"");
                    writeTo.println(";");
                }else{
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + null+ ", " + rand.nextInt() + " \"");
                    writeTo.println(";");
                }
                printDotHelper(node.left, writeTo);
                printDotHelper(node.right, writeTo);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    void printDot(AVLNode node, String fileName){
        try{
            Process p = new ProcessBuilder("dot", "-Tsvg")
                .redirectOutput(ProcessBuilder.Redirect.to(new File(fileName + ".svg")))
                .start();
            PrintStream writeTo = new PrintStream(p.getOutputStream());
            writeTo.print("digraph G{\n");
            writeTo.print("  graph [ordering=\"out\"];\n");
            printDotHelper(node, writeTo);
            writeTo.print("}\n");
            writeTo.close();
            p.waitFor();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //=== End of debug functions ==================


    //=== Constructors ============================

    public AVLTreeMapOpt() {
        comparator = null;
    }

    public AVLTreeMapOpt(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public void setParent(Object parent){
        this.parent = parent;
    }

    public Object getParent(){
        return parent;
    }

    //=== Public functions and helper functions ===

    //=== Any key providing functions =============

    public K anyKey(){
        if(root != null){
            return root.key;
        }else{
            return null;
        }
    }

    //=== Invalidatable functions =================

    public boolean isValid(){
        return valid;
    }

    public void invalidate(){
        valid = false;
    }


    //=== Lock Functions ==========================

    public boolean tryLock(){
        long currentValue = lockWord;
        if((currentValue % 2) == 0){
            if(unsafe.compareAndSwapLong(this, lockWordOffset, currentValue, currentValue + 1)){
                return true;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }
    
    public void lock(){
        if (tryLock()) {
            statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
            return;
        }
        boolean zeroSeen;
        int i;
        do{
            while(true){
                zeroSeen = false;
                i = 0;
                while(!zeroSeen && i < 10000){
                    if((lockWord % 2) == 0){
                        zeroSeen = true;
                    }
                    i++;
                }
                if(!zeroSeen){
                    Thread.yield();
                }else{
                    break;
                }
            }
        }while(!tryLock());
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void unlock(){
        lockWord = lockWord + 1;
    }

    public long getOptimisticReadToken(){
        long currentValue = lockWord;
        if((currentValue % 2) == 0){
            return currentValue;
        }else{
            return Long.MIN_VALUE;
        }
    }

    public boolean validateOptimisticReadToken(long optimisticReadToken){
        unsafe.loadFence();
        boolean validateRes = optimisticReadToken == lockWord;
        return validateRes;
    }

    public int getStatistics(){
        return statLockStatistics;
    }
    
    public void resetStatistics(){
        statLockStatistics = 0;
    }
    
    public int getHighContentionLimit(){
        return STAT_LOCK_HIGH_CONTENTION_LIMIT;
    }

    public int getLowContentionLimit(){
        return STAT_LOCK_LOW_CONTENTION_LIMIT;
    }

    public boolean isHighContentionLimitReached(){
        return statLockStatistics > STAT_LOCK_HIGH_CONTENTION_LIMIT;
    }
    
    public boolean isLowContentionLimitReached(){
        return statLockStatistics < STAT_LOCK_LOW_CONTENTION_LIMIT;
    }

    //=== Sorted Set Functions ====================

    private final int computeHeight(){
        if(root == null){
            return 0;
        } else {
            AVLNode currentNode = root;
            int hightSoFar = 1;
            while(currentNode.left != null || currentNode.right != null){
                if(currentNode.balance == -1){
                    currentNode = currentNode.left;
                }else{
                    currentNode = currentNode.right;
                }
                hightSoFar = hightSoFar + 1;
            }
            return hightSoFar;
        }
    }

    private final K minKey(){
        AVLNode currentNode = root;
        while(currentNode.left != null){
            currentNode = currentNode.left;
        }
        if(currentNode == null){
            return null;
        }else{
            return currentNode.key;
        }
    }


    private final K maxKey(){
        AVLNode currentNode = root;
        while(currentNode.right != null){
            currentNode = currentNode.right;
        }
        if(currentNode == null){
            return null;
        }else{
            return currentNode.key;
        }
    }

    public SplitableAndJoinableMap<K, V> join(SplitableAndJoinableMap<K, V> right){
        // AVLValueNode **tstack[AVLATREE_STACK_NEED];
        // int tpos = 0;
        // int dstack[AVLATREE_STACK_NEED+1];
        // int dpos = 0;
        // int state = 1;
        // AVLValueNode **this;
        // int dir;
        // AVLValueNode *p1, *p2, *p;
        // dstack[dpos++] = AVLATREE_DIR_END;
        AVLNode prevNode = null;//f
        AVLNode currentNode = null;//f
        AVLTreeMapOpt<K, V> newTree = null;
        if(comparator == null){
            newTree = new AVLTreeMapOpt<K, V>();
        }else{
            newTree = new AVLTreeMapOpt<K, V>(comparator);
        }
        AVLTreeMapOpt<K,V> leftTree = this;
        AVLTreeMapOpt<K,V> rightTree = (AVLTreeMapOpt<K,V>)right;
        if(leftTree.root == null){
            newTree.root = rightTree.root;
            newTree.size = rightTree.size + leftTree.size;
            return newTree;
        }else if(rightTree.root == null){
            newTree.root = leftTree.root;
            newTree.size = leftTree.size + rightTree.size;
            return newTree;
        }
        int leftHeight = leftTree.computeHeight();
        int rightHeight = rightTree.computeHeight();
        if(leftHeight >= rightHeight){
            K minKey = rightTree.minKey();
            V minValue = rightTree.remove(minKey);
            rightTree.size = rightTree.size + 1;
            AVLNode newRoot = new AVLNode(minKey, minValue);
            int newRightHeight = rightTree.computeHeight();
            // Find a node v on the rightmost path from the root of T1 , whose height is either h or h + 1, as follows:
            // From: http://www.cs.toronto.edu/~avner/teaching/263/A/2sol.pdf
            // v <- root(T1 )
            // h' <- h1
            // while h > h + 1 do
            //    if balance factor (v) = -1
            //    then h' <- h' - 2
            //    else h' <- h- - 1
            //    v <- rightchild(v)
            prevNode = null;
            currentNode = leftTree.root;
            int currentHeight = leftHeight;
            while(currentHeight > newRightHeight + 1){
                if(currentNode.balance == -1){
                    currentHeight = currentHeight - 2;
                }else{
                    currentHeight = currentHeight - 1;
                }
                prevNode = currentNode;
                currentNode = currentNode.right;
            }
            AVLNode oldCurrentNodeParent = prevNode;
            newRoot.left = currentNode;
            if(currentNode != null){
                currentNode.parent = newRoot;
            }
            newRoot.right = rightTree.root;
            if(rightTree.root != null){
                rightTree.root.parent = newRoot;
            }
            newRoot.balance = newRightHeight - currentHeight;

            if(oldCurrentNodeParent == null){//Check if this can happen at all
                newTree.root = newRoot;
            }else if(oldCurrentNodeParent.left == currentNode){
                oldCurrentNodeParent.left = newRoot;
                newRoot.parent = oldCurrentNodeParent;
                newTree.root = leftTree.root;
            }else{
                oldCurrentNodeParent.right = newRoot;
                newRoot.parent = oldCurrentNodeParent;
                newTree.root = leftTree.root;
            }
            currentNode = newRoot;
        }else{
            //This case is symetric to the previous case
            K maxKey = leftTree.maxKey();//f
            V maxValue = leftTree.remove(maxKey);//f
            leftTree.size = leftTree.size + 1;
            AVLNode newRoot = new AVLNode(maxKey, maxValue);//f
            int newLeftHeight = leftTree.computeHeight();//f
            prevNode = null;//f
            currentNode = rightTree.root;//f
            int currentHeight = rightHeight;//f
            while(currentHeight > newLeftHeight + 1){//f
                if(currentNode.balance == 1){//f
                    currentHeight = currentHeight - 2;//f
                }else{
                    currentHeight = currentHeight - 1;//f
                }
                prevNode = currentNode;//f
                currentNode = currentNode.left;//f
            }
            AVLNode oldCurrentNodeParent = prevNode;//f
            newRoot.right = currentNode;//f
            if(currentNode != null){
                currentNode.parent = newRoot;//f
            }
            newRoot.left = leftTree.root;//f
            if(leftTree.root != null){
                leftTree.root.parent = newRoot;//f
            }
            newRoot.balance = currentHeight - newLeftHeight;//f
            if(oldCurrentNodeParent == null){//Check if this can happen at all
                newTree.root = newRoot;
            }else if(oldCurrentNodeParent.left == currentNode){
                oldCurrentNodeParent.left = newRoot;
                newRoot.parent = oldCurrentNodeParent;
                newTree.root = rightTree.root;
            }else{
                oldCurrentNodeParent.right = newRoot;
                newRoot.parent = oldCurrentNodeParent;
                newTree.root = rightTree.root;
            }
            currentNode = newRoot;
        }
        //Now we need to continue as if this was during the insert 
        while(prevNode != null){
            if(prevNode.left == currentNode){
                if(prevNode.balance == -1){
                    AVLNode leftChild = prevNode.left;
                    //Need to rotate
                    if(leftChild.balance == -1){
                        newTree.rotateLeft(prevNode);
                    }else{
                        newTree.rotateDoubleRight(prevNode);
                    }
                    newTree.size = leftTree.size + rightTree.size;
                    if(DEBUG) avlValidateP(newTree.root);
                    return newTree; //Parents not affected balance restored
                }else if(prevNode.balance == 0){
                    prevNode.balance = -1;
                }else{
                    prevNode.balance = 0;
                    break;//balanced
                }
            }else{
                //Take care of later... Should be symetric
                if(prevNode.balance == 1){
                    AVLNode rightChild = prevNode.right;
                    //Need to rotate
                    if(rightChild.balance == 1){
                        newTree.rotateRight(prevNode);
                    }else{
                        newTree.rotateDoubleLeft(prevNode);
                    }
                    newTree.size = leftTree.size + rightTree.size;
                    if(DEBUG) avlValidateP(newTree.root);
                    return newTree;
                }else if (prevNode.balance == 0){
                    prevNode.balance = 1;
                }else{
                    prevNode.balance = 0;
                    break;//Balanced
                }
            }
            currentNode = prevNode;
            prevNode = prevNode.parent;
        }
        newTree.size = leftTree.size + rightTree.size;
        if(DEBUG) avlValidateP(newTree.root);
        return newTree;
    }

    public SplitableAndJoinableMap<K, V> split(Object[] splitKeyWriteBack,
                                               SplitableAndJoinableMap<K, V>[] rightTreeWriteBack){
        AVLNode leftRoot = null;
        AVLNode rightRoot = null;
        if(root == null){
            return null;
        }else if(root.left == null && root.right == null){
            return null;
        }else if(root.left == null){
            splitKeyWriteBack[0] = root.right.key;
            rightRoot = root.right;
            rightRoot.parent = null;
            rightRoot.balance = 0;
            root.right = null;
            leftRoot = root;
            leftRoot.balance = 0;
        }else{
            splitKeyWriteBack[0] = root.key;
            leftRoot = root.left;
            leftRoot.parent = null;
            root.left = null;
            if (root.right == null){
                rightRoot = root;
                rightRoot.balance = 0;
            }else{
                K insertKey = root.key;
                V insertValue = root.value;
                root = root.right;
                root.parent = null;
                put(insertKey, insertValue);
                size = size - 1;
                rightRoot = root;
            }
        }
        AVLTreeMapOpt<K,V> leftTree = null;
        if(comparator == null){
            leftTree = new AVLTreeMapOpt<K, V>();
        }else{
            leftTree = new AVLTreeMapOpt<K, V>(comparator);
        }
        leftTree.root = leftRoot;
        AVLTreeMapOpt<K,V> rightTree = null;
        if(comparator == null){
            rightTree = new AVLTreeMapOpt<K, V>();
        }else{
            rightTree = new AVLTreeMapOpt<K, V>(comparator);
        }
        rightTree.root = rightRoot;
        int remainder = size % 2;
        int aproxSizes = size / 2;
        leftTree.size = aproxSizes;
        rightTree.size = aproxSizes + remainder;
        rightTreeWriteBack[0] = rightTree;
        if(DEBUG) {
            avlValidateP(leftTree.root);
            avlValidateP(rightTree.root);
        }
        //        System.err.println("S"+aproxSizes);
        //printDot(leftTree.root, "left");
        //printDot(rightTree.root, "right");
        //System.exit(0);
        return leftTree;
    }

    public int size(){
        return size;
    }

    public boolean isEmpty(){
        return root == null;
    }

    final private AVLNode getAVLNodeUsingComparator(Object keyParam) {
        @SuppressWarnings("unchecked")
        K key = (K) keyParam;
        AVLNode currentNode = root;
        Comparator<? super K> cpr = comparator;
        while(currentNode != null){
            K nodeKey = currentNode.key;
            int compareValue = cpr.compare(key,nodeKey);
            if(compareValue < 0) {
                currentNode = currentNode.left;
            } else if (compareValue > 0) {
                currentNode = currentNode.right;
            } else {
                return currentNode;
            }
        }
        return null;
    }

    final private AVLNode getAVLNode(Object keyParam){
        if(comparator != null){
            return getAVLNodeUsingComparator(keyParam);
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) keyParam;
            AVLNode currentNode = root;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = key.compareTo(nodeKey);
                if(compareValue < 0) {
                    currentNode = currentNode.left;
                } else if (compareValue > 0) {
                    currentNode = currentNode.right;
                } else {
                    return currentNode;
                }
            }
            return null;
        }
    }

    public boolean containsKey(Object key){
        return getAVLNode(key) != null;
    }

    public V get(Object key){
        AVLNode node = getAVLNode(key);
        if(node != null){
            return node.value;
        }else{
            return null;
        }
    }

    final private void rotateLeft(AVLNode prevNode){
        //Single left rotation
        AVLNode leftChild = prevNode.left;
        AVLNode prevNodeParent = prevNode.parent;
        prevNode.left = leftChild.right;
        if(prevNode.left != null){
            prevNode.left.parent = prevNode;
        }
        leftChild.right = prevNode;
        prevNode.parent = leftChild;
        prevNode.balance = 0;
        if(prevNodeParent == null){
            root = leftChild;
        }else if(prevNodeParent.left == prevNode){
            prevNodeParent.left = leftChild;
        }else{
            prevNodeParent.right = leftChild;
        }
        leftChild.parent = prevNodeParent;
        leftChild.balance = 0;
    }

    final private void rotateRight(AVLNode prevNode){
        //Single left rotation
        AVLNode rightChild = prevNode.right;
        AVLNode prevNodeParent = prevNode.parent;
        prevNode.right = rightChild.left;
        if(prevNode.right != null){
            prevNode.right.parent = prevNode;
        }
        rightChild.left = prevNode;
        prevNode.parent = rightChild;
        prevNode.balance = 0;
        if(prevNodeParent == null){
            root = rightChild;
        }else if(prevNodeParent.left == prevNode){
            prevNodeParent.left = rightChild;
        }else{
            prevNodeParent.right = rightChild;
        }
        rightChild.parent = prevNodeParent;
        rightChild.balance = 0;
    }


    final private void rotateDoubleRight(AVLNode prevNode){
        AVLNode prevNodeParent = prevNode.parent;
        AVLNode leftChild = prevNode.left;
        AVLNode leftChildRightChild = leftChild.right;

        leftChild.right = leftChildRightChild.left;
        if(leftChildRightChild.left != null){
            leftChildRightChild.left.parent = leftChild;
        }

        leftChildRightChild.left = leftChild;
        leftChild.parent = leftChildRightChild;

        prevNode.left = leftChildRightChild.right;
        if(leftChildRightChild.right != null){
            leftChildRightChild.right.parent = prevNode;
        }
        leftChildRightChild.right = prevNode;
        prevNode.parent = leftChildRightChild;

        prevNode.balance = (leftChildRightChild.balance == -1) ? +1 : 0;
        leftChild.balance = (leftChildRightChild.balance == 1) ? -1 : 0;
        if(prevNodeParent == null){
            root = leftChildRightChild;
        }else if(prevNodeParent.left == prevNode){
            prevNodeParent.left = leftChildRightChild;
        }else{
            prevNodeParent.right = leftChildRightChild;
        }
        leftChildRightChild.parent = prevNodeParent;
        leftChildRightChild.balance = 0;
    }

    final private void rotateDoubleLeft(AVLNode prevNode){
        AVLNode prevNodeParent = prevNode.parent;
        AVLNode rightChild = prevNode.right;
        AVLNode rightChildLeftChild = rightChild.left;
        rightChild.left = rightChildLeftChild.right;
        if(rightChildLeftChild.right != null){
            rightChildLeftChild.right.parent = rightChild;
        }

        rightChildLeftChild.right = rightChild;
        rightChild.parent = rightChildLeftChild;

        prevNode.right = rightChildLeftChild.left;
        if(rightChildLeftChild.left != null){
            rightChildLeftChild.left.parent = prevNode;
        }

        rightChildLeftChild.left = prevNode;
        prevNode.parent = rightChildLeftChild;

        prevNode.balance = (rightChildLeftChild.balance == 1) ? -1 : 0;
        rightChild.balance = (rightChildLeftChild.balance == -1) ? 1 : 0;
        if(prevNodeParent == null){
            root = rightChildLeftChild;
        }else if(prevNodeParent.left == prevNode){
            prevNodeParent.left = rightChildLeftChild;
        }else{
            prevNodeParent.right = rightChildLeftChild;
        }
        rightChildLeftChild.parent = prevNodeParent;
        rightChildLeftChild.balance = 0;
    }

    private V put(K keyParam, V value, boolean replace){
        if(DEBUG) avlValidateP(root);
        AVLNode prevNode = null;
        AVLNode currentNode = root;
        boolean dirLeft = true;
        if(comparator != null){
            K key = keyParam;
            Comparator<? super K> cpr = comparator;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = cpr.compare(key, nodeKey);
                if(compareValue < 0) {
                    dirLeft = true;
                    prevNode = currentNode;
                    currentNode = currentNode.left;
                } else if (compareValue > 0) {
                    dirLeft = false;
                    prevNode = currentNode;
                    currentNode = currentNode.right;
                } else {
                    V prevValue = currentNode.value;
                    if(replace){
                        currentNode.value = value;
                    }
                    if(DEBUG) avlValidateP(root);
                    return prevValue;
                }
            }
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) keyParam;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = key.compareTo(nodeKey);
                if(compareValue < 0) {
                    dirLeft = true;
                    prevNode = currentNode;
                    currentNode = currentNode.left;
                } else if (compareValue > 0) {
                    dirLeft = false;
                    prevNode = currentNode;
                    currentNode = currentNode.right;
                } else {
                    V prevValue = currentNode.value;
                    currentNode.value = value;
                    if(DEBUG) avlValidateP(root);
                    return prevValue;
                }
            }
        }

        //Insert node
        size = size + 1;
        currentNode = new AVLNode(keyParam, value);
        if(prevNode == null){
            root = currentNode;
        }else if(dirLeft){
            prevNode.left = currentNode;
        }else{
            prevNode.right = currentNode;
        }
        currentNode.parent = prevNode;
        //Balance
        while(prevNode != null){         
            if(prevNode.left == currentNode){
                if(prevNode.balance == -1){
                    AVLNode leftChild = prevNode.left;
                    //Need to rotate
                    if(leftChild.balance == -1){
                        rotateLeft(prevNode);
                    }else{
                        rotateDoubleRight(prevNode);
                    }
                    if(DEBUG) avlValidateP(root);
                    return null; //Parents not affected balance restored
                }else if(prevNode.balance == 0){
                    prevNode.balance = -1;
                }else{
                    prevNode.balance = 0;
                    break;//balanced
                }
            }else{
                //Take care of later... Should be symetric
                if(prevNode.balance == 1){
                    AVLNode rightChild = prevNode.right;
                    //Need to rotate
                    if(rightChild.balance == 1){
                        rotateRight(prevNode);
                    }else{
                        rotateDoubleLeft(prevNode);
                    }
                    if(DEBUG) avlValidateP(root);
                    return null; //Parents not affected balance restored
                }else if (prevNode.balance == 0){
                    prevNode.balance = 1;
                }else{
                    prevNode.balance = 0;
                    break;//Balanced
                }
            }
            currentNode = prevNode;
            prevNode = prevNode.parent;
        }
        if(DEBUG) avlValidateP(root);
        return null;
    }

    public V put(K key, V value){
        return put(key, value, true);
    }

    public V putIfAbsent(K key, V value) {
        return put(key, value, false);
    }

    final private boolean replaceWithRightmost(AVLNode toReplaceInNode){
        AVLNode currentNode = toReplaceInNode.left;
        int replacePos = 0;            
        while (currentNode.right != null) {
            replacePos = replacePos + 1;
            currentNode = currentNode.right;
        }
        toReplaceInNode.key = currentNode.key;
        toReplaceInNode.value = currentNode.value;
        if(currentNode.parent.right == currentNode){
            currentNode.parent.right = currentNode.left;
        }else{
            currentNode.parent.left = currentNode.left;
        }
        if(currentNode.left != null){
            currentNode.left.parent = currentNode.parent;
        }
        boolean continueBalance = true;
        currentNode = currentNode.parent;
        while (replacePos > 0 && continueBalance) {
            AVLNode operateOn = currentNode;
            currentNode = currentNode.parent;
            replacePos = replacePos - 1;
            continueBalance = deleteBalanceRight(operateOn);
        }
        return continueBalance;
    }

    final private boolean deleteBalanceLeft(AVLNode currentNode){
        boolean continueBalance = true;
        if(currentNode.balance == -1){
            currentNode.balance = 0;
        }else if(currentNode.balance == 0){
           currentNode.balance = 1;
           continueBalance = false;
        }else{
            AVLNode currentNodeParent = currentNode.parent;
            AVLNode rightChild = currentNode.right;
            int rightChildBalance = rightChild.balance; 
            if (rightChildBalance >= 0) { //Single RR rotation
                rotateRight(currentNode);
                if(rightChildBalance == 0){
                    currentNode.balance = 1;
                    rightChild.balance = -1;
                    continueBalance = false;
                }
            } else { //Double LR rotation
                AVLNode rightChildLeftChild = rightChild.left;
                int rightChildLeftChildBalance = rightChildLeftChild.balance;
                rightChild.left = rightChildLeftChild.right;
                if(rightChildLeftChild.right != null){
                    rightChildLeftChild.right.parent = rightChild;
                }
                rightChildLeftChild.right = rightChild;
                rightChild.parent = rightChildLeftChild;
                currentNode.right = rightChildLeftChild.left;
                if(rightChildLeftChild.left != null){
                    rightChildLeftChild.left.parent = currentNode;
                }
                rightChildLeftChild.left = currentNode;
                currentNode.parent = rightChildLeftChild;
                currentNode.balance = (rightChildLeftChildBalance == 1) ? -1 : 0;
                rightChild.balance = (rightChildLeftChildBalance == -1) ? 1 : 0;
                rightChildLeftChild.balance = 0;
                if(currentNodeParent == null){
                    root = rightChildLeftChild;
                }else if(currentNodeParent.left == currentNode){
                    currentNodeParent.left = rightChildLeftChild;
                }else{
                    currentNodeParent.right = rightChildLeftChild;
                }
                rightChildLeftChild.parent = currentNodeParent;
            }
        }
        return continueBalance;
    }

    final private boolean deleteBalanceRight(AVLNode currentNode){
        boolean continueBalance = true;
        if(currentNode.balance == 1){
            currentNode.balance = 0;
        }else if(currentNode.balance == 0){
           currentNode.balance = -1;
           continueBalance = false;
        }else{
            AVLNode currentNodeParent = currentNode.parent;
            AVLNode leftChild = currentNode.left;
            int leftChildBalance = leftChild.balance; 
            if (leftChildBalance <= 0) { //Single LL rotation
                rotateLeft(currentNode);
                if(leftChildBalance == 0){
                    currentNode.balance = -1;
                    leftChild.balance = 1;
                    continueBalance = false;
                }
            } else { //Double LR rotation
                AVLNode leftChildRightChild = leftChild.right;
                int leftChildRightChildBalance = leftChildRightChild.balance;
                leftChild.right = leftChildRightChild.left;
                if(leftChildRightChild.left != null){
                    leftChildRightChild.left.parent = leftChild;//null pointer exeception
                }
                leftChildRightChild.left = leftChild;
                leftChild.parent = leftChildRightChild;
                currentNode.left = leftChildRightChild.right;
                if(leftChildRightChild.right != null){
                    leftChildRightChild.right.parent = currentNode;//null pointer exception
                }
                leftChildRightChild.right = currentNode;
                currentNode.parent = leftChildRightChild;
                currentNode.balance = (leftChildRightChildBalance == -1) ? 1 : 0;
                leftChild.balance = (leftChildRightChildBalance == 1) ? -1 : 0;
                leftChildRightChild.balance = 0;
                if(currentNodeParent == null){
                    root = leftChildRightChild;
                }else if(currentNodeParent.left == currentNode){
                    currentNodeParent.left = leftChildRightChild;
                }else{
                    currentNodeParent.right = leftChildRightChild;
                }
                leftChildRightChild.parent = currentNodeParent;
            }
        }
        return continueBalance;
    }

    public V remove(Object keyParam){
        boolean dirLeft = true;
        if(DEBUG) avlValidateP(root);
        AVLNode currentNode = root;
        if(comparator != null){
            @SuppressWarnings("unchecked")
            K key = (K)keyParam;
            Comparator<? super K> cpr = comparator;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = cpr.compare(key, nodeKey);
                if(compareValue < 0) {
                    dirLeft = true;
                    currentNode = currentNode.left;
                } else if (compareValue > 0) {
                    dirLeft = false;
                    currentNode = currentNode.right;
                } else {
                    size = size - 1;
                    break;
                }
            }
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) keyParam;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = key.compareTo(nodeKey);
                if(compareValue < 0) {
                    dirLeft = true;
                    currentNode = currentNode.left;
                } else if (compareValue > 0) {
                    dirLeft = false;
                    currentNode = currentNode.right;
                } else {
                    size = size - 1;
                    break;
                }
            }
        }
        V toReturn = null;
        if(currentNode == null){
            if(DEBUG) avlValidateP(root);
            return null;
        }else{
            toReturn = currentNode.value;
        }
        //Fix balance
        AVLNode prevNode = currentNode.parent;
        boolean continueFix = true;
        if(currentNode.left == null){
            if(prevNode == null){
                root = currentNode.right;
            }else if(dirLeft){
                prevNode.left = currentNode.right;
            }else{
                prevNode.right = currentNode.right;

            }
            if(currentNode.right != null){
                currentNode.right.parent = prevNode;
            }
            currentNode = currentNode.right;
        }else if(currentNode.right == null){
            if(prevNode == null){
                root = currentNode.left;
            }else if(dirLeft){
                prevNode.left = currentNode.left;
            }else{
                prevNode.right = currentNode.left;
            }
            if(currentNode.left != null){
                currentNode.left.parent = prevNode;
            }
            currentNode = currentNode.left;
        }else{
            if(prevNode == null){
                continueFix = replaceWithRightmost(currentNode);
                currentNode = root.left;
                prevNode = root;
            }else if(prevNode.left == currentNode){
                continueFix = replaceWithRightmost(currentNode);
                prevNode = prevNode.left;
                currentNode = prevNode.left;
                dirLeft = true;
            }else{
                continueFix = replaceWithRightmost(currentNode);
                prevNode = prevNode.right;
                currentNode = prevNode.left;
                dirLeft = true;
            }
        }
        //current node is the node we are comming from
        //prev node is the node that needs rebalancing
        while (continueFix && prevNode != null) {
            AVLNode nextPrevNode = prevNode.parent;
            if(nextPrevNode != null){
                boolean findCurrentLeftDir = true;
                if(nextPrevNode.left == prevNode){
                    findCurrentLeftDir = true;
                }else{
                    findCurrentLeftDir = false;
                }
                if(currentNode == null){
                    if (dirLeft) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }else{
                    if (prevNode.left == currentNode) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }
                if(findCurrentLeftDir){
                    currentNode = nextPrevNode.left;
                }else{
                    currentNode = nextPrevNode.right;
                }
                prevNode = nextPrevNode;
            }else{
                if(currentNode == null){
                    if (dirLeft) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }else{
                    if (prevNode.left == currentNode) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }
                prevNode = null;
            }
        }
        if(DEBUG) avlValidateP(root);
        return toReturn;
    }

    public void clear(){
        size = 0;
        root = null;
    }

    final private void addAllToList(final AVLNode node, LinkedList<Map.Entry<K, V>> list){
        if(node!=null){
            addAllToList(node.left, list);
            AbstractMap.SimpleImmutableEntry<K,V> entry = new AbstractMap.SimpleImmutableEntry<K,V>(node.key, node.value){
                public int hashCode(){
                    return node.key.hashCode();
                }
            };
            list.add(entry);
            addAllToList(node.right, list);
        }
    } 

    final protected void addAllToList(LinkedList<Map.Entry<K, V>> list){
        addAllToList(root, list);
    } 

    //Set<K> keySet();
    //Collection<V> values();
    public Set<Map.Entry<K, V>> entrySet(){
        LinkedList<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>();
        addAllToList(root, list);
        return new HashSet<Map.Entry<K, V>>(list);
    }
    //boolean equals(Object o);
    //int hashCode();
}
