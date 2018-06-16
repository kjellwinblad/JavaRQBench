package se.uu.collection;

import java.util.function.BiFunction;

public interface RangeUpdateMap<K,V> extends RangeQueryMap<K, V> {
	public  void rangeUpdate(final K lo, final K hi, BiFunction<K,V,V> operation);
}
