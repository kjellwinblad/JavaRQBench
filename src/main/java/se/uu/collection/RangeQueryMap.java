package se.uu.collection;

import java.util.Map;
import java.util.function.Consumer;

public interface RangeQueryMap<K, V> extends Map<K,V>{
	public Object[] subSet(final K lo, final K hi);
	public void subSet(final K lo, final K hi, Consumer<K> consumer);
}
