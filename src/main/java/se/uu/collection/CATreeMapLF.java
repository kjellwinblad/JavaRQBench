package se.uu.collection;
import java.util.Random;

import java.util.concurrent.locks.*;
import java.util.*;
import java.io.*;
import algorithms.published.LockFreeKSTRQOld;
 
public class CATreeMapLF<K extends Comparable<? super K>, V> extends AbstractMap<K,V> {

    private volatile Object root = new DualLFAVLTreeMapSTD<K, V>();
    private final Comparator<? super K> comparator;
    // ====== FOR DEBUGING ======
    private final static boolean DEBUG = false;
    // ==========================

    static private final class RouteNode{
        volatile Object left;
        volatile Object right;
        final Object key;
        final ReentrantLock lock = new ReentrantLock();
        boolean valid = true;
        public RouteNode(Object key, Object left, Object right){
            this.key = key;
            this.left = left;
            this.right = right;
        }
        public String toString(){
            return "R(" + key + ")";
        }
    }

    //==== Functions for debuging and testing

    void printDotHelper(Object n, PrintStream writeTo, int level){
        try{
            if(n instanceof RouteNode){
                RouteNode node = (RouteNode)n;
                //LEFT
                writeTo.print("\"" + node + level+" \"");
                writeTo.print(" -> ");
                writeTo.print("\"" + node.left + (level +1)+" \"");
                writeTo.println(";");
                //RIGHT
                writeTo.print("\"" + node + level+" \"");
                writeTo.print(" -> ");
                writeTo.print("\"" + node.right + (level +1)+" \"");
                writeTo.println(";");

                printDotHelper(node.left, writeTo, level +1);
                printDotHelper(node.right, writeTo, level +1);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }


    void printDot(Object node, String fileName){
        try{
            lockAll();
            Process p = new ProcessBuilder("dot", "-Tsvg")
                .redirectOutput(ProcessBuilder.Redirect.to(new File(fileName + ".svg")))
                .start();
            PrintStream writeTo = new PrintStream(p.getOutputStream());
            writeTo.print("digraph G{\n");
            writeTo.print("  graph [ordering=\"out\"];\n");
            printDotHelper(node, writeTo, 0);
            writeTo.print("}\n");
            writeTo.close();
            p.waitFor();
            unlockAll();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //=== End of debug functions ==================


    //=== Constructors ============================

    public CATreeMapLF() {
        comparator = null;
    }

    public CATreeMapLF(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    //=== Public functions and helper functions ===


    //=== Sorted Set Functions ====================


    private int sizeHelper(Object currentNode){
        if(currentNode == null){
            return 0;
        }else{
            if(currentNode instanceof RouteNode){
                RouteNode r = (RouteNode)currentNode;
                int sizeSoFar = sizeHelper(r.left);
                return sizeSoFar + sizeHelper(r.right);
            }else {
                @SuppressWarnings("unchecked")
                DualLFAVLTreeMapSTD<K,V> b = (DualLFAVLTreeMapSTD<K,V>)currentNode;
                return b.size();
            }
        }
    }

    public int size(){
        lockAll();
        int size = sizeHelper(root);
        unlockAll();
        return size;
    }

    public boolean isEmpty(){
        return size() == 0;
    }

    public boolean containsKey(Object key){
        return get(key) != null;
    }

    final private Object getBaseNodeUsingComparator(Object keyParam){
        Object currNode = root;
        @SuppressWarnings("unchecked")
        K key = (K)keyParam;
        while (currNode instanceof RouteNode) {
            RouteNode currNodeR = (RouteNode)currNode;
            @SuppressWarnings("unchecked")
            K routeKey = (K)(currNodeR.key);
            if (comparator.compare(key, routeKey) < 0) {
                currNode = currNodeR.left;
            }else {
                currNode = currNodeR.right;
            }
        }
        return currNode;
    }

    final private Object getBaseNode(Object keyParam){
        Object currNode = root;
        if(comparator != null){
            return getBaseNodeUsingComparator(keyParam);
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) keyParam;
            while (currNode instanceof RouteNode) {
                RouteNode currNodeR = (RouteNode)currNode;
                @SuppressWarnings("unchecked")
                K routeKey = (K)(currNodeR.key);
                if (key.compareTo(routeKey) < 0) {
                    currNode = currNodeR.left;
                } else {
                    currNode = currNodeR.right;
                }
            }
            return currNode;
        }
    }

    final private void highContentionSplit(DualLFAVLTreeMapSTD<K, V> baseNode){
        if(baseNode.getRoot() == null || 
           (baseNode.getRoot().getLeft() == null &&
            baseNode.getRoot().getRight() == null)){
            baseNode.resetStatistics();//Fast path out if nrOfElem <= 1
            return;
        }

        RouteNode parent = (RouteNode)baseNode.getParent();
        Object[] writeBackSplitKey = new Object[1];
        @SuppressWarnings("unchecked")
        SplitableAndJoinableMap<K,V>[] writeBackRightTree = new SplitableAndJoinableMap[1];
        @SuppressWarnings("unchecked")
        DualLFAVLTreeMapSTD<K,V> leftTree = (DualLFAVLTreeMapSTD<K,V>)baseNode.split(writeBackSplitKey, writeBackRightTree);
        if(leftTree == null){
            baseNode.resetStatistics();
            return;
        }
        @SuppressWarnings("unchecked")
        K splitKey = (K)writeBackSplitKey[0];
        DualLFAVLTreeMapSTD<K,V> rightTree = (DualLFAVLTreeMapSTD<K,V>)writeBackRightTree[0];
        RouteNode newRoute = new RouteNode(splitKey, leftTree, rightTree);
        leftTree.setParent(newRoute);
        rightTree.setParent(newRoute);
        if (parent == null) {
            root = newRoute;
        }else {
            if (parent.left == baseNode){
                parent.left = newRoute;
            }else{
                parent.right = newRoute;
            }
        }
        baseNode.invalidate();
    }

    final private DualLFAVLTreeMapSTD<K,V> leftmostBaseNode(Object node){
        Object currentNode = node;
        while(currentNode instanceof RouteNode){
            RouteNode r = (RouteNode)currentNode;
            currentNode = r.left;
        }
        @SuppressWarnings("unchecked")
        DualLFAVLTreeMapSTD<K,V> toReturn = (DualLFAVLTreeMapSTD<K,V>)currentNode;
        return toReturn;
    }

    final private DualLFAVLTreeMapSTD<K,V> rightmostBaseNode(Object node){
        Object currentNode = node;
        while(currentNode instanceof RouteNode){
            RouteNode r = (RouteNode)currentNode;
            currentNode = r.right;
        }
        @SuppressWarnings("unchecked")
        DualLFAVLTreeMapSTD<K,V> toReturn = (DualLFAVLTreeMapSTD<K,V>)currentNode;
        return toReturn;
    }

    final private RouteNode parentOfUsingComparator(RouteNode node){
        @SuppressWarnings("unchecked")
        K key = (K)node.key;
        Object prevNode = null;
        Object currNode = root;

        while (currNode != node) {
            @SuppressWarnings("unchecked")
            RouteNode currNodeR = (RouteNode)currNode;
            @SuppressWarnings("unchecked")
            K routeKey = (K)(currNodeR.key);
            prevNode = currNode;
            if (comparator.compare(key, routeKey) < 0) {
                currNode = currNodeR.left;
            } else {
                currNode = currNodeR.right;
            }
        }
        return (RouteNode)prevNode;

    }

    final private RouteNode parentOf(RouteNode node){
        if(comparator != null){
            return parentOfUsingComparator(node);
        }else{
            @SuppressWarnings("unchecked")
            Comparable<? super K> key = (Comparable<? super K>) node.key;
            Object prevNode = null;
            Object currNode = root;
            while (currNode != node) {
                RouteNode currNodeR = (RouteNode)currNode;
                @SuppressWarnings("unchecked")
                K routeKey = (K)(currNodeR.key);
                prevNode = currNode;
                if (key.compareTo(routeKey) < 0) {
                    currNode = currNodeR.left;
                } else {
                    currNode = currNodeR.right;
                }
            }
            return (RouteNode)prevNode;
        }
    }

    final private void lowContentionJoin(DualLFAVLTreeMapSTD<K, V> baseNode){
        RouteNode parent = (RouteNode)baseNode.getParent();
        if(parent == null){
            baseNode.resetStatistics();
        }else if (parent.left == baseNode) {
            DualLFAVLTreeMapSTD<K,V> neighborBase = leftmostBaseNode(parent.right);
            if (!neighborBase.tryLock()) {
                baseNode.resetStatistics();
                return;
            } else if (!neighborBase.isValid()) {
                neighborBase.unlock();
                baseNode.resetStatistics();
                return;
            } else {
                @SuppressWarnings("unchecked")
                DualLFAVLTreeMapSTD<K,V> newNeighborBase = (DualLFAVLTreeMapSTD<K,V>)baseNode.join(neighborBase);
                parent.lock.lock();
                RouteNode gparent = null; // gparent = grandparent
                do {
                    if (gparent != null){
                        gparent.lock.unlock();
                    }
                    gparent = parentOf(parent);
                    if (gparent != null){
                        gparent.lock.lock();
                    }
                } while (gparent != null && !gparent.valid);
                if (gparent == null) {
                    root = parent.right;
                } else if(gparent.left == parent){
                    gparent.left = parent.right;
                } else {
                    gparent.right = parent.right;
                }
                parent.valid = false;
                parent.lock.unlock();
                if (gparent != null){
                    gparent.lock.unlock();
                }
                //Unlink is done!
                //Put in joined base node
                RouteNode neighborBaseParent = null;
                if(parent.right == neighborBase){
                    neighborBaseParent = gparent;
                }else{
                    neighborBaseParent = (RouteNode)neighborBase.getParent();
                }
                newNeighborBase.setParent(neighborBaseParent);
                if(neighborBaseParent == null){
                    root = newNeighborBase;
                } else if (neighborBaseParent.left == neighborBase) {
                    neighborBaseParent.left = newNeighborBase;
                } else {
                    neighborBaseParent.right = newNeighborBase;
                }
                neighborBase.invalidate();
                neighborBase.unlock();
                baseNode.invalidate();
            }
        } else { /* This case is symmetric to the previous one */
            DualLFAVLTreeMapSTD<K,V> neighborBase = rightmostBaseNode(parent.left);//ff
            if (!neighborBase.tryLock()) {//ff
                baseNode.resetStatistics();//ff
            } else if (!neighborBase.isValid()) {//ff
                neighborBase.unlock();//ff
                baseNode.resetStatistics();//ff
            } else {
                //                System.out.println("R" + baseNode + " " + neighborBase);
                @SuppressWarnings("unchecked")
                DualLFAVLTreeMapSTD<K,V> newNeighborBase = (DualLFAVLTreeMapSTD<K,V>)neighborBase.join(baseNode);//ff
                parent.lock.lock();//ff
                RouteNode gparent = null; // gparent = grandparent //ff
                do {//ff
                    if (gparent != null){//ff
                        gparent.lock.unlock();//ff
                    }//ff
                    gparent = parentOf(parent);//ff
                    if (gparent != null){//ff
                        gparent.lock.lock();//ff
                    }//ff
                } while (gparent != null && !gparent.valid);//ff
                if (gparent == null) {//ff
                    root = parent.left;//ff
                } else if(gparent.left == parent){//ff
                    gparent.left = parent.left;//ff
                } else {//ff
                    gparent.right = parent.left;//ff
                }//ff
                parent.valid = false;
                parent.lock.unlock();//ff
                if (gparent != null){//ff
                    gparent.lock.unlock();//ff
                }//ff
                RouteNode neighborBaseParent = null;
                if(parent.left == neighborBase){
                    neighborBaseParent = gparent;
                }else{
                    neighborBaseParent = (RouteNode)neighborBase.getParent();
                }
                newNeighborBase.setParent(neighborBaseParent);//ff
                if(neighborBaseParent == null){//ff
                    root = newNeighborBase;//ff
                } else if (neighborBaseParent.left == neighborBase) {//ff
                    neighborBaseParent.left = newNeighborBase;//ff
                } else {//ff
                    neighborBaseParent.right = newNeighborBase;//ff
                }//ff
                neighborBase.invalidate();//ff
                neighborBase.unlock();//ff
                baseNode.invalidate();//ff
            }
        }
    }

    private final void adaptIfNeeded(DualLFAVLTreeMapSTD<K,V> baseNode){
        if (baseNode.isHighContentionLimitReached()){
            if(baseNode.size() <= 1){
                baseNode.transformToLockFree();
            }else{
                highContentionSplit(baseNode);
            }
        } else if (baseNode.isLowContentionLimitReached()) {
            lowContentionJoin(baseNode);
        }
    }

    public V get(Object key){
        while(true){
            @SuppressWarnings("unchecked")
            DualLFAVLTreeMapSTD<K,V> baseNode = (DualLFAVLTreeMapSTD<K,V>)getBaseNode(key);
            //First do an optemistic attempt
            long optimisticReadToken = baseNode.getOptimisticReadToken();
            if(0L != optimisticReadToken
               && baseNode.isValid()){
                V result = baseNode.get(key);
                if(baseNode.validateOptimisticReadToken(optimisticReadToken)){
                    return result;
                }
            }
            //System.err.println("FAILED GET");
            //Optemistic attempt failed, do the normal approach
            baseNode.readLock();
            baseNode.addToContentionStatistics();//Because the optemistic attempt failed
            //Check if valid
            if (baseNode.isValid() == false) {
                baseNode.readUnlock();
                continue; // retry
            }          
            //Do the operation
            V result = baseNode.get(key);
            baseNode.readUnlock();
            return result;
        }
    }

    public V put(K key, V value){
        while(true){
            @SuppressWarnings("unchecked")
            DualLFAVLTreeMapSTD<K, V> baseNode = (DualLFAVLTreeMapSTD<K, V>)getBaseNode(key);
            //First check if we can do it in a lock free way
            long optimisticReadToken = 0;
            LockFreeKSTRQOld<K, V> lfMap = null;
            if((lfMap = baseNode.getLockFreeMap()) != null &&
               0L != (optimisticReadToken = baseNode.getOptimisticReadToken())
               && baseNode.isValid()){
                baseNode.indicateWriteStart();
                if(baseNode.validateOptimisticReadToken(optimisticReadToken)){
                    V result = lfMap.put(key, value);
                    long localSize = 0;
                    if(result == null){
                        localSize = baseNode.increaseLocalSize();
                    }
                    baseNode.indicateWriteEnd();
                    if(localSize >= 5 && baseNode.readLocalSizeSum() > 20){
                        baseNode.lock(); //Will convert to locked version
                        baseNode.unlock();
                    }
                    return result;
                }else{
                    baseNode.indicateWriteEnd();
                }
            }
            //We could not accquire lock
            baseNode.lock();
            //Check if valid
            if (!baseNode.isValid()) {
                baseNode.unlock();
                continue; // retry
            }
            //Do the operation
            V result = baseNode.put(key, value);
            adaptIfNeeded(baseNode);
            baseNode.unlock();
            return result;
        }
    }


    public V putIfAbsent(K key, V value){
        while(true){
            @SuppressWarnings("unchecked")
            DualLFAVLTreeMapSTD<K, V> baseNode = (DualLFAVLTreeMapSTD<K, V>)getBaseNode(key);
            //First check if we can do it in a lock free way
            long optimisticReadToken = baseNode.getOptimisticReadToken();
            LockFreeKSTRQOld<K, V> lfMap = baseNode.getLockFreeMap();
            if(lfMap != null &&
               0L != optimisticReadToken
               && baseNode.isValid()){
                baseNode.indicateWriteStart();
                if(baseNode.validateOptimisticReadToken(optimisticReadToken)){
                    V result = lfMap.putIfAbsent(key, value);
                    long localSize = 0;
                    if(result == null){
                        localSize = baseNode.increaseLocalSize();
                    }
                    baseNode.indicateWriteEnd();
                    if(localSize >= 5 && baseNode.readLocalSizeSum() > 20){
                        baseNode.lock();
                        if (baseNode.isValid() && baseNode.isLockFreeMode()) {
                            baseNode.transformToLocked();
                        }
                        baseNode.unlock();
                    }
                    return result;
                }else{
                    baseNode.indicateWriteEnd();
                }
            }
            baseNode.lock();
            //Check if valid
            if (!baseNode.isValid()) {
                baseNode.unlock();
                continue; // retry
            }
            //Do the operation
            V result = baseNode.putIfAbsent(key, value);
            adaptIfNeeded(baseNode);
            baseNode.unlock();
            return result;
        }
    }


    public V remove(Object key){
        while(true){
            @SuppressWarnings("unchecked")
            DualLFAVLTreeMapSTD<K,V> baseNode = (DualLFAVLTreeMapSTD<K,V>)getBaseNode(key);
            //First check if we can do it in a lock free way
            long optimisticReadToken = baseNode.getOptimisticReadToken();
            LockFreeKSTRQOld<K, V> lfMap = baseNode.getLockFreeMap();
            if(lfMap != null &&
               0L != optimisticReadToken
               && baseNode.isValid()){
                baseNode.indicateWriteStart();
                if(baseNode.validateOptimisticReadToken(optimisticReadToken)){
                    V result = lfMap.remove((K)key);
                    if(result != null){
                        baseNode.decreaseLocalSize();
                    }
                    baseNode.indicateWriteEnd();
                    return result;
                }else{
                    baseNode.indicateWriteEnd();
                }
            }
            baseNode.lock();
            //Check if valid
            if (baseNode.isValid() == false) {
                baseNode.unlock();
                continue; // retry
            }
            //Do the operation
            V result = baseNode.remove(key);
            adaptIfNeeded(baseNode);
            baseNode.unlock();
            return result;
        }
    }

    public void clear(){
        lockAll();
        root = new DualLFAVLTreeMapSTD<K, V>();
        unlockAll();
    }


    private void lockAllHelper(Object currentNode, LinkedList<DualLFAVLTreeMapSTD> lockedSoFar){
        try {
            if(currentNode != null){
                if(currentNode instanceof RouteNode){
                    RouteNode r = (RouteNode)currentNode;
                    lockAllHelper(r.left, lockedSoFar);
                    lockAllHelper(r.right, lockedSoFar);
                }else {
                    DualLFAVLTreeMapSTD b = (DualLFAVLTreeMapSTD)currentNode;
                    b.lock();
                    if(b.isValid()){
                        lockedSoFar.addLast(b);
                    }else{
                        //Retry
                        b.unlock();
                        for(DualLFAVLTreeMapSTD m : lockedSoFar){
                            m.unlock();
                        }
                        throw new RuntimeException();
                    }
                }
            }
        } catch (RuntimeException e){
            //Retry
            lockAllHelper(root, new LinkedList<DualLFAVLTreeMapSTD>());
        }
    }


    private void unlockAllHelper(Object currentNode) {
        if(currentNode != null){
            if(currentNode instanceof RouteNode) {
                RouteNode b = (RouteNode)currentNode;
                unlockAllHelper(b.left);
                unlockAllHelper(b.right);
            } else {
                DualLFAVLTreeMapSTD b = (DualLFAVLTreeMapSTD)currentNode;
                b.unlock();
            }
        }
    }

    private void lockAll(){
        lockAllHelper(root, new LinkedList<DualLFAVLTreeMapSTD>());
    }

    private void unlockAll(){
        unlockAllHelper(root);
    }

    final private void addAllToList(Object currentNode, LinkedList<Map.Entry<K, V>> list){
        if(currentNode == null){
            return;
        }else{
            if(currentNode instanceof RouteNode){
                @SuppressWarnings("unchecked")
                RouteNode r = (RouteNode)currentNode;
                addAllToList(r.left, list);
                addAllToList(r.right, list);
            }else {
                @SuppressWarnings("unchecked")
                DualLFAVLTreeMapSTD<K, V> b = (DualLFAVLTreeMapSTD<K, V>)currentNode;
                b.addAllToList(list);
                return;
            }
        }
    } 

    //Set<K> keySet();
    //Collection<V> values();
    public Set<Map.Entry<K, V>> entrySet(){
        LinkedList<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>();
        lockAll();
        addAllToList(root, list);
        unlockAll();
        return new HashSet<Map.Entry<K, V>>(list);
    }
    //boolean equals(Object o);
    //int hashCode();
}
