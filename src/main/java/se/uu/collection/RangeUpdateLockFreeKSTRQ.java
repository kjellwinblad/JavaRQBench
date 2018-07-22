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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import algorithms.published.LockFreeKSTRQ;

public class RangeUpdateLockFreeKSTRQ<K extends Comparable<? super K>,V>  extends AbstractMap<K,V> implements RangeQueryMap<K, V>, RangeUpdateMap<K, V>{

	private DistributedRWLock lock = new DistributedRWLock();
	private LockFreeKSTRQ<K, V> map = new LockFreeKSTRQ<K, V>(32);
	
	
	@Override
	public V get(Object key) {
		lock.readLock();
		V returnValue = map.get(key);
		lock.readUnlock();
		return returnValue;
	}

	@Override
	public V put(K key, V value) {
		lock.readLock();
		V returnValue = map.put(key, value);
		lock.readUnlock();
		return returnValue;
	}

    public V putIfAbsent(K key, V value){
		lock.readLock();
		V returnValue = map.putIfAbsent(key, value);
		lock.readUnlock();
		return returnValue;
    }
	
	@Override
	public V remove(Object key) {
		lock.readLock();
		V returnValue = map.remove(key);
		lock.readUnlock();
		return returnValue;
	}

	@Override
	public void rangeUpdate(K lo, K hi, BiFunction<K, V, V> operation) {
		lock.lock();
		map.rangeUpdateNonAtomic(lo, hi, operation);
		lock.unlock();
	}

	@Override
	public Object[] subSet(K lo, K hi) {
		lock.readLock();
		Object[] returnValue = map.subSet(lo, hi);
		lock.readUnlock();
		return returnValue;
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return map.entrySet();
	}

	@Override
	public void subSet(K lo, K hi, Consumer<K> consumer) {
		lock.readLock();
		map.subSet(lo, hi, consumer);
		lock.readUnlock();
	}

}
