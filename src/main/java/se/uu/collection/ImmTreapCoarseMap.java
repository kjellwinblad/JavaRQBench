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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import se.uu.collection.ImmutableTreapMap.ImmutableTreapValue;
import sun.misc.Contended;

import java.util.*;

public class ImmTreapCoarseMap<K, V> extends AbstractMap<K, V>
		implements RangeQueryMap<K, V>, RangeUpdateMap<K, V> {
	@Contended
	private volatile ImmutableTreapValue<K,V> root = ImmutableTreapMap.createEmpty();
	private static final AtomicReferenceFieldUpdater<ImmTreapCoarseMap, ImmutableTreapValue> rootUpdater =
		     AtomicReferenceFieldUpdater.newUpdater(ImmTreapCoarseMap.class, ImmutableTreapValue.class, "root");
	
	long pad1;
	long pad2;
	long pad3;
	long pad4;
	long pad5;
	long pad6;
	long pad7;
	long pad8;
	private final Comparator<? super K> comparator;
	
	// === Constructors ============================

	public ImmTreapCoarseMap() {
		comparator = null;
	}

	public ImmTreapCoarseMap(Comparator<? super K> comparator) {
		this.comparator = comparator;
	}

	public int size() {

		int size = ImmutableTreapMap.size(root);
		return size;
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public boolean containsKey(Object key) {
		return get(key) != null;
	}


	public V get(Object key) {
		ImmutableTreapValue<K, V> theRoot = root;
		return ImmutableTreapMap.get(theRoot, (K)key, comparator);
	}

	public V put(K key, V value) {
		ImmutableTreapValue theRoot;
		ImmutableTreapValue newRoot;
		do{
			theRoot = root;
			newRoot = ImmutableTreapMap.put(theRoot, key, value, comparator);
		}while(!rootUpdater.compareAndSet(this, theRoot, newRoot));
		return (V) ImmutableTreapMap.getPrevValue();
	}

	public V putIfAbsent(K key, V value) {
		ImmutableTreapValue theRoot;
		ImmutableTreapValue newRoot;
		do{
			theRoot = root;
			newRoot = ImmutableTreapMap.putIfAbsent(theRoot, key, value, comparator);
		}while(!rootUpdater.compareAndSet(this, theRoot, newRoot));
		return (V)ImmutableTreapMap.getPrevValue();
	}

	public V remove(Object key) {
		ImmutableTreapValue theRoot;
		ImmutableTreapValue newRoot;
		do{
			theRoot = root;
			newRoot = ImmutableTreapMap.remove(theRoot, (K)key, comparator);
		}while(!rootUpdater.compareAndSet(this, theRoot, newRoot));
		return (V)ImmutableTreapMap.getPrevValue();
	}

	public void clear() {
		root = ImmutableTreapMap.createEmpty();
	}


	public Set<Map.Entry<K, V>> entrySet() {
		ImmutableTreapValue<K, V> theRoot = root;
		TreeSet<Map.Entry<K, V>> retVal = new TreeSet<>();
		ImmutableTreapMap.traverseAllItems(theRoot,
				(k, v) -> retVal.add(new AbstractMap.SimpleImmutableEntry<K, V>(k, v) {
					public int hashCode() {
						return k.hashCode();
					}
				}));
		return retVal;
	}

	public final Object[] subSet(final K lo, final K hi) {
		return null;

	}

	@SuppressWarnings("unchecked")
	public void subSet(final K lo, final K hi, Consumer<K> consumer) {
		ImmutableTreapValue<K, V> theRoot = root;
		ImmutableTreapMap.traverseKeysInRange(theRoot, lo, hi, consumer, comparator);
	
	}

	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		ImmTreapCoarseMap<Integer, Integer> map = new ImmTreapCoarseMap<>();
		for(int i = 0; i < 100; i++){
			map.put(i, i);
		}
		
		for(int i = 0; i < 10; i++){
			map.remove(i);
		}
		map.subSet(0, 101, (k) -> System.out.print(k + ", "));
		System.out.println();
		
		map.subSet(20, 30, (k) -> System.out.print(k + ", "));
		System.out.println();
	}

	@Override
	public void rangeUpdate(K lo, K hi, BiFunction<K, V, V> operation) {
		// TODO Auto-generated method stub
		
	}

}
