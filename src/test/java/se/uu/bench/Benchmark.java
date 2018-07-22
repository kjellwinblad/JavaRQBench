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

package se.uu.bench;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;


final class Sleeper { // Code based on code taken from https://gist.github.com/bric3/314c3d01a80e5e3c158965dcd459a8a5
	private static final long LONG_SLEEP_LIMIT = TimeUnit.MILLISECONDS.toNanos(1500);
	private static final long SLEEP_PRECISION = TimeUnit.MILLISECONDS.toNanos(200);
//    private static final long SPIN_YIELD_PRECISION = TimeUnit.MILLISECONDS.toNanos(40);


    /* Spin-yield loop based alternative to Thread.sleep
     * Based on the code of Andy Malakov
     * http://andy-malakov.blogspot.fr/2010/06/alternative-to-threadsleep.html
     */
    public static void sleepNanos(long nanoDuration) throws InterruptedException {
        final long end = System.nanoTime() + nanoDuration;
        long timeLeft = nanoDuration;
        do {
          if(timeLeft > LONG_SLEEP_LIMIT) {
            TimeUnit.NANOSECONDS.sleep(nanoDuration);
            //System.err.println("NANOSLEEP");
            return;
          } else if (timeLeft > SLEEP_PRECISION) {
            Thread.sleep(1);
            //System.err.println("SLEEP");
            }// else {
                //if (timeLeft > SPIN_YIELD_PRECISION) {
		    //System.err.println("YIELD");
		  //Thread.yield();
                //}
            //}
            timeLeft = end - System.nanoTime();

            //if (Thread.interrupted())
            //    throw new InterruptedException();
        } while (timeLeft > 0);
    }
}

abstract public class Benchmark{


    AtomicLongArray paddedStoppedFlag = new AtomicLongArray(33);
    protected volatile boolean shallStart = false;
    protected String additionalMeasurmentString = "";
    private long oddThreadsMeasurments = 0;
    private boolean timeLimited;
    private int nrOfWarmUpRuns;
    protected double warmUpRunSeconds;
    private int nrOfMeasurmentRuns;
    private double measurmentRunSeconds;
    private ResultReportType reportType;
    protected int nrOfThreads;
    protected CyclicBarrier waitBarrier = null;
    protected String setType;
    protected long globalAvoidOptOutSum = 0;

    public enum ResultReportType {
        ALL,AVERAGE_MIN_MAX,AVERAGE,AVERAGE_THROUGHPUT
    }

    protected abstract class WorkerThread extends Thread{
        protected int[] threadLocalWork = new int[128];
    	protected long measurment = 0;
        protected long avoidOptOutSum = 0;

        public long getMeasurment(){
            return measurment;
        }

        public long getAvoidOptOutSum(){
            return measurment;
        }

        public abstract void setUp();
        
        public void doLocalWork(int numberOfLocalWorkUnits){
        	for(int i = 0; i < numberOfLocalWorkUnits; i++){
        		threadLocalWork[ThreadLocalRandom.current().nextInt(128)]++;
        		threadLocalWork[ThreadLocalRandom.current().nextInt(128)]--;
        	}
        }
        
    }

    protected WorkerThread[] threads;
	private boolean oddEvenThreadDiffer;

    protected interface AbstractElem extends Comparable<AbstractElem>{
        public int compareTo(AbstractElem o);
        public String toString();
        public int hashCode();
        public int getKey();
        public void setKey(int key);
    }

    protected final class Elem implements AbstractElem{
        private int key;
        public Elem(int key){
            this.key = key;
        }
        public int compareTo(AbstractElem o){
            return key - o.getKey();
        }
        public String toString(){
            return new Integer(key).toString();
        }
        public int hashCode() {
            return key * 0x9e3775cd;
        }
        public boolean equals(Object elem) {
            return ((AbstractElem)elem).getKey() == key;
        }
        public int getKey(){
            return key;
        }

        public void setKey(int key){
            this.key = key;
        }
        
    }

    //We do not want any false sharing in this elements
    protected final class WorkingElem implements AbstractElem{
        private int key[] = new int[65];
        public int compareTo(AbstractElem o){
            return key[32] - o.getKey();
        }
        public String toString(){
            return new Integer(key[32]).toString();
        }
        public int hashCode() {
            return key[32] * 0x9e3775cd;
        }
        
        public int getKey(){
            return key[32];
        }
        
        public void setKey(int key){
            this.key[32] = key;
        }
    };

    abstract protected void setUp() throws Exception;


