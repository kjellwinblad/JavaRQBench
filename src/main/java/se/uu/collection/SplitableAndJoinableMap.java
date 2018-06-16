package se.uu.collection;

import java.util.Map;

public interface SplitableAndJoinableMap<K, V> extends Map<K,V>{
    public SplitableAndJoinableMap<K, V> join(SplitableAndJoinableMap<K, V> right);
    public SplitableAndJoinableMap<K, V> split(Object[] splitKeyWriteBack,
                                               SplitableAndJoinableMap<K, V>[] rightTreeWriteBack);
}
