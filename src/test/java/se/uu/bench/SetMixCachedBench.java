package se.uu.bench;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

public class SetMixCachedBench extends Benchmark{

    private final int keyRangeSize;
    private final int prefillNrOfOps;
    private final double percentageReads;
    private Map<AbstractElem,AbstractElem> benchMap = null;

    private Elem[] objectCache = null;

    private Elem getElemFromCache(int key){
        return objectCache[64 + key];
    }

    private class MixWorkerThread extends WorkerThread{

        public void setUp(){}
        public void run(){
            final int hundredPercentInt = 1000000;
            final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            final int deleteLimit = (int)(hundredPercentInt * (percentageReads + ((1.0 - percentageReads) / 2.0)));
            final int localPercentageReads = (int)((hundredPercentInt) * percentageReads);
            final Map<AbstractElem,AbstractElem> localBenchMap = benchMap;
            //We do not want any false sharing in this elements
            final WorkingElem workingElement = new WorkingElem();
            final int localKeyRangeSize = keyRangeSize;
            long localAvoidOptOutSum = 0;
            long localMeasurment = 0;

            while(!shallStart()){
                Thread.yield();
            }
            while( ! stopped() ){
                //do operation here
                int opRand = randomGen.nextInt(hundredPercentInt);
                if(opRand < localPercentageReads){
                    workingElement.setKey(randomGen.nextInt(localKeyRangeSize));
                    AbstractElem elem = localBenchMap.get(workingElement);
                    localAvoidOptOutSum = localAvoidOptOutSum + (elem == null ? 3 : elem.getKey());
                }else if (opRand < deleteLimit){
                    AbstractElem elem = getElemFromCache(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.putIfAbsent(elem, elem);
                }else{
                    workingElement.setKey(randomGen.nextInt(localKeyRangeSize));
                    localBenchMap.remove(workingElement);
                }
                localMeasurment++;
            }
            measurment = localMeasurment;
            avoidOptOutSum = localAvoidOptOutSum;
        }
    }


    public void setUp() throws Exception{
        if(objectCache == null){
            objectCache = new Elem[keyRangeSize+128];
            for(int i = 0; i < keyRangeSize; i++){
                objectCache[i+64] = new Elem(i);
            }
        }
        @SuppressWarnings("unchecked")
        Class<Map<AbstractElem,AbstractElem>> cls = (Class<Map<AbstractElem,AbstractElem>>)Class.forName(setType);
        if(benchMap == null){
            if(setType.equals("trees.logicalordering.LogicalOrderingAVL")){
                benchMap = new trees.logicalordering.LogicalOrderingAVL<AbstractElem,AbstractElem>(new Elem(Integer.MIN_VALUE/2),
                                                                                                   new Elem(Integer.MAX_VALUE/2));
            }else{
                benchMap = cls.newInstance();
            }
            ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            for(int i = 0; i < prefillNrOfOps; i++){//Put initial elements in map
                Elem elem = getElemFromCache(randomGen.nextInt(keyRangeSize));
                benchMap.putIfAbsent(elem, elem);
            }
        }else if(benchMap instanceof contention.datastructures.lockBased.LockBasedFriendlyTreeMap){
            ((contention.datastructures.lockBased.LockBasedFriendlyTreeMap)benchMap).startMaintenance();;
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
        benchMap = null;
        return results;
    }

    public SetMixCachedBench(String setType,
                             int nrOfThreads,
                             int nrOfWarmUpRuns,
                             double warmUpRunSeconds,
                             int nrOfMeasurmentRuns,
                             double measurmentRunSeconds,
                             ResultReportType reportType,
                             int keyRangeSize,
                             int prefillNrOfOps,
                             double percentageReads){
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
    }

    public static void main(String [] args){
        if(args.length !=10){
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
            System.err.println("");
            System.err.println("Example:");
            System.err.println("");
            System.err.println("java -server -cp ../classes/:. se.uu.bench.SetMixBench se.uu.collection.CATreeMap 4 5 3 ALL 1000000 100000");
            System.exit(0);
        }
        SetMixCachedBench bench = new SetMixCachedBench(args[0],// String setType
                                                         new Integer(args[1]),// int nrOfThreads
                                                         new Integer(args[2]),//int nrOfWarmUpRuns
                                                         new Double(args[3]),//double warmUpRunSeconds
                                                         new Integer(args[4]),//int nrOfMeasurmentRuns
                                                         new Double(args[5]),//double measurmentRunSeconds
                                                         Enum.valueOf(Benchmark.ResultReportType.class, args[6]),// ResultReportType reportType
                                                         new Integer(args[7]),//int keyRangeSize
                                                         new Integer(args[8]),//int prefillNrOfOps);
                                                         new Double(args[9]));//double percentageReads
        
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