    public long tearDown() throws Exception{
        long sum = 0;
        long sum2 = 0;
        for(int i = 0; i < nrOfThreads; i++){
            threads[i].join();
            if(!oddEvenThreadDiffer || i % 2 == 0){
            	sum = sum + threads[i].getMeasurment();
            }else {
            	sum2 =  sum2 + threads[i].getMeasurment();
            }
            globalAvoidOptOutSum = globalAvoidOptOutSum + threads[i].getAvoidOptOutSum();
            threads[i] = null;
        }
        paddedStoppedFlag.set(16, 0);
        shallStart = false;
        Thread.sleep(100 /* Modified for time series experiment original 1000*/);
        System.err.println("#"+globalAvoidOptOutSum+"#");
        if(oddEvenThreadDiffer){
        	oddThreadsMeasurments = sum2;
        }
        return sum;
    }

    public boolean stopped(){       
        return paddedStoppedFlag.get(16) == 1;
    }

    public boolean shallStart(){
        return shallStart;
    }

    public Benchmark(boolean timeLimited,
            		int nrOfWarmUpRuns,
            		double warmUpRunSeconds,
            		int nrOfMeasurmentRuns,
            		double measurmentRunSeconds,
            		ResultReportType reportType,
            		int nrOfThreads,
            		String setType){
this.oddEvenThreadDiffer = false;
this.nrOfWarmUpRuns = nrOfWarmUpRuns;
this.warmUpRunSeconds = warmUpRunSeconds;
this.nrOfMeasurmentRuns = nrOfMeasurmentRuns;
this.measurmentRunSeconds = measurmentRunSeconds;
this.reportType = reportType;
this.nrOfThreads = nrOfThreads;
this.timeLimited = timeLimited;
this.setType = setType;
}
    
    public Benchmark(boolean oddEvenThreadDiffer,
    				 boolean timeLimited,
                     int nrOfWarmUpRuns,
                     double warmUpRunSeconds,
                     int nrOfMeasurmentRuns,
                     double measurmentRunSeconds,
                     ResultReportType reportType,
                     int nrOfThreads,
                     String setType){
    	this(timeLimited,
             nrOfWarmUpRuns,
             warmUpRunSeconds,
             nrOfMeasurmentRuns,
             measurmentRunSeconds,
             reportType,
             nrOfThreads,
             setType);
    	this.oddEvenThreadDiffer = oddEvenThreadDiffer;
    }

