/*
 *  Copyright 2018 Kjell Winblad (kjellwinblad@gmail.com, http://winsh.me)
 *
 *  This file is part of JavaRQBench
 *
 *  catrees is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  catrees is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with catrees.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.uu.collection;

import java.util.AbstractMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import algorithms.published.LockFreeKSTRQ;

public class NonAtomicRangeUpdateConcurrentSkipListMap<K extends Comparable<? super K>,V>  extends AbstractMap<K,V> implements RangeQueryMap<K, V>, RangeUpdateMap<K, V>{

	private ConcurrentSkipListMap<K, V> map = new ConcurrentSkipListMap<K, V>();
	
	
	@Override
	public V get(Object key) {
		V returnValue = map.get(key);
		return returnValue;
	}

	@Override
	public V put(K key, V value) {
		V returnValue = map.put(key, value);
		return returnValue;
	}

    public V putIfAbsent(K key, V value){
		V returnValue = map.putIfAbsent(key, value);
		return returnValue;
    }
	
	@Override
	public V remove(Object key) {
		V returnValue = map.remove(key);
		return returnValue;
	}

	@Override
	public void rangeUpdate(K lo, K hi, BiFunction<K, V, V> operation) {
		ConcurrentNavigableMap<K,V> navMap = map.subMap(lo, true, hi, true);
		Set<java.util.Map.Entry<K, V>> entrySet = navMap.entrySet();
		for(java.util.Map.Entry<K, V> entry: entrySet){
			map.put(entry.getKey(),operation.apply(entry.getKey(), entry.getValue()));
		}
	}

	private ThreadLocal<Stack<Object>> localReturnStack = new ThreadLocal<Stack<Object>>(){

		@Override
		protected Stack<Object> initialValue() {
			return new Stack<Object>();
		}
		
	};

	@Override
	public void subSet(K lo, K hi, Consumer<K> consumer) {
		ConcurrentNavigableMap<K,V> navMap = map.subMap(lo, true, hi, true);
		NavigableSet<K> entrySet = navMap.keySet();
		for(K entry: entrySet){
			consumer.accept(entry);
		}
	}
	
	@Override
	public Object[] subSet(K lo, K hi) {
		ConcurrentNavigableMap<K,V> navMap = map.subMap(lo, true, hi, true);
		Stack<Object> returnStack = localReturnStack.get(); 
		NavigableSet<K> entrySet = navMap.keySet();
		for(K entry: entrySet){
			returnStack.push(entry);
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



}
