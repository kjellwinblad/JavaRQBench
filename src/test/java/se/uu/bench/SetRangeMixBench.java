package se.uu.bench;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;

import se.uu.collection.RangeQueryMap;

public class SetRangeMixBench extends Benchmark{

    private final int keyRangeSize;
    private final int prefillNrOfOps;
    private final double percentageReads;
    private RangeQueryMap<AbstractElem,AbstractElem> benchMap = null;
	private double percentageRangeQueries;
	private int maxRangeSize;

    private class MixWorkerThread extends WorkerThread{

        public void setUp(){}
        public void run(){
            final int hundredPercentInt = 1000000;
            final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            final int deleteLimit = (int)(hundredPercentInt * ((percentageReads + percentageRangeQueries) + ((1.0 - percentageReads - percentageRangeQueries) / 2.0)));
            final int rangeQueryLimit = (int)(hundredPercentInt * (percentageReads + percentageRangeQueries));
            final int localPercentageReads = (int)((hundredPercentInt) * percentageReads);
            final RangeQueryMap<AbstractElem,AbstractElem> localBenchMap = benchMap;
            //We do not want any false sharing in this elements
            final WorkingElem workingElement = new WorkingElem();
            final WorkingElem rangeWorkingElementStart = new WorkingElem();
            final WorkingElem rangeWorkingElementEnd = new WorkingElem();
            final int localKeyRangeSize = keyRangeSize;
            final int localMaxRangeSize = maxRangeSize;
            final int halfRangeSize = maxRangeSize/2;
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
                }else if (opRand < rangeQueryLimit){
                	int rangeStart = randomGen.nextInt(localKeyRangeSize) - halfRangeSize;
                	int sizeOfRange = randomGen.nextInt(localMaxRangeSize);
                	rangeWorkingElementStart.setKey(rangeStart);
                	rangeWorkingElementEnd.setKey(rangeStart + sizeOfRange);
                	Object[] subset= localBenchMap.subSet(rangeWorkingElementStart, rangeWorkingElementEnd);
                	for(int i = 0; i < subset.length; i++){
                		localAvoidOptOutSum = localAvoidOptOutSum + ((AbstractElem)subset[i]).getKey();
                	}
                }else if (opRand < deleteLimit){
                    AbstractElem elem = new Elem(randomGen.nextInt(localKeyRangeSize));
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
        @SuppressWarnings("unchecked")
        Class<RangeQueryMap<AbstractElem,AbstractElem>> cls = (Class<RangeQueryMap<AbstractElem,AbstractElem>>)Class.forName(setType);
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
        benchMap = null;
        return results;
    }

    public SetRangeMixBench(String setType,
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
                       int maxRangeSize){
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
    }

    public static void main(String [] args){
        if(args.length !=12){
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
            System.err.println("");
            System.err.println("Example:");
            System.err.println("");
            System.err.println("java -server -cp ../target/scala-2.11/test-classes/:../classes/:. se.uu.bench.SetRangeMixBench se.uu.collection.CATreeMap 4 5 3 3 3 ALL 1000000 100000 0.1 0.4 100");
            System.exit(0);
        }
        SetRangeMixBench bench = new SetRangeMixBench(args[0],    // String setType
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
                                            new Integer(args[11]));// int maxRangeSize
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
