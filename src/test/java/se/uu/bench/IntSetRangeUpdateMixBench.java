package se.uu.bench;

import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.Map;
import java.util.function.*;
import java.util.ArrayList;
import java.util.Collections;


//import se.uu.collection.LockFreeRangeCollectorSkipList;
import se.uu.collection.RangeUpdateMap;

public class IntSetRangeUpdateMixBench extends Benchmark{

    private final int keyRangeSize;
    private final int prefillNrOfOps;
    private final double percentageReads;
    private RangeUpdateMap<Integer,Integer> benchMap = null;
    private double percentageRangeQueries;
    private int maxRangeSize;
    private double percentageRangeUpdates;
    private int maxRangeSizeUpdate;
	private final Integer[] keys;
	private boolean changeRangeQuerySize;
	private int rangeQuereisInitialSize;

	Integer getKey(int key){
		return keys[key];
	}
	
    private class MixWorkerThread extends WorkerThread{

		long numberOfRangeQueries = 0;
		long numberOfTraversedItemsRangeQueries = 0;
		long traversedNodes = 0;
		long nrOfSplits = 0;
		long nrOfJoins = 0;
    	
        public void setUp(){}
        
        public void run(){
            final int hundredPercentInt = 1000000;
            final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            final int deleteLimit = (int)(hundredPercentInt * ((percentageReads + percentageRangeQueries + percentageRangeUpdates) + ((1.0 - percentageReads - percentageRangeQueries - percentageRangeUpdates) / 2.0)));
            final int rangeUpdateLimit = (int)(hundredPercentInt * (percentageReads + percentageRangeQueries + percentageRangeUpdates));
            final int rangeQueryLimit = (int)(hundredPercentInt * (percentageReads + percentageRangeQueries));
            final int localPercentageReads = (int)((hundredPercentInt) * percentageReads);
            final RangeUpdateMap<Integer,Integer> localBenchMap = benchMap;
            //We do not want any false sharing in this elements
            //final Elem workingElement = new Elem(1);
            //final Elem rangeWorkingElementStart = new Elem(1);
            //final Elem rangeWorkingElementEnd = new Elem(1);
            final int localKeyRangeSize = keyRangeSize;
            final int localMaxRangeSize = maxRangeSize;
            final int halfRangeSize = maxRangeSize/2;
            final int localMaxRangeSizeUpdate = maxRangeSizeUpdate;
            final int halfRangeSizeUpdate = maxRangeSize/2;
            final long[] localAvoidOptOutSum = new long[34];
            long localMeasurment = 0;
            // System.err.println("localPercentageReads "+localPercentageReads);
            // System.err.println("rangeQueryLimit "+rangeQueryLimit);
            // System.err.println("localMaxRangeSize "+localMaxRangeSize);
            // System.err.println("rangeUpdateLimit "+rangeUpdateLimit);
            // System.err.println("localMaxRangeSizeUpdate "+localMaxRangeSizeUpdate);
            // System.err.println("deleteLimit "+deleteLimit);
            // System.err.println("hundredPercentInt "+hundredPercentInt);

	    final Consumer<Integer> rangeConsumer = new Consumer<Integer>(){
		    public void accept(Integer k){
			localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + k.intValue();
			localAvoidOptOutSum[17]++;
		    }
		};

	    while(!shallStart()){
                //Thread.yield();
            }
            while( ! stopped() ){
                //do operation here
                int opRand = randomGen.nextInt(hundredPercentInt);
                if(opRand < localPercentageReads){
                    Integer workingElement = getKey(randomGen.nextInt(localKeyRangeSize));
                    Integer elem = localBenchMap.get(workingElement);//localBenchMap.get(new Elem(randomGen.nextInt(localKeyRangeSize)));
                    localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + (elem == null ? 3 : elem.intValue());
                }else if (opRand < rangeQueryLimit){
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSize;
                	if(rangeStart < 0){
                		rangeStart = 0;
                	}
                	int sizeOfRange = randomGen.nextInt(localMaxRangeSize);
                	Integer rangeWorkingElementStart = getKey(rangeStart);
                	Integer rangeWorkingElementEnd = getKey(rangeStart + sizeOfRange);
                	localBenchMap.subSet(
                					rangeWorkingElementStart,
                					rangeWorkingElementEnd,
                					rangeConsumer);//(k) -> localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + k.getKey()//localBenchMap.subSet(new Elem(rangeStart), new Elem(rangeStart + sizeOfRange));//localBenchMap.subSet(rangeWorkingElementStart, rangeWorkingElementEnd);
                	localAvoidOptOutSum[18]++;
                	//                	for(int i = 0; i < subset.length; i++){
//                		localAvoidOptOutSum = localAvoidOptOutSum + ((AbstractElem)subset[i]).getKey();
//                	}
                }else if (opRand < rangeUpdateLimit){
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSizeUpdate;
                	if(rangeStart < 0){
                		rangeStart = 0;
                	}
                	int sizeOfRange = randomGen.nextInt(localMaxRangeSizeUpdate);
                	Integer rangeWorkingElementStart = getKey(rangeStart);
                	Integer rangeWorkingElementEnd = getKey(rangeStart + sizeOfRange);
                	localBenchMap.rangeUpdate(rangeWorkingElementStart,rangeWorkingElementEnd, (key, value) -> value.intValue()/2 + 1);//new Elem(rangeStart), new Elem(rangeStart + sizeOfRange), (key, value) -> new Elem(value.getKey() + 1));
                }else if (opRand < deleteLimit){
                    Integer elem = getKey(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.put(elem, elem);
                }else{
                    Integer workingElement = getKey(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.remove(workingElement);//(new Elem(randomGen.nextInt(localKeyRangeSize)));
                }
                localMeasurment++;
            }
            measurment = localMeasurment;
            numberOfRangeQueries =localAvoidOptOutSum[18];
            numberOfTraversedItemsRangeQueries = localAvoidOptOutSum[17];
    		if (benchMap instanceof se.uu.collection.ImmTreapCATreeMapSTDR) {
    			traversedNodes =((se.uu.collection.ImmTreapCATreeMapSTDR) benchMap).getTraversedNodes();
    		}
    		if (benchMap instanceof se.uu.collection.LockFreeImmTreapCATreeMapSTDR) {
    			traversedNodes =((se.uu.collection.LockFreeImmTreapCATreeMapSTDR) benchMap).getTraversedNodes();
    			nrOfJoins =((se.uu.collection.LockFreeImmTreapCATreeMapSTDR) benchMap).getNrOfJoins();
    			nrOfSplits =((se.uu.collection.LockFreeImmTreapCATreeMapSTDR) benchMap).getNrOfSplits();

    		}
            avoidOptOutSum = localAvoidOptOutSum[16];
        }
    }


	public void setUp() throws Exception {
		@SuppressWarnings("unchecked")
		Class<RangeUpdateMap<Integer, Integer>> cls = (Class<RangeUpdateMap<Integer, Integer>>) Class.forName(setType);
		if (benchMap == null) {
			benchMap = cls.newInstance();
			ThreadLocalRandom randomGen = ThreadLocalRandom.current();
			HashSet<Integer> keys = new HashSet<>();
			while (keys.size() < prefillNrOfOps) {// Put initial elements in
														// map
				Integer elem = getKey(randomGen.nextInt(keyRangeSize));
				keys.add(elem);
			}
			ArrayList<Integer> keysArray = new ArrayList<Integer>();
			keysArray.addAll(keys);
			Collections.shuffle(keysArray);
			for(Integer key : keysArray){
				benchMap.put(key, key);	
			}
			keys= null;
			keysArray= null;
		}
		// Give GC and JIT some time
		//System.gc();
		Thread.sleep(500 /*Modified for time series orignal (5000)*/);
		threads = new MixWorkerThread[nrOfThreads];
		if(changeRangeQuerySize) {
			runTriggeringRun();			
		}else {
			for (int i = 0; i < nrOfThreads; i++) {
				threads[i] = new MixWorkerThread();
				threads[i].setUp();
				threads[i].start();
			}
		}
	}
    
    private void runTriggeringRun() throws Exception{
    	// Five seconds triggering run
    	//long warmUpRunNanos = (long)(5 * 1000000000);
        int tempMaxRangeSize = maxRangeSize;
        maxRangeSize = rangeQuereisInitialSize;
    	System.err.println("=> Starting triggering run");
		for (int i = 0; i < nrOfThreads; i++) {
			threads[i] = new MixWorkerThread();
			threads[i].setUp();
			threads[i].start();
		}
        long started = System.nanoTime();
        shallStart = true;
        //Sleeper.sleepNanos(5 * 1000000000);
	Thread.sleep(2200);
        paddedStoppedFlag.set(16, 1);
        long ended = System.nanoTime();
        long nrOfOpsPerformed = tearDown(false);
        double throuput = new Long(nrOfOpsPerformed).doubleValue() / new Long((ended - started)).doubleValue();
        System.err.println("== Triggering run completed " + throuput + " ops/nanosecond ("+(ended - started)+")");
        System.err.println("== nrOfOpsPerformed " + nrOfOpsPerformed );
    	
    	//System.gc();
		Thread.sleep(2000 /*Modified for time series orignal (5000)*/);
		maxRangeSize = tempMaxRangeSize;
		threads = new MixWorkerThread[nrOfThreads];
		for (int i = 0; i < nrOfThreads; i++) {
			threads[i] = new MixWorkerThread();
			threads[i].setUp();
			threads[i].start();
		}
	}

	// public void setUp() throws Exception{
    //     @SuppressWarnings("unchecked")
    //     Class<RangeUpdateMap<Integer,Integer>> cls = (Class<RangeUpdateMap<Integer,Integer>>)Class.forName(setType);
    //     if(benchMap == null){
    //         benchMap = cls.newInstance();
    //         ThreadLocalRandom randomGen = ThreadLocalRandom.current();
    //         for(int i = 0; i < prefillNrOfOps; i++){//Put initial elements in map
    //             Integer elem = getKey(randomGen.nextInt(keyRangeSize));
    //             benchMap.put(elem, elem);
    //         }
    //     }
    //     //Give GC and JIT some time
    //     System.gc();
    //     Thread.sleep(5000);
    //     threads = new MixWorkerThread[nrOfThreads];
    //     for(int i = 0; i < nrOfThreads; i++){
    //         threads[i] = new MixWorkerThread();
    //         threads[i].setUp();
    //         threads[i].start();
    //     }
    // }


    public long tearDown(boolean clearDataStructure) throws Exception{
        long numberOfRangeQueries = 0;
        long numberOfTraversedItemsRangeQueries = 0;
        long traversedNodes = 0;
        long nrOfSplits = 0;
        long nrOfJoins = 0;
        long numberOfRouteNodes = 0;
        for(int i = 0; i < nrOfThreads; i++){
            threads[i].join();
            numberOfRangeQueries = numberOfRangeQueries +((MixWorkerThread)threads[i]).numberOfRangeQueries;
            numberOfTraversedItemsRangeQueries = numberOfTraversedItemsRangeQueries +((MixWorkerThread)threads[i]).numberOfTraversedItemsRangeQueries;
            traversedNodes = traversedNodes +((MixWorkerThread)threads[i]).traversedNodes;
            nrOfJoins = nrOfJoins +((MixWorkerThread)threads[i]).nrOfJoins;
            nrOfSplits = nrOfSplits +((MixWorkerThread)threads[i]).nrOfSplits;
        }
	    if(benchMap instanceof se.uu.collection.CATreeMapSTDR){
	    	numberOfRouteNodes = ((se.uu.collection.CATreeMapSTDR)benchMap).numberOfRouteNodes();
	    	System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.CATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
	    if(benchMap instanceof se.uu.collection.FatCATreeMapSTDR){
	    	numberOfRouteNodes = ((se.uu.collection.FatCATreeMapSTDR)benchMap).numberOfRouteNodes();
	    	System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.FatCATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
	    if(benchMap instanceof se.uu.collection.ImmTreapCATreeMapSTDR){
	    	numberOfRouteNodes = ((se.uu.collection.ImmTreapCATreeMapSTDR)benchMap).numberOfRouteNodes();
	    	System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.ImmTreapCATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
	    if(benchMap instanceof se.uu.collection.LockFreeImmTreapCATreeMapSTDR){
	    	numberOfRouteNodes = ((se.uu.collection.LockFreeImmTreapCATreeMapSTDR)benchMap).numberOfRouteNodes();
	    	System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.LockFreeImmTreapCATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
        additionalMeasurmentString = numberOfRangeQueries + " " + numberOfTraversedItemsRangeQueries + " " + traversedNodes + " " +numberOfRouteNodes + " " + nrOfJoins + " " + nrOfSplits;
        long results = super.tearDown();
        ThreadLocalRandom randomGen = ThreadLocalRandom.current();
        Integer e = benchMap.get(getKey((randomGen.nextInt(keyRangeSize))));
        globalAvoidOptOutSum = globalAvoidOptOutSum + (e == null ? 3 : e.intValue());
        if(benchMap instanceof contention.abstractions.MaintenanceAlg){
            ((contention.abstractions.MaintenanceAlg)benchMap).stopMaintenance();
        }
        if(clearDataStructure) {
        	benchMap = null;
        }
        return results;
    }
    
    public long tearDown() throws Exception{
        return tearDown(true);
    }

    public IntSetRangeUpdateMixBench(String setType,
                                  int nrOfThreads,
                                  int nrOfWarmUpRuns,
                                  double warmUpRunSeconds,
                                  int nrOfMeasurmentRuns,
                                  double measurmentRunSeconds,
                                  ResultReportType reportType,
                                  int keyRangeSize,
                                  int prefillNrOfOps,
                                  double percentageReads,
                                  double percentageRangeQueries,
                                  int maxRangeSize,
                                  double percentageRangeUpdates,
                                  int maxRangeSizeUpdate,
                                  boolean changeRangeQuerySize, 
                                  int rangeQuereisInitialSize){
        super(true,
              nrOfWarmUpRuns,
              warmUpRunSeconds,
              nrOfMeasurmentRuns,
              measurmentRunSeconds,
              reportType,
              nrOfThreads,
              setType);
        //kiwi.KiWi.MAX_THREADS = nrOfThreads;
        //LockFreeRangeCollectorSkipList.num_Threads = nrOfThreads;
        System.err.println("MEASURMENT RUN SECONDS " + measurmentRunSeconds);
        this.keyRangeSize = keyRangeSize;
        this.keys = new Integer[this.keyRangeSize + Math.max(rangeQuereisInitialSize,Math.max(maxRangeSize, maxRangeSizeUpdate))*2];
        for (int i = 0; i < keys.length; i++) {
			keys[i] = i;
		}
        this.prefillNrOfOps = prefillNrOfOps;
        this.percentageReads = percentageReads;
        this.percentageRangeQueries = percentageRangeQueries;
        this.maxRangeSize = maxRangeSize;
        this.percentageRangeUpdates = percentageRangeUpdates;
        this.maxRangeSizeUpdate = maxRangeSizeUpdate;
        this.changeRangeQuerySize = changeRangeQuerySize;
        this.rangeQuereisInitialSize = rangeQuereisInitialSize;
        
    }

    public static void main(String [] args){
        if(!(args.length == 14 || args.length == 15)){
            System.err.println("Parameters:");
            System.err.println("");
            System.err.println("String setType,");
            System.err.println("int nrOfThreads,");
            System.err.println("int nrOfWarmUpRuns,");
            System.err.println("double warmUpRunSeconds,");
            System.err.println("int nrOfMeasurmentRuns,");
            System.err.println("double measurmentRunSeconds,");
            System.err.println("ResultReportType reportType, (ALL)");
            System.err.println("int keyRangeSize");
            System.err.println("int prefillNrOfOps");
            System.err.println("double percentageReads");
            System.err.println("double percentageRangeQueries");
            System.err.println("int maxRangeSize");
            System.err.println("double percentageRangeUpdates");
            System.err.println("int maxRangeSizeUpdate");
            System.err.println("");
            System.err.println("Example:");
            System.err.println("");
            System.err.println("java -server -cp ../target/scala-2.11/test-classes/:../classes/:. se.uu.bench.SetRangeUpdateMixBench se.uu.collection.CATreeMap 4 5 3 3 3 ALL 1000000 100000 0.1 0.4 100");
            System.exit(0);
        }
        boolean changeRangeQuerySize = false;
        int rangeQuereisInitialSize = 0;
        if(args.length == 15) {
        	changeRangeQuerySize = true;
        	rangeQuereisInitialSize = new Integer(args[14]);
        }
        IntSetRangeUpdateMixBench bench = new IntSetRangeUpdateMixBench(args[0],    // String setType
                                                                  new Integer(args[1]), // int nrOfThreads
                                                                  new Integer(args[2]), // int nrOfWarmUpRuns
                                                                  new Double(args[3]),  // double warmUpRunSeconds
                                                                  new Integer(args[4]), // int nrOfMeasurmentRuns
                                                                  new Double(args[5]),  // double measurmentRunSeconds
                                                                  Enum.valueOf(Benchmark.ResultReportType.class, args[6]), // ResultReportType reportType
                                                                  new Integer(args[7]), // int keyRangeSize
                                                                  new Integer(args[8]), // int prefillNrOfOps);
                                                                  new Double(args[9]),  // double percentageReads
                                                                  new Double(args[10]), // double percentageRangeQueries
                                                                  new Integer(args[11]),// int maxRangeSize
                                                                  new Double(args[12]), // double percentageRangeUpdates
                                                                  new Integer(args[13]),
                                                                  changeRangeQuerySize,
                                                                  rangeQuereisInitialSize
                                                                  );// int maxRangeSizeUpdate
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