    public void start() throws Exception{
        long warmUpRunNanos = (long)(warmUpRunSeconds * 1000000000);
        long measurmentRunNanos = (long)(measurmentRunSeconds * 1000000000);
        //Warm up
        if(!timeLimited){
            waitBarrier = new CyclicBarrier(nrOfThreads + 1);
        }
        for(int i = 0; i < nrOfWarmUpRuns; i++){
            System.err.println("=> Seting up warmup run");
            setUp();
            System.err.println("== Benchmark setup completed");
            Thread.sleep(100);
            System.err.println("=> Starting warm up run");
            long started = System.nanoTime();
            shallStart = true;
            if(timeLimited){
                Sleeper.sleepNanos(warmUpRunNanos);
            }else{
                waitBarrier.await();
            }
            paddedStoppedFlag.set(16, 1);
            long ended = System.nanoTime();
            long nrOfOpsPerformed = tearDown();
            double throuput = new Long(nrOfOpsPerformed).doubleValue() / new Long((ended - started)).doubleValue();
            System.err.println("== Warmup run completed " + throuput + " ops/nanosecond ("+(ended - started)+")");
            System.err.println("== nrOfOpsPerformed " + nrOfOpsPerformed );
            if(oddEvenThreadDiffer){
                double throuput2 = new Long(oddThreadsMeasurments).doubleValue() / new Long((ended - started)).doubleValue();
                System.err.println("== Odd threads throuput " + throuput2 + " ops/nanosecond");
            }
        }
        
        long[] benchmarkResults = new long[nrOfMeasurmentRuns];
        long[] benchmarkResults2 = new long[nrOfMeasurmentRuns];
        String[] additionalMeasurmentStrings = new String[nrOfMeasurmentRuns];
        long[] benchmarkTimes = new long[nrOfMeasurmentRuns];
        //Benchmark
        for(int i = 0; i < nrOfMeasurmentRuns; i++){
            System.err.println("=> Seting up benchmark run");
            setUp();
            System.err.println("== Benchmark setup completed");
            Thread.sleep(1000);
            System.err.println("=> Starting benchmark run");
	    System.err.println("EXPECTED RUN TIME "+measurmentRunNanos);
            long started = System.nanoTime();
            shallStart = true;
            if(timeLimited){
                Sleeper.sleepNanos(measurmentRunNanos);
            }else{
                waitBarrier.await();
            }
            paddedStoppedFlag.set(16, 1);
            long ended = System.nanoTime();
            long nrOfOpsPerformed = tearDown();
            benchmarkResults[i] = nrOfOpsPerformed;
            additionalMeasurmentStrings[i] = additionalMeasurmentString;
            if(oddEvenThreadDiffer){
            	benchmarkResults2[i] = oddThreadsMeasurments;
            }
            benchmarkTimes[i] = ended - started;
            double throuput = new Long(nrOfOpsPerformed).doubleValue() / new Long((ended - started)).doubleValue();
	    System.err.println("RUNNING TIME: " + new Long((ended - started)).doubleValue());
            System.err.println("== Benchmark run completed " + throuput + " ops/nanosecond");
            System.err.println("== nrOfOpsPerformed " + nrOfOpsPerformed );
            if(oddEvenThreadDiffer){
                double throuput2 = new Long(oddThreadsMeasurments).doubleValue() / new Long((ended - started)).doubleValue();
                System.err.println("== Odd threads throuput " + throuput2 + " ops/nanosecond");
            }
        }
        System.out.print(setType + " " + nrOfThreads + " ");
        if(reportType == ResultReportType.ALL){          
            for(int i = 0; i < nrOfMeasurmentRuns; i++){
                System.out.print(benchmarkTimes[i]);
                System.out.print(" ");
                System.out.print(benchmarkResults[i]);
                if(oddEvenThreadDiffer){
                    System.out.print(" ");
                    System.out.print(benchmarkResults2[i]);
                }
                if(!additionalMeasurmentStrings[0].equals("")){
                	System.out.print(" ");
                	System.out.print(additionalMeasurmentStrings[i]);
                }
                if(i < (nrOfMeasurmentRuns-1)){
                    System.out.print(" ");
                }
            }
            System.out.print("\n");
        }else if(reportType == ResultReportType.AVERAGE_MIN_MAX){
            long sumMeasurments = 0;
            long sumTimes = 0;

            long minMeasurment = Long.MAX_VALUE;
            int minMeasurmentIndex = -1;
            long maxMeasurment = Long.MIN_VALUE;
            int maxMeasurmentIndex = -1;

            long minTime = Long.MAX_VALUE;
            int minTimeIndex = -1;
            long maxTime = Long.MIN_VALUE;
            int maxTimeIndex = -1;
            for(int i = 0; i < nrOfMeasurmentRuns; i++){
                if(benchmarkTimes[i] < minTime){
                    minTime = benchmarkTimes[i];
                    minTimeIndex = i;
                }
                if(benchmarkTimes[i] > maxTime){
                    maxTime = benchmarkTimes[i];
                    maxTimeIndex = i;
                }
                if(benchmarkResults[i] < minMeasurment){
                    minMeasurment = benchmarkResults[i];
                    minMeasurmentIndex = i;
                }
                if(benchmarkResults[i] > maxMeasurment){
                    maxMeasurment = benchmarkResults[i];
                    maxMeasurmentIndex = i;
                }
                sumTimes = sumTimes + benchmarkTimes[i];
                sumMeasurments = sumMeasurments + benchmarkResults[i];               
            }
            System.out.print(((double)sumTimes)/nrOfMeasurmentRuns + " ");
            System.out.print(((double)sumMeasurments)/nrOfMeasurmentRuns + " ");
            System.out.print(benchmarkTimes[minTimeIndex] + " " + benchmarkResults[minTimeIndex] + " ");
            System.out.print(benchmarkTimes[maxTimeIndex] + " " + benchmarkResults[maxTimeIndex] + " ");
            System.out.print(benchmarkTimes[minMeasurmentIndex] + " " + benchmarkResults[minMeasurmentIndex] + " ");
            System.out.print(benchmarkTimes[maxMeasurmentIndex] + " " + benchmarkResults[maxMeasurmentIndex]);
        }else if(reportType == ResultReportType.AVERAGE){
            long sumMeasurments = 0;
            long sumTimes = 0;
            for(int i = 0; i < nrOfMeasurmentRuns; i++){
                sumTimes = sumTimes + benchmarkTimes[i];
                sumMeasurments = sumMeasurments + benchmarkResults[i];               
            }
            System.out.print(((double)sumTimes)/nrOfMeasurmentRuns + " ");
            System.out.println(((double)sumMeasurments)/nrOfMeasurmentRuns);
        }else if(reportType == ResultReportType.AVERAGE_THROUGHPUT){
            long sumMeasurments = 0;
            long sumTimes = 0;
            for(int i = 0; i < nrOfMeasurmentRuns; i++){
                sumTimes = sumTimes + benchmarkTimes[i];
                sumMeasurments = sumMeasurments + benchmarkResults[i];               
            }
            System.out.println(((double)sumMeasurments)/((double)sumTimes));
        }
    }

}
