package se.uu.collection;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FatSkipListMapStamped<K,V> extends AbstractMap<K, V> implements SplitableAndJoinableMap<K, V>, Invalidatable, AnyKeyProviding<K> {

	
	//STUFF NEEDED FOR INTEGRATION WITH CA TREE
	
	
    private final StampedLock lock = new StampedLock();
    private final Lock wlock = lock.asWriteLock();
    private final Lock rlock = lock.asReadLock();
    private int statLockStatistics = 0;
    private boolean valid = true;
    //Use setRoot and getRoot to access the root
    private Object parent = null;
    private static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
    private static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
    private static final int STAT_LOCK_FAILURE_CONTRIB = 250;
    private static final int STAT_LOCK_SUCCESS_CONTRIB = 1;
	
	
	
    public void setParent(Object parent){
        this.parent = parent;
    }

    public Object getParent(){
        return parent;
    }

    //=== Public functions and helper functions ===

    //=== Any key providing functions =============

    public K anyKey(){
        if(isEmpty()){
        	return null;
        }else{
        	return this.head.getNextPointer(MAX_NR_OF_LEVELS).maxKey();
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
        return wlock.tryLock();
    }
    
    public void lock(){
        if (wlock.tryLock()) {
            statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
            return;
        }
        wlock.lock();
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }
    
	public boolean lockIsContended() {
        if (wlock.tryLock()) {
            return false;
        }
        wlock.lock();
        return true;
	}
    
	public void lockNoStats() {
		wlock.lock();
	}

    public void addToContentionStatistics(){
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void subFromContentionStatistics(){
	statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
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
	
    final protected void addAllToList(LinkedList<Map.Entry<K, V>> list){
		Node currentNode = head.getNextPointer(MAX_NR_OF_LEVELS -1);
		while(currentNode != end){
			for(int i = 0; i < currentNode.size(); i++){
				@SuppressWarnings("unchecked")
				K key = (K)currentNode.getKeys()[i];
	            @SuppressWarnings({ "serial", "unchecked" })
				AbstractMap.SimpleImmutableEntry<K,V> entry = new AbstractMap.SimpleImmutableEntry<K,V>(key, (V)currentNode.getValues()[i]){
	                public int hashCode(){
	                    return key.hashCode();
	                }
	            };
	            list.add(entry);
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS -1);
		}
    } 
	
	//END =======================================
	
	
	//Number of elements per node (should be 3 or greater)
	private static final int DEGREE = 32;
	private static final int MAX_NR_OF_LEVELS = 30;
	
	private Comparator<? super K> comparator;
	private int size = 0;
    private Node head = new Node(MAX_NR_OF_LEVELS);
    private Node end = new Node(MAX_NR_OF_LEVELS);

    public FatSkipListMapStamped() {
        this(null);
    }

    public FatSkipListMapStamped(Comparator<? super K> comparator) {
        this.comparator = comparator;
        for(int i = 0; i < MAX_NR_OF_LEVELS; i++){
        	head.changeNextPointer(i, end);
        	end.changeBackPointer(i, head);
        }
    }
	
    private int compare(K key1, K key2){
    	if(comparator == null){
    		@SuppressWarnings("unchecked")
            Comparable<? super K> keyComp = (Comparable<? super K>) key1;
            return keyComp.compareTo(key2);
    	}else{
    		return comparator.compare(key1, key2);
    	}
    }

	private boolean lessThan(Node currentLeft, K key) {
		if(currentLeft == head) return true;
		else if(currentLeft == end) return false;
		else return compare(key, currentLeft.maxKey()) > 0;
	}
    int counter = 0;
    private Node findNode(K key){
    	if(head.getNextPointer(MAX_NR_OF_LEVELS -1) == end){
    		return null;
    	}
    	Node prevLeft = head;
    	Node currentNode = head;
        for(int i = 0; i < MAX_NR_OF_LEVELS; i++){
        	while(lessThan(currentNode, key)){
        		prevLeft = currentNode;
        		currentNode = currentNode.getNextPointer(i);
        	}
        	currentNode = prevLeft;
        }
        Node nextNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS -1);
        if(nextNode == end){
        	return currentNode;
        }else {
        	return nextNode;
        }
    }
    

	public String toString(){
		String b = head.toString() + "\n ============================== \n ";
		Node currentNode = head.getNextPointer(MAX_NR_OF_LEVELS -1);
		while(currentNode != end){
			b = b + currentNode.toString() + "\n ============================== \n ";
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS -1);
		}
		b = b + end.toString() + "\n ============================== \n ";
		b= b + "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n";
		return b;
	}
    
    

	private class Node{
		private int lastKeyIndex = -1;
		private K maxKey = null; //cache for performance reasons
		private Object nextPointers[];
		private Object[] keys = new Object[DEGREE];
		private Object[] values = new Object[DEGREE];
		private Object backPointers[];
		public int theId = 0;
		public Node(int levels){
			nextPointers = new Object[levels];
			backPointers = new Object[levels];
			theId = ThreadLocalRandom.current().nextInt(100);
		}
		public Node(){
			long randomNumber = ThreadLocalRandom.current().nextLong(1152921504606846976L);//2**60
			long base = 1152921504606846976L / 2;
			int levels = -1;
			for(int i = 1; i<= MAX_NR_OF_LEVELS; i++){
				if(randomNumber > base){
					levels = i;
					break;
				}
				base = base / 2;
			}
			if(levels == -1){
				levels = MAX_NR_OF_LEVELS;
			}
			nextPointers = new Object[levels];
			backPointers = new Object[levels];
			theId = ThreadLocalRandom.current().nextInt(10000);
		}
		
		@SuppressWarnings("unchecked")
		public String toString(){
			String b = "(" + theId + ") ";
			for(int i = 0; i < size(); i++){
				b = b + keys[i] + ", ";
			}
			b = b + "\n";
			for(int i = 0; i < levels(); i++){
					b = b + ((backPointers[i] == null) ? "null" : ((Node)backPointers[i]).theId) + "<- -> " + ((nextPointers[i] == null) ? "null" : ((Node)nextPointers[i]).theId) + "\n";
			}
			
			return b;
		}
		
		public boolean isFull(){
			return DEGREE == (lastKeyIndex +1) ;
		}
		
		@SuppressWarnings("unchecked")
		public K minKey(){
			return (K)keys[0];
		}
		
		@SuppressWarnings("unchecked")
		public K maxKey(){
			return maxKey;
		}
		
		public int levels(){
			return nextPointers.length;
		}
		public void changeNextPointer(int level, Object newValue){
			nextPointers[level - (MAX_NR_OF_LEVELS - nextPointers.length)] = newValue;
		}
		public void changeBackPointer(int level, Object newValue){
			backPointers[level - (MAX_NR_OF_LEVELS - backPointers.length)] = newValue;
		}
		@SuppressWarnings("unchecked")
		public Node getNextPointer(int level) {
			return (Node)nextPointers[level - (MAX_NR_OF_LEVELS - nextPointers.length)];
		}
		@SuppressWarnings("unchecked")
		public Node getBackPointer(int level) {
			return (Node)backPointers[level - (MAX_NR_OF_LEVELS - backPointers.length)];
		}
		private void splitAndInsert(K key, V value, boolean writeOver){
			Object[] predecessors = getPredecessors();
			Node newNode = new Node();
			int splitFromIndex = DEGREE / 2;
			for(int i = splitFromIndex; i < DEGREE ; i++){
				newNode.keys[i-splitFromIndex] = keys[i];
				newNode.values[i-splitFromIndex] = values[i];
			}
			lastKeyIndex = splitFromIndex - 1;
			newNode.lastKeyIndex = DEGREE - splitFromIndex -1;
			if(compare(key, newNode.minKey()) < 0 ){
				insert(key, value, writeOver);
				newNode.maxKey = (K)newNode.keys[newNode.lastKeyIndex];
			}else{
				newNode.insert(key, value, writeOver);
			}
			//Fix pointers in the new node
			for(int i = MAX_NR_OF_LEVELS - newNode.levels(); i < MAX_NR_OF_LEVELS; i++){
				@SuppressWarnings("unchecked")
				Node predecessorAtLevel = (Node)predecessors[i];
				newNode.changeBackPointer(i, predecessorAtLevel);
				Node nextNodeAtLevel = predecessorAtLevel.getNextPointer(i);
				newNode.changeNextPointer(i, nextNodeAtLevel);
				predecessorAtLevel.changeNextPointer(i, newNode);
				nextNodeAtLevel.changeBackPointer(i, newNode);
			}
		}
		
		private Object[] getPredecessors() {
			Object[] predecessors = new Object[MAX_NR_OF_LEVELS];
			Node currentNode = this;
			for(int i = MAX_NR_OF_LEVELS - 1; i >= 0 ; i--){
				while(currentNode.levels() < (MAX_NR_OF_LEVELS - i)){
					currentNode = currentNode.getBackPointer(i+1);
				}
				predecessors[i] = currentNode;
			}
			return predecessors;
		}
		//Based on open JDK 8 code Arrays.binarySearch
		@SuppressWarnings("unchecked")
		public V getInNode(K key) {
			int low = 0;
			int high = lastKeyIndex;

			while (low <= high) {
				int mid = (low + high) >>> 1;
				Object midVal = keys[mid];
				int cmp = compare(key, (K)midVal);
				if (cmp > 0)
					low = mid + 1;
				else if (cmp < 0)
					high = mid - 1;
				else
					return (V)values[mid]; // key found
			}
			return null;//-(low + 1); // key not found.
		}
		
		public V insert(K key, V value, boolean writeOver) {
			int low = 0;
			int high = lastKeyIndex;

			while (low <= high) {
				int mid = (low + high) >>> 1;
				Object midVal = keys[mid];
				@SuppressWarnings("unchecked")
				int cmp = compare(key, (K)midVal);
				if (cmp > 0)
					low = mid + 1;
				else if (cmp < 0)
					high = mid - 1;
				else{
					@SuppressWarnings("unchecked")
					V oldValue = (V)values[mid];
					if(writeOver){
						values[mid] = value; // key found
					}
					return oldValue;
				}
			}
			if (isFull()) {
				splitAndInsert(key, value, writeOver);
			} else {
				Object tmpKey = null;
				Object tmpValue = null;
				Object oldTmpKey = null;
				Object oldTmpValue = null;
				lastKeyIndex = lastKeyIndex + 1;
				for (int i = low; i <= lastKeyIndex; i++) {
					oldTmpKey = tmpKey;
					oldTmpValue = tmpValue;
					tmpKey = keys[i];
					tmpValue = values[i];
					keys[i] = oldTmpKey;
					values[i] = oldTmpValue;
				}
				keys[low] = key;
				values[low] = value;
			}
			if(maxKey != keys[lastKeyIndex]){
				maxKey = (K)keys[lastKeyIndex];
			}
			return null;// -(low + 1); // key not found.
		}
		
		public V remove(K key) {
			int low = 0;
			int high = lastKeyIndex;

			while (low <= high) {
				int mid = (low + high) >>> 1;
				Object midVal = keys[mid];
				@SuppressWarnings("unchecked")
				int cmp = compare(key, (K)midVal);
				if (cmp > 0)
					low = mid + 1;
				else if (cmp < 0)
					high = mid - 1;
				else{
					@SuppressWarnings("unchecked")
					V oldValue = (V)values[mid];
					for(int i = mid; i < lastKeyIndex; i++){
						keys[i] = keys[i+1];
						values[i] =	values[i+1];	
					}
					lastKeyIndex = lastKeyIndex - 1;
					if (lastKeyIndex == -1) {
						// Fix pointers
						for (int i = MAX_NR_OF_LEVELS - this.levels(); i < MAX_NR_OF_LEVELS; i++) {
							Node predecesor = getBackPointer(i);
							Node successor = getNextPointer(i);
							predecesor.changeNextPointer(i, successor);
							successor.changeBackPointer(i, predecesor);
						}
					}else{
						if(maxKey != keys[lastKeyIndex]){
							maxKey = (K)keys[lastKeyIndex];
						}
					}
					return oldValue;
				}
			}
			return null;//-(low + 1); // key not found.
		}
		
		public int size() {
			return lastKeyIndex + 1;
		}
		public Object[] getKeys() {
			return keys;
		}
		public Object[] getValues() {
			return values;
		}
	}
		
	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return head.getNextPointer(MAX_NR_OF_LEVELS -1) == end;
	}

	@Override
	public boolean containsKey(Object key) {
		return get(key) != null;
	}


	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		Node node = findNode((K)key);
		if(node == null){
			return null;
		}else{
			return node.getInNode((K)key);
		}
	}

	@Override
	public V put(K key, V value) {
		Node node = findNode((K) key);
		if (node == null) {
			node = new Node();
			for (int i = MAX_NR_OF_LEVELS - node.levels(); i < MAX_NR_OF_LEVELS; i++) {
				head.changeNextPointer(i, node);
				end.changeBackPointer(i, node);
				node.changeBackPointer(i, head);
				node.changeNextPointer(i, end);
			}
		}
		V returnValue = node.insert((K) key, value, true);
		if (returnValue == null) {
			size++;
			return null;
		} else {
			return returnValue;
		}
	}
	int i = 0;
	public V putIfAbsent(K key, V value) {
		Node node = findNode((K) key);
		if (node == null) {
			node = new Node();
			for (int i = MAX_NR_OF_LEVELS - node.levels(); i < MAX_NR_OF_LEVELS; i++) {
				head.changeNextPointer(i, node);
				end.changeBackPointer(i, node);
				node.changeBackPointer(i, head);
				node.changeNextPointer(i, end);
			}
		}

		V returnValue = node.insert((K) key, value, false);
		if (returnValue == null) {
			size++;
			return null;
		} else {
			return returnValue;
		}
	}

	@Override
	public V remove(Object key) {
		@SuppressWarnings("unchecked")
		Node node = findNode((K)key);
		if(node == null){
			return null;
		}else{
			@SuppressWarnings("unchecked")
			V returnValue = node.remove((K)key);
			if(returnValue == null){
				return null;
			}else{
				size--;
				return returnValue;
			}
		}
	}

	@Override
	public void clear() {
        for(int i = 0; i < MAX_NR_OF_LEVELS; i++){
        	head.changeNextPointer(i, end);
        	end.changeBackPointer(i, head);
        }
        size = 0;
	}

	@Override
	public SplitableAndJoinableMap<K, V> join(
			SplitableAndJoinableMap<K, V> right) {
		FatSkipListMapStamped<K, V> newMap;
		FatSkipListMapStamped<K, V> theRight = (FatSkipListMapStamped<K, V>)right;
		if(comparator == null){
			newMap = new FatSkipListMapStamped<K, V>();
		}else{
			newMap = new FatSkipListMapStamped<K, V>(comparator);
		}
		newMap.head = this.head;
		newMap.end = theRight.end;
		for(int i = 0; i < MAX_NR_OF_LEVELS; i++){
			@SuppressWarnings("unchecked")
			Node leftConnectNodeAtLevel = (Node)this.end.backPointers[i];
			@SuppressWarnings("unchecked")
			Node rightConnectNodeAtLevel = (Node)theRight.head.nextPointers[i];
			leftConnectNodeAtLevel.changeNextPointer(i, rightConnectNodeAtLevel);
			rightConnectNodeAtLevel.changeBackPointer(i, leftConnectNodeAtLevel);
		}
		newMap.size = this.size + theRight.size;
		return newMap;
	}

	@Override
	public SplitableAndJoinableMap<K, V> split(Object[] splitKeyWriteBack,
			SplitableAndJoinableMap<K, V>[] rightTreeWriteBack) {
		FatSkipListMapStamped<K, V> newLeftPart;
		FatSkipListMapStamped<K, V> newRightPart;
		if (comparator == null) {
			newLeftPart = new FatSkipListMapStamped<K, V>();
			newRightPart = new FatSkipListMapStamped<K, V>();
		} else {
			newLeftPart = new FatSkipListMapStamped<K, V>(comparator);
			newRightPart = new FatSkipListMapStamped<K, V>(comparator);
		}
		if (head.getNextPointer(MAX_NR_OF_LEVELS - 1).getNextPointer(
				MAX_NR_OF_LEVELS - 1) == end) {// Only one node
			Node theOnlyNode = head.getNextPointer(MAX_NR_OF_LEVELS - 1);
			Node newRightNode = new Node();
			int splitFromIndex = (theOnlyNode.lastKeyIndex + 1) / 2;
			for (int i = splitFromIndex; i < (theOnlyNode.lastKeyIndex + 1); i++) {
				newRightNode.keys[i - splitFromIndex] = theOnlyNode.keys[i];
				newRightNode.values[i - splitFromIndex] = theOnlyNode.values[i];
			}
			newRightNode.lastKeyIndex = (theOnlyNode.lastKeyIndex + 1) - splitFromIndex - 1;
			theOnlyNode.lastKeyIndex = splitFromIndex - 1;
			rightTreeWriteBack[0] = newRightPart;
			// Link in left node
			for (int i = MAX_NR_OF_LEVELS - theOnlyNode.levels(); i < MAX_NR_OF_LEVELS; i++) {
				newLeftPart.head.changeNextPointer(i, theOnlyNode);
				newLeftPart.end.changeBackPointer(i, theOnlyNode);
				theOnlyNode.changeBackPointer(i, newLeftPart.head);
				theOnlyNode.changeNextPointer(i, newLeftPart.end);
			}
			theOnlyNode.maxKey = (K)theOnlyNode.keys[theOnlyNode.lastKeyIndex];
			// Link in right node
			for (int i = MAX_NR_OF_LEVELS - newRightNode.levels(); i < MAX_NR_OF_LEVELS; i++) {
				newRightPart.head.changeNextPointer(i, newRightNode);
				newRightPart.end.changeBackPointer(i, newRightNode);
				newRightNode.changeBackPointer(i, newRightPart.head);
				newRightNode.changeNextPointer(i, newRightPart.end);
			}
			newRightNode.maxKey = (K)newRightNode.keys[newRightNode.lastKeyIndex];
			rightTreeWriteBack[0] = newRightPart;
			splitKeyWriteBack[0] = newRightNode.minKey();
			newLeftPart.size = this.size/2;
			newRightPart.size = this.size - newLeftPart.size;
			return newLeftPart;
		} else {
			int level = 0;
			while (this.head.getNextPointer(level) == this.end) {
				level++;
			}
			Node splitNode = this.head.getNextPointer(level);
			// Create leftPart
			for (int i = level; i < MAX_NR_OF_LEVELS; i++) {
					Node endNodeAtLevel = splitNode.getBackPointer(i);
					if(endNodeAtLevel != head){
						endNodeAtLevel.changeNextPointer(i, newLeftPart.end);
						newLeftPart.end.changeBackPointer(i, endNodeAtLevel);
						newLeftPart.head.changeNextPointer(i,this.head.getNextPointer(i));
						this.head.getNextPointer(i).changeBackPointer(i, newLeftPart.head);
					}
			}
			// Create right part
			for (int i = level; i < MAX_NR_OF_LEVELS; i++) {
				Node startNodeAtLevel = splitNode.getNextPointer(i);
				if (startNodeAtLevel != this.end) {
					newRightPart.head.changeNextPointer(i,startNodeAtLevel);
					startNodeAtLevel.changeBackPointer(i, newRightPart.head);
					newRightPart.end.changeBackPointer(i, this.end.getBackPointer(i));
					this.end.getBackPointer(i).changeNextPointer(i, newRightPart.end);
				}
			}
			//Approximate sizes
			newLeftPart.size = this.size/2;
			newRightPart.size = this.size - newLeftPart.size;
			
			// Link in the splitNode with a newly generated number of levels
			Node newSplitNode = new Node();
			newSplitNode.keys = splitNode.keys;
			newSplitNode.values = splitNode.values;
			newSplitNode.lastKeyIndex = splitNode.lastKeyIndex;
			newSplitNode.maxKey = (K)newSplitNode.keys[newSplitNode.lastKeyIndex];
			if(this.head.getNextPointer(MAX_NR_OF_LEVELS -1) == splitNode){
				//Unlucky case want elements in both resulting nodes
				splitKeyWriteBack[0] = splitNode.getNextPointer(MAX_NR_OF_LEVELS -1).minKey();
				for (int i = MAX_NR_OF_LEVELS - newSplitNode.levels(); i < MAX_NR_OF_LEVELS; i++) {
					Node backNodeAtLevel = newLeftPart.end.getBackPointer(i);
					newLeftPart.end.changeBackPointer(i, newSplitNode);
					newSplitNode.changeNextPointer(i, newLeftPart.end);
					newSplitNode.changeBackPointer(i, backNodeAtLevel);
					backNodeAtLevel.changeNextPointer(i, newSplitNode);
				}
			}else{
				splitKeyWriteBack[0] = splitNode.minKey();
				for (int i = MAX_NR_OF_LEVELS - newSplitNode.levels(); i < MAX_NR_OF_LEVELS; i++) {
					Node nextNodeAtLevel = newRightPart.head.getNextPointer(i);
					newRightPart.head.changeNextPointer(i, newSplitNode);
					newSplitNode.changeBackPointer(i, newRightPart.head);
					newSplitNode.changeNextPointer(i, nextNodeAtLevel);
					nextNodeAtLevel.changeBackPointer(i, newSplitNode);
				}
			}
			rightTreeWriteBack[0] = newRightPart;
			return newLeftPart;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		TreeMap<K, V> entrySet;
		if(comparator == null){
			entrySet = new TreeMap<K, V>();
		}else{
			entrySet = new TreeMap<K, V>(comparator);
		}
		Node currentNode = head.getNextPointer(MAX_NR_OF_LEVELS -1);
		while(currentNode != end){
			for(int i = 0; i < currentNode.size(); i++){
				entrySet.put((K)currentNode.getKeys()[i], (V)currentNode.getValues()[i]);
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS -1);
		}
		return entrySet.entrySet();
	}
	
	public static void main(String[] args){
		FatSkipListMapStamped<Integer, Integer> map= new FatSkipListMapStamped<Integer, Integer>();
		for(int i = 10; i >= 0; i--){
			System.out.println("INSERT: " + i);
			map.put(i, i);
		}
		for(Integer e: map.keySet()){
			System.out.println(e);
		}
		for(int i = 10; i >= 0; i--){
			System.out.println("GET: " + i);
			System.out.println(map.get(i));
		}
		for(int i = 10; i >= 0; i--){
			System.out.println("Remove: " + i);
			map.remove(i, i);
		}
//		for(Integer e: map.keySet()){
//			System.out.println(e);
//		}
		for(int i = 0; i < 100000; i++){
			//System.out.println("INSERT: " + i);
			map.put(i, i);
		}
		System.out.println(map.get(50000));
	}

	public boolean hasLessThanTwoElements() {
		if(isEmpty()){
			return true;
		}else if(head.getNextPointer(MAX_NR_OF_LEVELS -1).getNextPointer(MAX_NR_OF_LEVELS -1) != end){
			return false; //More than two nodes
		}else{
			Node onlyNode = head.getNextPointer(MAX_NR_OF_LEVELS -1);
			return onlyNode.size()< 2;
		}
	}

	public K maxKey() {
		if(isEmpty()){
			return null;
		}else{
			return end.getBackPointer(MAX_NR_OF_LEVELS -1).maxKey();
		}
	}
	
	@SuppressWarnings("unchecked")
	public void addKeysInRangeToStack(K lo, K hi, Consumer<K> consumer) {
		Node currentNode = findNode(lo);
		if(currentNode == null){
			return;
		}
		while (currentNode != end) {
			if (compare(hi, currentNode.maxKey()) <= 0) {
				// We only need to look at this node
				int i = 0;
				K currentKey = (K) currentNode.getKeys()[i];
				while (compare(hi, currentKey) >= 0) {
					consumer.accept(currentKey);
					//returnStack.push(currentKey);
					i = i + 1;
					
					if(i >= currentNode.size()){
						break;
					}
					
					currentKey = (K) currentNode.getKeys()[i];
				}
				return;
			} else {
				// All keys in this node need to be included
				for (int i = 0; i < currentNode.size(); i++) {
					//returnStack.push((K) currentNode.getKeys()[i]);
					consumer.accept((K)currentNode.getKeys()[i]);
				}
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS - 1);
		}
	}


	@SuppressWarnings("unchecked")
	public void optimisticAddKeysInRangeToStack(K lo, K hi, Consumer<K> consumer) {
	    long readToken = getOptimisticReadToken();
		Node currentNode = findNode(lo);
		if(currentNode == null){
			return;
		}
		while (currentNode != end) {
		    if(readToken !=  getOptimisticReadToken() || currentNode.maxKey() == null){
			return;
		    }
			if (compare(hi, currentNode.maxKey()) <= 0) {
				// We only need to look at this node
				int i = 0;
				K currentKey = (K) currentNode.getKeys()[i];
				while (compare(hi, currentKey) >= 0) {
					consumer.accept(currentKey);
					//returnStack.push(currentKey);
					i = i + 1;
					
					if(i >= currentNode.size()){
						break;
					}
					
					currentKey = (K) currentNode.getKeys()[i];
				}
				return;
			} else {
				// All keys in this node need to be included
				for (int i = 0; i < currentNode.size(); i++) {
					//returnStack.push((K) currentNode.getKeys()[i]);
					consumer.accept((K)currentNode.getKeys()[i]);
				}
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS - 1);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void performOperationToValuesInRange(K lo, K hi, BiFunction<K,V,V> operation) {
		Node currentNode = findNode(lo);
		if(currentNode == null){
			return;
		}
		while (currentNode != end) {
			if (compare(hi, currentNode.maxKey()) <= 0) {
				// We only need to look at this node
				int i = 0;
				K currentKey = (K) currentNode.getKeys()[i];
				while (compare(hi, currentKey) >= 0) {
					currentNode.getValues()[i] = operation.apply(currentKey, (V)currentNode.getValues()[i]);
					i = i + 1;
					
					if(i >= currentNode.size()){
						break;
					}
					
					currentKey = (K) currentNode.getKeys()[i];
				}
				return;
			} else {
				// All keys in this node need to be included
				for (int i = 0; i < currentNode.size(); i++) {
					currentNode.getValues()[i] = operation.apply((K)currentNode.getKeys()[i], (V)currentNode.getValues()[i]);
				}
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS - 1);
		}
	}

	@SuppressWarnings("unused")
	private int computeActualSize() {
		Node currentNode = head.getNextPointer(MAX_NR_OF_LEVELS -1);
		int counter = 0;
		while(currentNode != end){
			for(int i = 0; i < currentNode.size(); i++){
				counter++;
			}
			currentNode = currentNode.getNextPointer(MAX_NR_OF_LEVELS -1);
		}
		return counter;
	}




}
