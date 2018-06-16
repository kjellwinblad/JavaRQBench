package se.uu.collection;

public interface StatisticsLock {
    public boolean tryLock();
    public void lock();
    public void unlock();
    public int getStatistics();
    public void resetStatistics();
    public int getHighContentionLimit();
    public int getLowContentionLimit();
    public boolean isHighContentionLimitReached();
    public boolean isLowContentionLimitReached();
}
