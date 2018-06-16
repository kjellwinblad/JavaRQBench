package se.uu.bench;

import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.*;
import java.util.ArrayList;
import java.util.Collections;
//import se.uu.collection.LockFreeRangeCollectorSkipList;
import se.uu.collection.RangeUpdateMap;

public class IntSetRangeQueryAndUpdate extends Benchmark {

	private final int keyRangeSize;
	private final int prefillNrOfOps;
	private RangeUpdateMap<Integer, Integer> benchMap = null;
	private int maxRangeSize;
	private final Integer[] keys;

	Integer getKey(int key) {
		return keys[key];
	}

	private class MixWorkerThread extends WorkerThread {

		final int id;
		long numberOfRangeQueries = 0;
		long numberOfTraversedItemsRangeQueries = 0;
		long traversedNodes = 0;
		long nrOfSplits = 0;
		long nrOfJoins = 0;

		public MixWorkerThread(int id) {
			super();
			this.id = id;
		}

		public void setUp() {
		}

		public void run() {
			final int hundredPercentInt = 1000000;
			final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
			final RangeUpdateMap<Integer, Integer> localBenchMap = benchMap;
			final int localKeyRangeSize = keyRangeSize;
			final int localMaxRangeSize = maxRangeSize;
			final int halfRangeSize = maxRangeSize / 2;
			final long[] localAvoidOptOutSum = new long[34];
			long localMeasurment = 0;
			final Consumer<Integer> rangeConsumer = new Consumer<Integer>() {
				public void accept(Integer k) {
					localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + k.intValue();
					localAvoidOptOutSum[17]++;
				}
			};

			while (!shallStart()) {
				Thread.yield();
			}

			if (id % 2 == 0) {
			       //Update thread
				while (!stopped()) {
					// do operation here
					int opRand = randomGen.nextInt(hundredPercentInt);
					if (opRand < (0.5*hundredPercentInt)) {
						Integer elem = getKey(randomGen.nextInt(localKeyRangeSize));
						localBenchMap.put(elem, elem);
	                	//System.out.println(id + " put:" + elem);
					} else {
						Integer workingElement = getKey(randomGen.nextInt(localKeyRangeSize));
						localBenchMap.remove(workingElement);// (new
																// Elem(randomGen.nextInt(localKeyRangeSize)));
	                	//System.out.println(id + " rm:" + workingElement);
					}
					localMeasurment++;
				}
				measurment = localMeasurment;
				avoidOptOutSum = localAvoidOptOutSum[16];
	    		if (benchMap instanceof se.uu.collection.LockFreeImmTreapCATreeMapSTDR) {
	    			nrOfJoins =((se.uu.collection.LockFreeImmTreapCATreeMapSTDR) benchMap).getNrOfJoins();
	    			nrOfSplits =((se.uu.collection.LockFreeImmTreapCATreeMapSTDR) benchMap).getNrOfSplits();    		
	    		}
			} else {
				//Range query thread
				while (!stopped()) {
					// do operation here
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSize;
                	if(rangeStart < 0){
                		rangeStart = 0;
                	}
			if(rangeStart + localMaxRangeSize > localKeyRangeSize){
			    rangeStart = localKeyRangeSize - localMaxRangeSize;
			}
			if(rangeStart < 0){
			    rangeStart = 0;
			}
                	Integer rangeWorkingElementStart = getKey(rangeStart);
                	Integer rangeWorkingElementEnd = getKey(rangeStart + localMaxRangeSize);
                	//System.out.println(id + ":" + rangeWorkingElementStart + " " + rangeWorkingElementEnd);
                	localBenchMap.subSet(
                					rangeWorkingElementStart,
                					rangeWorkingElementEnd,
                					rangeConsumer);
                	localAvoidOptOutSum[18]++;
					localMeasurment++;
				}
	            //System.err.println("== Range Query Statistics== " + localAvoidOptOutSum[18] + " " + localAvoidOptOutSum[17] + " " +
				//((double) localAvoidOptOutSum[17]) /((double) localAvoidOptOutSum[18]));
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
	            measurment = localMeasurment;
				avoidOptOutSum = localAvoidOptOutSum[16];
			}
			
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
		System.gc();
		Thread.sleep(5000);
		threads = new MixWorkerThread[nrOfThreads];
		for (int i = 0; i < nrOfThreads; i++) {
			threads[i] = new MixWorkerThread(i);
			threads[i].setUp();
			threads[i].start();
		}
	}
	
