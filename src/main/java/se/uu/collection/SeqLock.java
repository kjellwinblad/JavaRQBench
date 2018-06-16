package se.uu.collection;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import sun.misc.Unsafe;

public class SeqLock {
	
    volatile long seqNumber = 2L;
    private int statLockStatistics = 0;
    
    private static final AtomicLongFieldUpdater<SeqLock> seqNumberUpdater =
            AtomicLongFieldUpdater.newUpdater(SeqLock.class, "seqNumber");
    
    private static final Unsafe unsafe;

    private static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
    private static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
    private static final int STAT_LOCK_FAILURE_CONTRIB = 250;
    private static final int STAT_LOCK_SUCCESS_CONTRIB = 1;
    
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception ex) { 
            throw new Error(ex);
        }
    }
    
	public boolean tryLock() {
    	long readSeqNumber = seqNumber;
		if((readSeqNumber % 2) != 0){
			return false;
		}else{
			boolean success = seqNumberUpdater.compareAndSet(this, readSeqNumber, readSeqNumber + 1);
			if(success){
				return true;
			}else{
				return false;
			}
		}
	}
    
    public void lock(){
    	while(true){
        	long readSeqNumber = seqNumber;	
        	while((readSeqNumber % 2) != 0){
        		unsafe.fullFence();
        		unsafe.fullFence();
        		readSeqNumber = seqNumber;
        	}
        	if(seqNumberUpdater.compareAndSet(this, readSeqNumber, readSeqNumber + 1)){
        		break;
        	}
    	}
    }

    public void unlock(){
    	seqNumber = seqNumber + 1;
    }
    
    public boolean isWriteLocked(){
    	return (seqNumber % 2) != 0;
    }
    

	public long tryOptimisticRead() {
		long readSeqNumber = seqNumber;
		if((readSeqNumber % 2) != 0){
			return 0;
		}else{
			return readSeqNumber;
		}
	}

	public boolean validate(long optimisticReadToken) {
		unsafe.loadFence();
		long readSeqNumber = seqNumber;
		return readSeqNumber == optimisticReadToken;
	}
	
	
    public void lockUpdateStatistics(){
        if (tryLock()) {
            statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
            return;
        }
        lock();
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void addToContentionStatistics(){
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void subFromContentionStatistics(){
        statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
    }

    
    public int getLockStatistics(){
    	return statLockStatistics;
    }
    
    public void resetStatistics(){
        statLockStatistics = 0;
    }

    public boolean isHighContentionLimitReached(){
        return statLockStatistics > STAT_LOCK_HIGH_CONTENTION_LIMIT;
    }
    
    public boolean isLowContentionLimitReached(){
        return statLockStatistics < STAT_LOCK_LOW_CONTENTION_LIMIT;
    }
    
}
