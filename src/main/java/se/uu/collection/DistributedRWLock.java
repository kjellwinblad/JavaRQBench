package se.uu.collection;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;

import sun.misc.Unsafe;

public class DistributedRWLock {
    private static final Unsafe unsafe;

    volatile long writeBarrier = 0;
    
    private static final AtomicLongFieldUpdater<DistributedRWLock> writeBarrierUpdater =
            AtomicLongFieldUpdater.newUpdater(DistributedRWLock.class, "writeBarrier");
    
    private StampedLock stampedLock = new StampedLock();
    private Lock lock = stampedLock.asWriteLock();
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception ex) { 
            throw new Error(ex);
        }
    }
    
    private byte[] localStatisticsArray = new byte[128*2 + (int)LOCAL_STATISTICS_ARRAY_SLOT_SIZE*NUMBER_OF_LOCAL_STATISTICS_SLOTS];


    private static final int NUMBER_OF_LOCAL_STATISTICS_SLOTS = Runtime.getRuntime().availableProcessors();
    private static final long LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET = 0;
    private static final long LOCAL_STATISTICS_ARRAY_SIZE_OFFSET = 8;
    private static final long LOCAL_STATISTICS_ARRAY_START_OFFSET = 
        128 + unsafe.ARRAY_BYTE_BASE_OFFSET;
    private static final long LOCAL_STATISTICS_ARRAY_SLOT_SIZE = 192;
    
    
    public void indicateReadStart(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;

        long writeIndicatorOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        long prevValue;

        prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                           writeIndicatorOffset);
        while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                          writeIndicatorOffset,
                                          prevValue,
                                         prevValue + 1)){
            unsafe.fullFence();
            unsafe.fullFence();
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               writeIndicatorOffset);
        }
    }

    public void indicateReadEnd(){
        long slot = Thread.currentThread().getId() % NUMBER_OF_LOCAL_STATISTICS_SLOTS;
        long writeIndicatorOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            slot * LOCAL_STATISTICS_ARRAY_SLOT_SIZE +
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        long prevValue;
        prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                           writeIndicatorOffset);
        while(!unsafe.compareAndSwapLong(localStatisticsArray,
                                         writeIndicatorOffset,
                                         prevValue,
                                         prevValue - 1)){
            unsafe.fullFence();
            unsafe.fullFence();
            prevValue = unsafe.getLongVolatile(localStatisticsArray,
                                               writeIndicatorOffset);
        }
    }
    
    private void waitNoOngoingRead(){
        long currentOffset = 
            LOCAL_STATISTICS_ARRAY_START_OFFSET + 
            LOCAL_STATISTICS_ARRAY_WRITE_INDICATOR_OFFSET;
        for(int i = 0; i < NUMBER_OF_LOCAL_STATISTICS_SLOTS; i++){
            while(0 != unsafe.getLongVolatile(localStatisticsArray,
                                              currentOffset)){
                unsafe.fullFence();
                unsafe.fullFence();
            }
            currentOffset = currentOffset + LOCAL_STATISTICS_ARRAY_SLOT_SIZE;
        }
    }
    
    
    public void lock(){
    	while(writeBarrier != 0L){
    		Thread.yield();
    	}
    	lock.lock();
    	waitNoOngoingRead();
    }

    public void unlock(){
    	lock.unlock();
    }
    
    public void readLock(){
    	boolean barrierRaised = false;
    	int patience = 10000;
    	while(true){
    		indicateReadStart();
    		if(stampedLock.isWriteLocked()){
    			indicateReadEnd();
    			while(stampedLock.isWriteLocked()){
    				Thread.yield();
    				if(patience == 0 && !barrierRaised){
    					writeBarrierUpdater.incrementAndGet(this);
    					barrierRaised = true;
    				}
    				patience--;
    			}
    		}else{
    			break;
    		}
    	}
		if(barrierRaised){
			writeBarrierUpdater.decrementAndGet(this);
		}
    }

    public void readUnlock(){
    	indicateReadEnd();    	
    }
    
}
