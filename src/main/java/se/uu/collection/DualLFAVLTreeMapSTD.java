package se.uu.collection;
import java.util.Random;
import java.lang.reflect.Field;
import java.util.*;
import java.io.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.StampedLock;
import algorithms.published.LockFreeKSTRQOld;
import java.nio.ByteBuffer;
import sun.misc.Unsafe;

final class STDAVLNodeD<K extends Comparable<? super K>,V>{

    private static final AtomicReferenceFieldUpdater<STDAVLNodeD, STDAVLNodeD> leftUpdater =
        AtomicReferenceFieldUpdater.newUpdater(STDAVLNodeD.class, STDAVLNodeD.class, "left");
    private static final AtomicReferenceFieldUpdater<STDAVLNodeD, STDAVLNodeD> rightUpdater =
        AtomicReferenceFieldUpdater.newUpdater(STDAVLNodeD.class, STDAVLNodeD.class, "right");


    K key;
    private volatile STDAVLNodeD<K,V> left;
    private volatile STDAVLNodeD<K,V> right;
    V value;
    int balance = 0;
    STDAVLNodeD<K,V> parent = null;
    public STDAVLNodeD(K key, V value){
        this.key = key;
        this.value = value;
    }

    public STDAVLNodeD<K,V> getLeft(){
        return left;
    }

    public STDAVLNodeD<K,V> getRight(){
        return right;
    }


    public void setLeft(STDAVLNodeD<K,V> n){
        leftUpdater.lazySet(this, n);
    }

    public void setRight(STDAVLNodeD<K,V> n){
        rightUpdater.lazySet(this, n);
    }


    public String toString(){
        return "NODE(" + key + ", " + balance + ")";
    }
}


public class DualLFAVLTreeMapSTD<K extends Comparable<? super K>, V> extends AbstractMap<K,V> implements SplitableAndJoinableMap<K, V>, Invalidatable, AnyKeyProviding<K>{


    private final StampedLock lock = new StampedLock();
    private final Lock wlock = lock.asWriteLock();
    private final Lock rlock = lock.asReadLock();
    private int statLockStatistics = 0;
    private volatile boolean valid = true;
    private int size = 0;
    //Use setRoot and getRoot to access the root
    private volatile STDAVLNodeD<K,V> theRoot = null;
    private Object parent = null;
    private final Comparator<? super K> comparator;
    private static final AtomicReferenceFieldUpdater<DualLFAVLTreeMapSTD, STDAVLNodeD> rootUpdater =
        AtomicReferenceFieldUpdater.newUpdater(DualLFAVLTreeMapSTD.class, STDAVLNodeD.class, "theRoot");
    private boolean currentlyAVLTree = true;
    private volatile LockFreeKSTRQOld<K, V> lockFreeHolder = null;
    private final byte[] localStatisticsArray = new byte[128*2 + (int)LOCAL_STATISTICS_ARRAY_SLOT_SIZE*Runtime.getRuntime().availableProcessors()];
    private static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
    private static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
    private static final int STAT_LOCK_FAILURE_CONTRIB = 250;
    private static final int STAT_LOCK_SUCCESS_CONTRIB = 1;