	// public void setUp() throws Exception {
	// 	@SuppressWarnings("unchecked")
	// 	Class<RangeUpdateMap<Integer, Integer>> cls = (Class<RangeUpdateMap<Integer, Integer>>) Class.forName(setType);
	// 	if (benchMap == null) {
	// 		benchMap = cls.newInstance();
	// 		ThreadLocalRandom randomGen = ThreadLocalRandom.current();
	// 		for (int i = 0; i < prefillNrOfOps; i++) {// Put initial elements in
	// 													// map
	// 			Integer elem = getKey(randomGen.nextInt(keyRangeSize));
	// 			benchMap.put(elem, elem);
	// 		}
	// 	}
	// 	// Give GC and JIT some time
	// 	System.gc();
	// 	Thread.sleep(5000);
	// 	threads = new MixWorkerThread[nrOfThreads];
	// 	for (int i = 0; i < nrOfThreads; i++) {
	// 		threads[i] = new MixWorkerThread(i);
	// 		threads[i].setUp();
	// 		threads[i].start();
	// 	}
	// }

	public long tearDown() throws Exception {
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
        additionalMeasurmentString = numberOfRangeQueries + " " + numberOfTraversedItemsRangeQueries + " " + traversedNodes + " " + numberOfRouteNodes  + " " + nrOfJoins + " " + nrOfSplits;
		long results = super.tearDown();
		ThreadLocalRandom randomGen = ThreadLocalRandom.current();
		Integer e = benchMap.get(getKey((randomGen.nextInt(keyRangeSize))));
		globalAvoidOptOutSum = globalAvoidOptOutSum + (e == null ? 3 : e.intValue());
		if (benchMap instanceof contention.abstractions.MaintenanceAlg) {
			((contention.abstractions.MaintenanceAlg) benchMap).stopMaintenance();
		}
		benchMap = null;
		return results;
	}

	public IntSetRangeQueryAndUpdate(String setType, int nrOfThreads, int nrOfWarmUpRuns, double warmUpRunSeconds,
			int nrOfMeasurmentRuns, double measurmentRunSeconds, ResultReportType reportType, int keyRangeSize,
			int prefillNrOfOps, int maxRangeSize) {
		super(true, true, nrOfWarmUpRuns, warmUpRunSeconds, nrOfMeasurmentRuns, measurmentRunSeconds, reportType, nrOfThreads,
				setType);
		//kiwi.KiWi.MAX_THREADS = nrOfThreads;
        //LockFreeRangeCollectorSkipList.num_Threads = nrOfThreads;
		System.err.println("MEASURMENT RUN SECONDS " + measurmentRunSeconds);
		this.keyRangeSize = keyRangeSize;
		this.keys = new Integer[this.keyRangeSize + (maxRangeSize * 2)];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = i;
		}
		this.prefillNrOfOps = prefillNrOfOps;
		this.maxRangeSize = maxRangeSize;
	}

	public static void main(String[] args) {
		if (args.length != 10) {
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
			System.err.println("int maxRangeSize");
			System.err.println("");
			System.err.println("Example:");
			System.err.println("");
			System.err.println(
					"java -server -cp ../target/scala-2.11/test-classes/:../classes/:. se.uu.bench.SetRangeUpdateMixBench se.uu.collection.CATreeMap 4 5 3 3 3 ALL 1000000 100000 0.1 0.4 100");
			System.exit(0);
		}
		IntSetRangeQueryAndUpdate bench = new IntSetRangeQueryAndUpdate(args[0], // String
																					// setType
				new Integer(args[1]), // int nrOfThreads
				new Integer(args[2]), // int nrOfWarmUpRuns
				new Double(args[3]), // double warmUpRunSeconds
				new Integer(args[4]), // int nrOfMeasurmentRuns
				new Double(args[5]), // double measurmentRunSeconds
				Enum.valueOf(Benchmark.ResultReportType.class, args[6]), // ResultReportType
																			// reportType
				new Integer(args[7]), // int keyRangeSize
				new Integer(args[8]), // int prefillNrOfOps);
				new Integer(args[9]));// int maxRangeSizeUpdate
		try {
			bench.start();
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

}
