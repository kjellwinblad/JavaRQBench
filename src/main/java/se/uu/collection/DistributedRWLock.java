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
