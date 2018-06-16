package se.uu.collection;

import java.util.AbstractMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import edu.stanford.ppl.concurrent.SnapTreeMap;
import algorithms.published.LockFreeKSTRQ;

public class RangeUpdateSnapTree<K extends Comparable<? super K>,V>  extends AbstractMap<K,V> implements RangeQueryMap<K, V>, RangeUpdateMap<K, V>{

    //	private DistributedRWLock lock = new DistributedRWLock();
	private SnapTreeMap<K, V> map = new SnapTreeMap<K, V>();
	
	
	@Override
	public V get(Object key) {
	    //lock.readLock();
		V returnValue = map.get(key);
		//	lock.readUnlock();
		return returnValue;
	}

	@Override
	public V put(K key, V value) {
	    //	lock.readLock();
		V returnValue = map.put(key, value);
		//	lock.readUnlock();
		return returnValue;
	}

    public V putIfAbsent(K key, V value){
	//	lock.readLock();
		V returnValue = map.putIfAbsent(key, value);
		//	lock.readUnlock();
		return returnValue;
    }
	
	@Override
	public V remove(Object key) {
	    //	lock.readLock();
		V returnValue = map.remove(key);
		//	lock.readUnlock();
		return returnValue;
	}

	@Override
	public void rangeUpdate(K lo, K hi, BiFunction<K, V, V> operation) {
	    //lock.lock();
		ConcurrentNavigableMap<K,V> navMap = map.subMap(lo, true, hi, true);
		Set<java.util.Map.Entry<K, V>> entrySet = navMap.entrySet();
		for(java.util.Map.Entry<K, V> entry: entrySet){
			map.put(entry.getKey(),operation.apply(entry.getKey(), entry.getValue()));
		}
		//	lock.unlock();
	}

	private ThreadLocal<Stack<Object>> localReturnStack = new ThreadLocal<Stack<Object>>(){

		@Override
		protected Stack<Object> initialValue() {
			return new Stack<Object>();
		}
		
	};
	
	@Override
	public Object[] subSet(K lo, K hi) {
	    //lock.readLock();
		SnapTreeMap<K,V> clone = map.clone();
		//	lock.readUnlock();
		ConcurrentNavigableMap<K, V> rangeMap = clone.subMap(lo, true, hi, true);
		NavigableSet<K> keySet = rangeMap.keySet();
		Stack<Object> returnStack = localReturnStack.get();	
		for(K key : keySet){
			returnStack.push(key);
		}
		Object[] returnArray = new Object[returnStack.size()];
		Object[] stackArray = returnStack.getStackArray();
		for(int i = 0; i < returnStack.size(); i++){
			returnArray[i] = stackArray[i];
		}
		return returnArray;
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return map.entrySet();
	}

	@Override
	public void subSet(K lo, K hi, Consumer<K> consumer) {
	    //lock.readLock();
		SnapTreeMap<K,V> clone = map.clone();
		//	lock.readUnlock();
		ConcurrentNavigableMap<K, V> rangeMap = clone.subMap(lo, true, hi, true);
		NavigableSet<K> keySet = rangeMap.keySet();
		for(K key : keySet){
			consumer.accept(key);
		}		
	}

}
