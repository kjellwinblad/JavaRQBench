package se.uu.bench;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.function.*;

import se.uu.collection.RangeUpdateMap;

public class SetRangeUpdateMixBench extends Benchmark{

    private final int keyRangeSize;
    private final int prefillNrOfOps;
    private final double percentageReads;
    private RangeUpdateMap<AbstractElem,AbstractElem> benchMap = null;
    private double percentageRangeQueries;
    private int maxRangeSize;
    private double percentageRangeUpdates;
    private int maxRangeSizeUpdate;

    private class MixWorkerThread extends WorkerThread{

        public void setUp(){}
        
        public void run(){
            final int hundredPercentInt = 1000000;
            final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            final int deleteLimit = (int)(hundredPercentInt * ((percentageReads + percentageRangeQueries + percentageRangeUpdates) + ((1.0 - percentageReads - percentageRangeQueries - percentageRangeUpdates) / 2.0)));
            final int rangeUpdateLimit = (int)(hundredPercentInt * (percentageReads + percentageRangeQueries + percentageRangeUpdates));
            final int rangeQueryLimit = (int)(hundredPercentInt * (percentageReads + percentageRangeQueries));
            final int localPercentageReads = (int)((hundredPercentInt) * percentageReads);
            final RangeUpdateMap<AbstractElem,AbstractElem> localBenchMap = benchMap;
            //We do not want any false sharing in this elements
            final Elem workingElement = new Elem(1);
            final Elem rangeWorkingElementStart = new Elem(1);
            final Elem rangeWorkingElementEnd = new Elem(1);
            final int localKeyRangeSize = keyRangeSize;
            final int localMaxRangeSize = maxRangeSize;
            final int halfRangeSize = maxRangeSize/2;
            final int localMaxRangeSizeUpdate = maxRangeSizeUpdate;
            final int halfRangeSizeUpdate = maxRangeSize/2;
            final long[] localAvoidOptOutSum = new long[32];
            long localMeasurment = 0;
            // System.err.println("localPercentageReads "+localPercentageReads);
            // System.err.println("rangeQueryLimit "+rangeQueryLimit);
            // System.err.println("localMaxRangeSize "+localMaxRangeSize);
            // System.err.println("rangeUpdateLimit "+rangeUpdateLimit);
            // System.err.println("localMaxRangeSizeUpdate "+localMaxRangeSizeUpdate);
            // System.err.println("deleteLimit "+deleteLimit);
            // System.err.println("hundredPercentInt "+hundredPercentInt);

	    final Consumer<AbstractElem> rangeConsumer = new Consumer<AbstractElem>(){
		    public void accept(AbstractElem k){
			localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + k.getKey();
		    }
		};

	    while(!shallStart()){
                Thread.yield();
            }
            while( ! stopped() ){
                //do operation here
                int opRand = randomGen.nextInt(hundredPercentInt);
                if(opRand < localPercentageReads){
                    workingElement.setKey(randomGen.nextInt(localKeyRangeSize));
                    AbstractElem elem = localBenchMap.get(workingElement);//localBenchMap.get(new Elem(randomGen.nextInt(localKeyRangeSize)));
                    localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + (elem == null ? 3 : elem.getKey());
                }else if (opRand < rangeQueryLimit){
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSize;
                	int sizeOfRange = randomGen.nextInt(localMaxRangeSize);
                	rangeWorkingElementStart.setKey(rangeStart);
                	rangeWorkingElementEnd.setKey(rangeStart + sizeOfRange);
                	localBenchMap.subSet(
                					rangeWorkingElementStart,
                					rangeWorkingElementEnd,
                					rangeConsumer);//(k) -> localAvoidOptOutSum[16] = localAvoidOptOutSum[16] + k.getKey()//localBenchMap.subSet(new Elem(rangeStart), new Elem(rangeStart + sizeOfRange));//localBenchMap.subSet(rangeWorkingElementStart, rangeWorkingElementEnd);
//                	for(int i = 0; i < subset.length; i++){
//                		localAvoidOptOutSum = localAvoidOptOutSum + ((AbstractElem)subset[i]).getKey();
//                	}
                }else if (opRand < rangeUpdateLimit){
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSizeUpdate;
                	int sizeOfRange = randomGen.nextInt(localMaxRangeSizeUpdate);
                	rangeWorkingElementStart.setKey(rangeStart);
                	rangeWorkingElementEnd.setKey(rangeStart + sizeOfRange);
                	localBenchMap.rangeUpdate(rangeWorkingElementStart,rangeWorkingElementEnd, (key, value) -> new Elem(value.getKey() + 1));//new Elem(rangeStart), new Elem(rangeStart + sizeOfRange), (key, value) -> new Elem(value.getKey() + 1));
                }else if (opRand < deleteLimit){
                    AbstractElem elem = new Elem(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.putIfAbsent(elem, elem);
                }else{
                    workingElement.setKey(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.remove(workingElement);//(new Elem(randomGen.nextInt(localKeyRangeSize)));
                }
                localMeasurment++;
            }
            measurment = localMeasurment;
            avoidOptOutSum = localAvoidOptOutSum[16];
        }
    }


    public void setUp() throws Exception{
        @SuppressWarnings("unchecked")
        Class<RangeUpdateMap<AbstractElem,AbstractElem>> cls = (Class<RangeUpdateMap<AbstractElem,AbstractElem>>)Class.forName(setType);
        if(benchMap == null){
            benchMap = cls.newInstance();
            ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            for(int i = 0; i < prefillNrOfOps; i++){//Put initial elements in map
                Elem elem = new Elem(randomGen.nextInt(keyRangeSize));
                benchMap.putIfAbsent(elem, elem);
            }
        }
        //Give GC and JIT some time
        System.gc();
        Thread.sleep(5000);
        threads = new MixWorkerThread[nrOfThreads];
        for(int i = 0; i < nrOfThreads; i++){
            threads[i] = new MixWorkerThread();
            threads[i].setUp();
            threads[i].start();
        }
    }

    public long tearDown() throws Exception{
        long results = super.tearDown();
        ThreadLocalRandom randomGen = ThreadLocalRandom.current();
        AbstractElem e = benchMap.get(new Elem(randomGen.nextInt(keyRangeSize)));
        globalAvoidOptOutSum = globalAvoidOptOutSum + (e == null ? 3 : e.getKey());
        if(benchMap instanceof contention.abstractions.MaintenanceAlg){
            ((contention.abstractions.MaintenanceAlg)benchMap).stopMaintenance();
        }
	    if(benchMap instanceof se.uu.collection.CATreeMapSTDR){
		System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.CATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
	    if(benchMap instanceof se.uu.collection.FatCATreeMapSTDR){
		System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.FatCATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
	    if(benchMap instanceof se.uu.collection.ImmTreapCATreeMapSTDR){
		System.err.println("NUMBER OF ROUTE NODES = "+((se.uu.collection.ImmTreapCATreeMapSTDR)benchMap).numberOfRouteNodes());
	    }
        benchMap = null;
        return results;
    }

    public SetRangeUpdateMixBench(String setType,
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
                                  int maxRangeSizeUpdate){
        super(true,
              nrOfWarmUpRuns,
              warmUpRunSeconds,
              nrOfMeasurmentRuns,
              measurmentRunSeconds,
              reportType,
              nrOfThreads,
              setType);
        System.err.println("MEASURMENT RUN SECONDS " + measurmentRunSeconds);
        this.keyRangeSize = keyRangeSize;
        this.prefillNrOfOps = prefillNrOfOps;
        this.percentageReads = percentageReads;
        this.percentageRangeQueries = percentageRangeQueries;
        this.maxRangeSize = maxRangeSize;
        this.percentageRangeUpdates = percentageRangeUpdates;
        this.maxRangeSizeUpdate = maxRangeSizeUpdate;
    }

    public static void main(String [] args){
        if(args.length !=14){
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
        SetRangeUpdateMixBench bench = new SetRangeUpdateMixBench(args[0],    // String setType
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
                                                                  new Integer(args[13]));// int maxRangeSizeUpdate
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