    private static final Unsafe unsafe;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception ex) { 
            throw new Error(ex);
        }
    }    

    private static final int NUMBER_OF_LOCAL_STATISTICS_SLOTS = Runtime.getRuntime().availableProcessors();
    private static final long LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET = 0;
    private static final long LOCAL_STATISTICS_ARRAY_SIZE_OFFSET = 8;
    private static final long LOCAL_STATISTICS_ARRAY_START_OFFSET = 
        128 + unsafe.ARRAY_BYTE_BASE_OFFSET;
    private static final long LOCAL_STATISTICS_ARRAY_SLOT_SIZE = 192;





    // ====== FOR DEBUGING ======
    private final static boolean DEBUG = false;
    // ==========================

    public STDAVLNodeD<K,V> getRoot(){
        return theRoot;
    }    
    
    public void setRoot(STDAVLNodeD<K,V> n){
        rootUpdater.lazySet(this, n);
    }

    //==== Functions for debuging and testing
    
    public String toString(){
        return "B(" + getRoot() + ", " + isValid() + "," + getStatistics() + "," + getParent() + ","+size()+")";
    }

    final private int avlValidateP(STDAVLNodeD<K,V> toTest){
        
        if(toTest != null && toTest.parent != null){
            System.out.println("Parent should be null\n");
            printDot(getRoot(), "parent_should_be_null");
            throw new RuntimeException();
        }
        return avlValidate(toTest);
    }
    final private int avlValidate(STDAVLNodeD<K,V> toTest){
        if(toTest == null){
            return 0;
        }else{
            int hl = avlValidate(toTest.getLeft());
            if(toTest.getLeft() != null && toTest.getLeft().parent != toTest){
                System.out.println("WRONG PARENT\n");
                printDot(getRoot(), "wrong_parent");
                throw new RuntimeException();
            }
            int hr = avlValidate(toTest.getRight());
            if(toTest.getRight() != null && toTest.getRight().parent != toTest){
                System.out.println("WRONG PARENT\n");
                printDot(getRoot(), "wrong_parent");
                throw new RuntimeException();
            }
            if(toTest.balance == 0 && hl != hr){
                System.out.println("FAIL 1 "+hl+" " +hr+"\n");
                printDot(getRoot(), "fail1");
                throw new RuntimeException();
            }else if(toTest.balance == -1 && (hr - hl) != -1){
                System.out.println("FAIL 2\n");
                printDot(getRoot(), "fail2");
                throw new RuntimeException();
            }else if(toTest.balance == 1 && (hr - hl) != 1){
                System.out.println("FAIL 3 "+(hr - hl)+"\n");
                printDot(getRoot(), "fail3");
                throw new RuntimeException();
            }else if(toTest.balance > 1 || toTest.balance < -1){
                System.out.println("FAIL 4\n");
                printDot(getRoot(), "fail4");
                throw new RuntimeException();
            }
            if(hl > hr){
                return hl + 1;
            }else{
                return hr + 1;
            }
        }
    }

    void printDotHelper(STDAVLNodeD<K,V> node, PrintStream writeTo){
        Random rand = new Random();
        try{
            if(node!=null){
                if(node.getLeft() !=null){
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + node.getLeft().value + ", " + node.getLeft().balance + ", " + (node.getLeft().parent != null ? node.getLeft().parent.key : null) + " \"");
                    writeTo.println(";");
                }else{
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + null+ ", " + rand.nextInt() + " \"");
                    writeTo.println(";");
                }
                if(node.getRight() !=null){
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + node.getRight().value + ", " + node.getRight().balance + ", " + (node.getRight().parent != null ? node.getRight().parent.key : null) + " \"");
                    writeTo.println(";");
                }else{
                    writeTo.print("\"" + node.value + ", " + node.balance + ", " + (node.parent != null ? node.parent.key : null) + " \"");
                    writeTo.print(" -> ");
                    writeTo.print("\"" + null+ ", " + rand.nextInt() + " \"");
                    writeTo.println(";");
                }
                printDotHelper(node.getLeft(), writeTo);
                printDotHelper(node.getRight(), writeTo);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    void printDot(STDAVLNodeD<K,V> node, String fileName){
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

    public DualLFAVLTreeMapSTD() {
        comparator = null;
    }

    public DualLFAVLTreeMapSTD(Comparator<? super K> comparator) {
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
        if(getRoot() != null){
            return getRoot().key;
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
        if(wlock.tryLock()){
            if(isLockFreeMode()){
                transformToLocked();
            }
            return true;
        }else{
            return false;
        }
    }
    
    public void lock(){
        if (wlock.tryLock()) {
            statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
            if(isLockFreeMode()){
                transformToLocked();
            }
            return;
        }
        wlock.lock();
        if(isLockFreeMode()){
            transformToLocked();
        }
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void addToContentionStatistics(){
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void unlock(){
        wlock.unlock();
    }

    public void readLock(){
        rlock.lock();
    }

    public void readUnlock(){
        rlock.unlock();
    }


    public long getOptimisticReadToken(){
        return lock.tryOptimisticRead();
    }

    public boolean validateOptimisticReadToken(long optimisticReadToken){
        return lock.validate(optimisticReadToken);
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


    //=== Local statistics array functions ========

    private void resetLocalStatisticsArray(){
        for(int i = 0; i < NUMBER_OF_LOCAL_STATISTICS_SLOTS; i++){
        // long writeIndicatorOffset = 
        //     LOCAL_STATISTICS_ARRAY_START_OFFSET + 
        //     i * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
        //     LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        long sizeOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            i * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_SIZE_OFFSET;

        //unsafe.putLong(localStatisticsArray, writeIndicatorOffset, 0);
        unsafe.putLong(localStatisticsArray, sizeOffset, 0);
        }
    }

    public void indicateWriteStart(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;
        long writeIndicatorOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        long prevValue;
        do{
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               writeIndicatorOffset);
        }while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                          writeIndicatorOffset,
                                          prevValue,
                                          prevValue + 1));
    }

    public void indicateWriteEnd(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;
        long writeIndicatorOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        long prevValue;
        do{
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               writeIndicatorOffset);
        }while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                          writeIndicatorOffset,
                                          prevValue,
                                          prevValue - 1));
    }


    public long increaseLocalSize(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;
        long sizeOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_SIZE_OFFSET;
        long prevValue;
        do{
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               sizeOffset);
        }while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                          sizeOffset,
                                          prevValue,
                                          prevValue + 1));
        return prevValue + 1;
    }

    public long decreaseLocalSize(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;
        long sizeOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_SIZE_OFFSET;
        long prevValue;
        do{
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               sizeOffset);
        }while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                          sizeOffset,
                                          prevValue,
                                          prevValue - 1));
        return prevValue -1;
    }

    public long readLocalSizeSum(){
        long sum = 0;
        for(int i = 0; i < NUMBER_OF_LOCAL_STATISTICS_SLOTS; i++){
            long sizeOffset = 
                LOCAL_STATISTICS_ARRAY_START_OFFSET + 
                i * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
                LOCAL_STATISTICS_ARRAY_SIZE_OFFSET;
            
            sum = sum + unsafe.getLongVolatile(localStatisticsArray, sizeOffset);
        }
        return sum;
    }


    public void waitNoOngoingWrite(){
        long currentOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        for(int i = 0; i < NUMBER_OF_LOCAL_STATISTICS_SLOTS; i++){
            while(0 != unsafe.getLongVolatile(localStatisticsArray,
                                              currentOffset)){
                Thread.yield();
            }
            currentOffset = currentOffset + LOCAL_STATISTICS_ARRAY_SLOT_SIZE;
        }
    }

    //=== Sorted Set Functions ====================

    private final int computeHeight(){
        STDAVLNodeD<K,V> r = getRoot();
        if(r == null){
            return 0;
        } else {
            STDAVLNodeD<K,V> currentNode = r;
            int hightSoFar = 1;
            while(currentNode.getLeft() != null || currentNode.getRight() != null){
                if(currentNode.balance == -1){
                    currentNode = currentNode.getLeft();
                }else{
                    currentNode = currentNode.getRight();
                }
                hightSoFar = hightSoFar + 1;
            }
            return hightSoFar;
        }
    }

    private final K minKey(){
        STDAVLNodeD<K,V> currentNode = getRoot();
        if(currentNode == null){
            return null;
        }
        while(currentNode.getLeft() != null){
            currentNode = currentNode.getLeft();
        }
        return currentNode.key;
    }


    private final K maxKey(){
        STDAVLNodeD<K,V> currentNode = getRoot();
        if(currentNode == null){
            return null;
        }
        while(currentNode.getRight() != null){
            currentNode = currentNode.getRight();
        }
        return currentNode.key;
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
        STDAVLNodeD<K,V> prevNode = null;//f
        STDAVLNodeD<K,V> currentNode = null;//f
        DualLFAVLTreeMapSTD<K, V> newTree = null;
        if(comparator == null){
            newTree = new DualLFAVLTreeMapSTD<K, V>();
        }else{
            newTree = new DualLFAVLTreeMapSTD<K, V>(comparator);
        }
        DualLFAVLTreeMapSTD<K,V> leftTree = this;
        DualLFAVLTreeMapSTD<K,V> rightTree = (DualLFAVLTreeMapSTD<K,V>)right;
        if(leftTree.getRoot() == null){
            newTree.setRoot(rightTree.getRoot());
            newTree.size = rightTree.size + leftTree.size;
            return newTree;
        }else if(rightTree.getRoot() == null){
            newTree.setRoot(leftTree.getRoot());
            newTree.size = leftTree.size + rightTree.size;
            return newTree;
        }
        int leftHeight = leftTree.computeHeight();
        int rightHeight = rightTree.computeHeight();
        if(leftHeight >= rightHeight){
            K minKey = rightTree.minKey();
            V minValue = rightTree.remove(minKey);
            rightTree.size = rightTree.size + 1;
            STDAVLNodeD<K,V> newRoot = new STDAVLNodeD<K,V>(minKey, minValue);
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
            currentNode = leftTree.getRoot();
            int currentHeight = leftHeight;
            while(currentHeight > newRightHeight + 1){
                if(currentNode.balance == -1){
                    currentHeight = currentHeight - 2;
                }else{
                    currentHeight = currentHeight - 1;
                }
                prevNode = currentNode;
                currentNode = currentNode.getRight();
            }
            STDAVLNodeD<K,V> oldCurrentNodeParent = prevNode;
            newRoot.setLeft(currentNode);
            if(currentNode != null){
                currentNode.parent = newRoot;
            }
            newRoot.setRight(rightTree.getRoot());
            if(rightTree.getRoot() != null){
                rightTree.getRoot().parent = newRoot;
            }
            newRoot.balance = newRightHeight - currentHeight;

            if(oldCurrentNodeParent == null){//Check if this can happen at all
                newTree.setRoot(newRoot);
            }else if(oldCurrentNodeParent.getLeft() == currentNode){
                oldCurrentNodeParent.setLeft(newRoot);
                newRoot.parent = oldCurrentNodeParent;
                newTree.setRoot(leftTree.getRoot());
            }else{
                oldCurrentNodeParent.setRight(newRoot);
                newRoot.parent = oldCurrentNodeParent;
                newTree.setRoot(leftTree.getRoot());
            }
            currentNode = newRoot;
        }else{
            //This case is symetric to the previous case
            K maxKey = leftTree.maxKey();//f
            V maxValue = leftTree.remove(maxKey);//f
            leftTree.size = leftTree.size + 1;
            STDAVLNodeD<K,V> newRoot = new STDAVLNodeD<K,V>(maxKey, maxValue);//f
            int newLeftHeight = leftTree.computeHeight();//f
            prevNode = null;//f
            currentNode = rightTree.getRoot();//f
            int currentHeight = rightHeight;//f
            while(currentHeight > newLeftHeight + 1){//f
                if(currentNode.balance == 1){//f
                    currentHeight = currentHeight - 2;//f
                }else{
                    currentHeight = currentHeight - 1;//f
                }
                prevNode = currentNode;//f
                currentNode = currentNode.getLeft();//f
            }
            STDAVLNodeD<K,V> oldCurrentNodeParent = prevNode;//f
            newRoot.setRight(currentNode);//f
            if(currentNode != null){
                currentNode.parent = newRoot;//f
            }
            newRoot.setLeft(leftTree.getRoot());//f
            if(leftTree.getRoot() != null){
                leftTree.getRoot().parent = newRoot;//f
            }
            newRoot.balance = currentHeight - newLeftHeight;//f
            if(oldCurrentNodeParent == null){//Check if this can happen at all
                newTree.setRoot(newRoot);
            }else if(oldCurrentNodeParent.getLeft() == currentNode){
                oldCurrentNodeParent.setLeft(newRoot);
                newRoot.parent = oldCurrentNodeParent;
                newTree.setRoot(rightTree.getRoot());
            }else{
                oldCurrentNodeParent.setRight(newRoot);
                newRoot.parent = oldCurrentNodeParent;
                newTree.setRoot(rightTree.getRoot());
            }
            currentNode = newRoot;
        }
        //Now we need to continue as if this was during the insert 
        while(prevNode != null){
            if(prevNode.getLeft() == currentNode){
                if(prevNode.balance == -1){
                    STDAVLNodeD<K,V> leftChild = prevNode.getLeft();
                    //Need to rotate
                    if(leftChild.balance == -1){
                        newTree.rotateLeft(prevNode);
                    }else{
                        newTree.rotateDoubleRight(prevNode);
                    }
                    newTree.size = leftTree.size + rightTree.size;
                    if(DEBUG) avlValidateP(newTree.getRoot());
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
                    STDAVLNodeD<K,V> rightChild = prevNode.getRight();
                    //Need to rotate
                    if(rightChild.balance == 1){
                        newTree.rotateRight(prevNode);
                    }else{
                        newTree.rotateDoubleLeft(prevNode);
                    }
                    newTree.size = leftTree.size + rightTree.size;
                    if(DEBUG) avlValidateP(newTree.getRoot());
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
        if(DEBUG) avlValidateP(newTree.getRoot());
        return newTree;
    }

    public SplitableAndJoinableMap<K, V> split(Object[] splitKeyWriteBack,
                                               SplitableAndJoinableMap<K, V>[] rightTreeWriteBack){
        STDAVLNodeD<K,V> leftRoot = null;
        STDAVLNodeD<K,V> rightRoot = null;
        if(getRoot() == null){
            return null;
        }else if(getRoot().getLeft() == null && getRoot().getRight() == null){
            return null;
        }else if(getRoot().getLeft() == null){
            splitKeyWriteBack[0] = getRoot().getRight().key;
            rightRoot = getRoot().getRight();
            rightRoot.parent = null;
            rightRoot.balance = 0;
            getRoot().setRight(null);
            leftRoot = getRoot();
            leftRoot.balance = 0;
        }else{
            splitKeyWriteBack[0] = getRoot().key;
            leftRoot = getRoot().getLeft();
            leftRoot.parent = null;
            getRoot().setLeft(null);
            if (getRoot().getRight() == null){
                rightRoot = getRoot();
                rightRoot.balance = 0;
            }else{
                K insertKey = getRoot().key;
                V insertValue = getRoot().value;
                setRoot(getRoot().getRight());
                getRoot().parent = null;
                put(insertKey, insertValue);
                size = size - 1;
                rightRoot = getRoot();
            }
        }
        DualLFAVLTreeMapSTD<K,V> leftTree = null;
        if(comparator == null){
            leftTree = new DualLFAVLTreeMapSTD<K, V>();
        }else{
            leftTree = new DualLFAVLTreeMapSTD<K, V>(comparator);
        }
        leftTree.setRoot(leftRoot);
        DualLFAVLTreeMapSTD<K,V> rightTree = null;
        if(comparator == null){
            rightTree = new DualLFAVLTreeMapSTD<K, V>();
        }else{
            rightTree = new DualLFAVLTreeMapSTD<K, V>(comparator);
        }
        rightTree.setRoot(rightRoot);
        int remainder = size % 2;
        int aproxSizes = size / 2;
        leftTree.size = aproxSizes;
        rightTree.size = aproxSizes + remainder;
        rightTreeWriteBack[0] = rightTree;
        if(DEBUG) {
            avlValidateP(leftTree.getRoot());
            avlValidateP(rightTree.getRoot());
        }
        //        System.err.println("S"+aproxSizes);
        //printDot(leftTree.root, "left");
        //printDot(rightTree.root, "right");
        //System.exit(0);
        return leftTree;
    }

    private int countNodes(STDAVLNodeD n){
        if(n == null) return 0;
        else return 1 + countNodes(n.getLeft()) + countNodes(n.getRight());
    }


    public void transformToLockFree(){
        lockFreeHolder = new LockFreeKSTRQOld<K, V>(8);
        if(theRoot == null){
            return;
        }
        // breath fist traversal to insert all elements into the LF tree
        int startIndex = 0;
        int endIndex = 0;
        STDAVLNodeD<K,V>[] queue =
            new STDAVLNodeD[countNodes(theRoot)];
        queue[startIndex] = theRoot;//Enque root
        startIndex++;
        int numberOfElementsInserted = 0;
        while(startIndex != endIndex){
            STDAVLNodeD<K,V> currentNode = queue[endIndex];
            endIndex++;
            lockFreeHolder.put(currentNode.key, currentNode.value);
            numberOfElementsInserted++;
            if(currentNode.getLeft() != null){
                queue[startIndex] = currentNode.getLeft();//Enqueu root
                startIndex++;
            }
            if(currentNode.getRight() != null){
                queue[startIndex] = currentNode.getRight();//Enqueu root
                startIndex++;
            }
        }
        size = size - numberOfElementsInserted;
        theRoot = null;
        resetStatistics(); //Reset statistics so it will not be converted too quickly
        resetLocalStatisticsArray();
    }

    public void transformToLocked(){
        waitNoOngoingWrite();
        // breath fist traversal to insert all elements into the AVL tree
        int startIndex = 0;
        int endIndex = 0;
        LockFreeKSTRQOld.Node<K,V>[] queue = new LockFreeKSTRQOld.Node[lockFreeHolder.nodeCount()];
        queue[startIndex] = (LockFreeKSTRQOld.Node<K,V>)lockFreeHolder.getRoot().c.get(0);//Enqueue root
        startIndex++;
        while(startIndex != endIndex){
            LockFreeKSTRQOld.Node<K,V> currentNode = queue[endIndex];
            endIndex++;
            if(currentNode.c == null){
                //Leaf node
                for(int i = 0; i < currentNode.kcount; i++){
                    put((K)currentNode.k[i], (V)currentNode.v[i]); 
                }
            }else{
                //Iterate through children
                for(int i = 0; i < currentNode.c.length(); i++){
                    LockFreeKSTRQOld.Node<K,V> c = currentNode.c.get(i);
                    queue[startIndex] = c;//Enqueu root
                    startIndex++;
                }
            }
        }
        resetStatistics(); //Reset statistics so it will not be converted too quickly
        lockFreeHolder = null;
    }

    public boolean isLockFreeMode(){
        return lockFreeHolder != null;
    }

    public LockFreeKSTRQOld<K, V> getLockFreeMap(){
        return lockFreeHolder;
    }

    public int size(){
        return size;
    }

    public boolean isEmpty(){
        return getRoot() == null;
    }

    final private STDAVLNodeD<K,V> getSTDAVLNodeDUsingComparator(Object keyParam) {
        @SuppressWarnings("unchecked")
        K key = (K) keyParam;
        STDAVLNodeD<K,V> currentNode = getRoot();
        Comparator<? super K> cpr = comparator;
        while(currentNode != null){
            K nodeKey = currentNode.key;
            int compareValue = cpr.compare(key,nodeKey);
            if(compareValue < 0) {
                currentNode = currentNode.getLeft();
            } else if (compareValue > 0) {
                currentNode = currentNode.getRight();
            } else {
                return currentNode;
            }
        }
        return null;
    }

    final private STDAVLNodeD<K,V> getSTDAVLNodeD(Object keyParam){
        if(comparator != null){
            return getSTDAVLNodeDUsingComparator(keyParam);
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) keyParam;
            STDAVLNodeD<K,V> currentNode = getRoot();
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = key.compareTo(nodeKey);
                if(compareValue < 0) {
                    currentNode = currentNode.getLeft();
                } else if (compareValue > 0) {
                    currentNode = currentNode.getRight();
                } else {
                    return currentNode;
                }
            }
            return null;
        }
    }

    public boolean containsKey(Object key){
        return getSTDAVLNodeD(key) != null;
    }

    private V getLocked(Object key){
        STDAVLNodeD<K,V> node = getSTDAVLNodeD(key);
        if(node != null){
            return node.value;
        }else{
            return null;
        }
    }

    public V get(Object key){
        LockFreeKSTRQOld<K, V> lfMap = getLockFreeMap();
        V result;
        if(lfMap == null) result = getLocked(key);
        else result = lfMap.get((K)key);
        return result;
    }

    final private void rotateLeft(STDAVLNodeD<K,V> prevNode){
        //Single left rotation
        STDAVLNodeD<K,V> leftChild = prevNode.getLeft();
        STDAVLNodeD<K,V> prevNodeParent = prevNode.parent;
        prevNode.setLeft(leftChild.getRight());
        if(prevNode.getLeft() != null){
            prevNode.getLeft().parent = prevNode;
        }
        leftChild.setRight(prevNode);
        prevNode.parent = leftChild;
        prevNode.balance = 0;
        if(prevNodeParent == null){
            setRoot(leftChild);
        }else if(prevNodeParent.getLeft() == prevNode){
            prevNodeParent.setLeft(leftChild);
        }else{
            prevNodeParent.setRight(leftChild);
        }
        leftChild.parent = prevNodeParent;
        leftChild.balance = 0;
    }

    final private void rotateRight(STDAVLNodeD<K,V> prevNode){
        //Single left rotation
        STDAVLNodeD<K,V> rightChild = prevNode.getRight();
        STDAVLNodeD<K,V> prevNodeParent = prevNode.parent;
        prevNode.setRight(rightChild.getLeft());
        if(prevNode.getRight() != null){
            prevNode.getRight().parent = prevNode;
        }
        rightChild.setLeft(prevNode);
        prevNode.parent = rightChild;
        prevNode.balance = 0;
        if(prevNodeParent == null){
            setRoot(rightChild);
        }else if(prevNodeParent.getLeft() == prevNode){
            prevNodeParent.setLeft(rightChild);
        }else{
            prevNodeParent.setRight(rightChild);
        }
        rightChild.parent = prevNodeParent;
        rightChild.balance = 0;
    }


    final private void rotateDoubleRight(STDAVLNodeD<K,V> prevNode){
        STDAVLNodeD<K,V> prevNodeParent = prevNode.parent;
        STDAVLNodeD<K,V> leftChild = prevNode.getLeft();
        STDAVLNodeD<K,V> leftChildRightChild = leftChild.getRight();

        leftChild.setRight(leftChildRightChild.getLeft());
        if(leftChildRightChild.getLeft() != null){
            leftChildRightChild.getLeft().parent = leftChild;
        }

        leftChildRightChild.setLeft(leftChild);
        leftChild.parent = leftChildRightChild;

        prevNode.setLeft(leftChildRightChild.getRight());
        if(leftChildRightChild.getRight() != null){
            leftChildRightChild.getRight().parent = prevNode;
        }
        leftChildRightChild.setRight(prevNode);
        prevNode.parent = leftChildRightChild;

        prevNode.balance = (leftChildRightChild.balance == -1) ? +1 : 0;
        leftChild.balance = (leftChildRightChild.balance == 1) ? -1 : 0;
        if(prevNodeParent == null){
            setRoot(leftChildRightChild);
        }else if(prevNodeParent.getLeft() == prevNode){
            prevNodeParent.setLeft(leftChildRightChild);
        }else{
            prevNodeParent.setRight(leftChildRightChild);
        }
        leftChildRightChild.parent = prevNodeParent;
        leftChildRightChild.balance = 0;
    }

    final private void rotateDoubleLeft(STDAVLNodeD<K,V> prevNode){
        STDAVLNodeD<K,V> prevNodeParent = prevNode.parent;
        STDAVLNodeD<K,V> rightChild = prevNode.getRight();
        STDAVLNodeD<K,V> rightChildLeftChild = rightChild.getLeft();
        rightChild.setLeft(rightChildLeftChild.getRight());
        if(rightChildLeftChild.getRight() != null){
            rightChildLeftChild.getRight().parent = rightChild;
        }

        rightChildLeftChild.setRight(rightChild);
        rightChild.parent = rightChildLeftChild;

        prevNode.setRight(rightChildLeftChild.getLeft());
        if(rightChildLeftChild.getLeft() != null){
            rightChildLeftChild.getLeft().parent = prevNode;
        }

        rightChildLeftChild.setLeft(prevNode);
        prevNode.parent = rightChildLeftChild;

        prevNode.balance = (rightChildLeftChild.balance == 1) ? -1 : 0;
        rightChild.balance = (rightChildLeftChild.balance == -1) ? 1 : 0;
        if(prevNodeParent == null){
            setRoot(rightChildLeftChild);
        }else if(prevNodeParent.getLeft() == prevNode){
            prevNodeParent.setLeft(rightChildLeftChild);
        }else{
            prevNodeParent.setRight(rightChildLeftChild);
        }
        rightChildLeftChild.parent = prevNodeParent;
        rightChildLeftChild.balance = 0;
    }

    private V put(K keyParam, V value, boolean replace){
        if(DEBUG) avlValidateP(getRoot());
        STDAVLNodeD<K,V> prevNode = null;
        STDAVLNodeD<K,V> currentNode = getRoot();
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
                    currentNode = currentNode.getLeft();
                } else if (compareValue > 0) {
                    dirLeft = false;
                    prevNode = currentNode;
                    currentNode = currentNode.getRight();
                } else {
                    V prevValue = currentNode.value;
                    if(replace){
                        currentNode.value = value;
                    }
                    if(DEBUG) avlValidateP(getRoot());
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
                    currentNode = currentNode.getLeft();
                } else if (compareValue > 0) {
                    dirLeft = false;
                    prevNode = currentNode;
                    currentNode = currentNode.getRight();
                } else {
                    V prevValue = currentNode.value;
                    currentNode.value = value;
                    if(DEBUG) avlValidateP(getRoot());
                    return prevValue;
                }
            }
        }

        //Insert node
        size = size + 1;
        currentNode = new STDAVLNodeD<K,V>(keyParam, value);
        if(prevNode == null){
            setRoot(currentNode);
        }else if(dirLeft){
            prevNode.setLeft(currentNode);
        }else{
            prevNode.setRight(currentNode);
        }
        currentNode.parent = prevNode;
        //Balance
        while(prevNode != null){         
            if(prevNode.getLeft() == currentNode){
                if(prevNode.balance == -1){
                    STDAVLNodeD<K,V> leftChild = prevNode.getLeft();
                    //Need to rotate
                    if(leftChild.balance == -1){
                        rotateLeft(prevNode);
                    }else{
                        rotateDoubleRight(prevNode);
                    }
                    if(DEBUG) avlValidateP(getRoot());
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
                    STDAVLNodeD<K,V> rightChild = prevNode.getRight();
                    //Need to rotate
                    if(rightChild.balance == 1){
                        rotateRight(prevNode);
                    }else{
                        rotateDoubleLeft(prevNode);
                    }
                    if(DEBUG) avlValidateP(getRoot());
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
        if(DEBUG) avlValidateP(getRoot());
        return null;
    }

    public V put(K key, V value){
        return put(key, value, true);
    }

    public V putIfAbsent(K key, V value) {
        return put(key, value, false);
    }

    final private boolean replaceWithRightmost(STDAVLNodeD<K,V> toReplaceInNode){
        STDAVLNodeD<K,V> currentNode = toReplaceInNode.getLeft();
        int replacePos = 0;            
        while (currentNode.getRight() != null) {
            replacePos = replacePos + 1;
            currentNode = currentNode.getRight();
        }
        toReplaceInNode.key = currentNode.key;
        toReplaceInNode.value = currentNode.value;
        if(currentNode.parent.getRight() == currentNode){
            currentNode.parent.setRight(currentNode.getLeft());
        }else{
            currentNode.parent.setLeft(currentNode.getLeft());
        }
        if(currentNode.getLeft() != null){
            currentNode.getLeft().parent = currentNode.parent;
        }
        boolean continueBalance = true;
        currentNode = currentNode.parent;
        while (replacePos > 0 && continueBalance) {
            STDAVLNodeD<K,V> operateOn = currentNode;
            currentNode = currentNode.parent;
            replacePos = replacePos - 1;
            continueBalance = deleteBalanceRight(operateOn);
        }
        return continueBalance;
    }

    final private boolean deleteBalanceLeft(STDAVLNodeD<K,V> currentNode){
        boolean continueBalance = true;
        if(currentNode.balance == -1){
            currentNode.balance = 0;
        }else if(currentNode.balance == 0){
           currentNode.balance = 1;
           continueBalance = false;
        }else{
            STDAVLNodeD<K,V> currentNodeParent = currentNode.parent;
            STDAVLNodeD<K,V> rightChild = currentNode.getRight();
            int rightChildBalance = rightChild.balance; 
            if (rightChildBalance >= 0) { //Single RR rotation
                rotateRight(currentNode);
                if(rightChildBalance == 0){
                    currentNode.balance = 1;
                    rightChild.balance = -1;
                    continueBalance = false;
                }
            } else { //Double LR rotation
                STDAVLNodeD<K,V> rightChildLeftChild = rightChild.getLeft();
                int rightChildLeftChildBalance = rightChildLeftChild.balance;
                rightChild.setLeft(rightChildLeftChild.getRight());
                if(rightChildLeftChild.getRight() != null){
                    rightChildLeftChild.getRight().parent = rightChild;
                }
                rightChildLeftChild.setRight(rightChild);
                rightChild.parent = rightChildLeftChild;
                currentNode.setRight(rightChildLeftChild.getLeft());
                if(rightChildLeftChild.getLeft() != null){
                    rightChildLeftChild.getLeft().parent = currentNode;
                }
                rightChildLeftChild.setLeft(currentNode);
                currentNode.parent = rightChildLeftChild;
                currentNode.balance = (rightChildLeftChildBalance == 1) ? -1 : 0;
                rightChild.balance = (rightChildLeftChildBalance == -1) ? 1 : 0;
                rightChildLeftChild.balance = 0;
                if(currentNodeParent == null){
                    setRoot(rightChildLeftChild);
                }else if(currentNodeParent.getLeft() == currentNode){
                    currentNodeParent.setLeft(rightChildLeftChild);
                }else{
                    currentNodeParent.setRight(rightChildLeftChild);
                }
                rightChildLeftChild.parent = currentNodeParent;
            }
        }
        return continueBalance;
    }

    final private boolean deleteBalanceRight(STDAVLNodeD<K,V> currentNode){
        boolean continueBalance = true;
        if(currentNode.balance == 1){
            currentNode.balance = 0;
        }else if(currentNode.balance == 0){
           currentNode.balance = -1;
           continueBalance = false;
        }else{
            STDAVLNodeD<K,V> currentNodeParent = currentNode.parent;
            STDAVLNodeD<K,V> leftChild = currentNode.getLeft();
            int leftChildBalance = leftChild.balance; 
            if (leftChildBalance <= 0) { //Single LL rotation
                rotateLeft(currentNode);
                if(leftChildBalance == 0){
                    currentNode.balance = -1;
                    leftChild.balance = 1;
                    continueBalance = false;
                }
            } else { //Double LR rotation
                STDAVLNodeD<K,V> leftChildRightChild = leftChild.getRight();
                int leftChildRightChildBalance = leftChildRightChild.balance;
                leftChild.setRight(leftChildRightChild.getLeft());
                if(leftChildRightChild.getLeft() != null){
                    leftChildRightChild.getLeft().parent = leftChild;//null pointer exeception
                }
                leftChildRightChild.setLeft(leftChild);
                leftChild.parent = leftChildRightChild;
                currentNode.setLeft(leftChildRightChild.getRight());
                if(leftChildRightChild.getRight() != null){
                    leftChildRightChild.getRight().parent = currentNode;//null pointer exception
                }
                leftChildRightChild.setRight(currentNode);
                currentNode.parent = leftChildRightChild;
                currentNode.balance = (leftChildRightChildBalance == -1) ? 1 : 0;
                leftChild.balance = (leftChildRightChildBalance == 1) ? -1 : 0;
                leftChildRightChild.balance = 0;
                if(currentNodeParent == null){
                    setRoot(leftChildRightChild);
                }else if(currentNodeParent.getLeft() == currentNode){
                    currentNodeParent.setLeft(leftChildRightChild);
                }else{
                    currentNodeParent.setRight(leftChildRightChild);
                }
                leftChildRightChild.parent = currentNodeParent;
            }
        }
        return continueBalance;
    }

    public V remove(Object keyParam){
        boolean dirLeft = true;
        if(DEBUG) avlValidateP(getRoot());
        STDAVLNodeD<K,V> currentNode = getRoot();
        if(comparator != null){
            @SuppressWarnings("unchecked")
            K key = (K)keyParam;
            Comparator<? super K> cpr = comparator;
            while(currentNode != null){
                K nodeKey = currentNode.key;
                int compareValue = cpr.compare(key, nodeKey);
                if(compareValue < 0) {
                    dirLeft = true;
                    currentNode = currentNode.getLeft();
                } else if (compareValue > 0) {
                    dirLeft = false;
                    currentNode = currentNode.getRight();
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
                    currentNode = currentNode.getLeft();
                } else if (compareValue > 0) {
                    dirLeft = false;
                    currentNode = currentNode.getRight();
                } else {
                    size = size - 1;
                    break;
                }
            }
        }
        V toReturn = null;
        if(currentNode == null){
            if(DEBUG) avlValidateP(getRoot());
            return null;
        }else{
            toReturn = currentNode.value;
        }
        //Fix balance
        STDAVLNodeD<K,V> prevNode = currentNode.parent;
        boolean continueFix = true;
        if(currentNode.getLeft() == null){
            if(prevNode == null){
                setRoot(currentNode.getRight());
            }else if(dirLeft){
                prevNode.setLeft(currentNode.getRight());
            }else{
                prevNode.setRight(currentNode.getRight());

            }
            if(currentNode.getRight() != null){
                currentNode.getRight().parent = prevNode;
            }
            currentNode = currentNode.getRight();
        }else if(currentNode.getRight() == null){
            if(prevNode == null){
                setRoot(currentNode.getLeft());
            }else if(dirLeft){
                prevNode.setLeft(currentNode.getLeft());
            }else{
                prevNode.setRight(currentNode.getLeft());
            }
            if(currentNode.getLeft() != null){
                currentNode.getLeft().parent = prevNode;
            }
            currentNode = currentNode.getLeft();
        }else{
            if(prevNode == null){
                continueFix = replaceWithRightmost(currentNode);
                STDAVLNodeD<K,V> r = getRoot();
                currentNode = r.getLeft();
                prevNode = r;
            }else if(prevNode.getLeft() == currentNode){
                continueFix = replaceWithRightmost(currentNode);
                prevNode = prevNode.getLeft();
                currentNode = prevNode.getLeft();
                dirLeft = true;
            }else{
                continueFix = replaceWithRightmost(currentNode);
                prevNode = prevNode.getRight();
                currentNode = prevNode.getLeft();
                dirLeft = true;
            }
        }
        //current node is the node we are comming from
        //prev node is the node that needs rebalancing
        while (continueFix && prevNode != null) {
            STDAVLNodeD<K,V> nextPrevNode = prevNode.parent;
            if(nextPrevNode != null){
                boolean findCurrentLeftDir = true;
                if(nextPrevNode.getLeft() == prevNode){
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
                    if (prevNode.getLeft() == currentNode) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }
                if(findCurrentLeftDir){
                    currentNode = nextPrevNode.getLeft();
                }else{
                    currentNode = nextPrevNode.getRight();
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
                    if (prevNode.getLeft() == currentNode) {
                        continueFix = deleteBalanceLeft(prevNode);
                    } else {
                        continueFix = deleteBalanceRight(prevNode);
                    }
                }
                prevNode = null;
            }
        }
        if(DEBUG) avlValidateP(getRoot());
        return toReturn;
    }

    public void clear(){
        size = 0;
        setRoot(null);
    }

    final private void addAllToList(final STDAVLNodeD<K,V> node, LinkedList<Map.Entry<K, V>> list){
        if(node!=null){
            addAllToList(node.getLeft(), list);
            AbstractMap.SimpleImmutableEntry<K,V> entry = new AbstractMap.SimpleImmutableEntry<K,V>(node.key, node.value){
                public int hashCode(){
                    return node.key.hashCode();
                }
            };
            list.add(entry);
            addAllToList(node.getRight(), list);
        }
    } 

    final protected void addAllToList(LinkedList<Map.Entry<K, V>> list){
        addAllToList(getRoot(), list);
    } 

    //Set<K> keySet();
    //Collection<V> values();
    public Set<Map.Entry<K, V>> entrySet(){
        LinkedList<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>();
        addAllToList(getRoot(), list);
        return new HashSet<Map.Entry<K, V>>(list);
    }
    //boolean equals(Object o);
    //int hashCode();
}
