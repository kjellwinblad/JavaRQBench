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

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;

public class SetLocalMixBench extends Benchmark{

    private int keyRangeSize;

    private int prefillNrOfOps;

    private double percentageReads;

    private int nextKeyRangeSize;

    private Map<AbstractElem,AbstractElem> benchMap = null;

    private class MixWorkerThread extends WorkerThread{

        public void setUp(){}
        public void run(){
            final int hundredPercentInt = 1000000;
            final ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            final int deleteLimit = (int)(hundredPercentInt*(percentageReads + ((1.0 - percentageReads) / 2.0)));
            final int localPercentageReads = (int)(hundredPercentInt * percentageReads);
            final Map<AbstractElem,AbstractElem> localBenchMap = benchMap;
            //We do not want any false sharing in this elements
            final WorkingElem workingElement = new WorkingElem();
            final int localKeyRangeSize = keyRangeSize;
            final int localNextKeyRangeSize = nextKeyRangeSize;
            long localAvoidOptOutSum = 0;
            long localMeasurment = 0;
            final int halfNextKeyRangeSize = nextKeyRangeSize / 2;
            int key = randomGen.nextInt(localKeyRangeSize);
            int iterationNr = 0;
            while(!shallStart()){
                Thread.yield();
            }
            while( ! stopped() ){
                if(iterationNr == 200){
                    key = randomGen.nextInt(localKeyRangeSize);
                    iterationNr = 0;
                }else{
                    key = key + randomGen.nextInt(localNextKeyRangeSize) - halfNextKeyRangeSize;
                    if(key < 0){
                        key = 0 - key;
                    }
                    if(key >= localKeyRangeSize){
                        key = localKeyRangeSize -((key + 1) - localKeyRangeSize);
                    }
                }
                //do operation here
                int opRand = randomGen.nextInt(hundredPercentInt);
                if(opRand < localPercentageReads){
                    workingElement.setKey(key);
                    AbstractElem elem = localBenchMap.get(workingElement);
                    localAvoidOptOutSum = localAvoidOptOutSum + (elem == null ? 3 : elem.getKey());
                }else if (opRand < deleteLimit){
                    AbstractElem elem = new Elem(key);
                    localBenchMap.putIfAbsent(elem, elem);
                }else{
                    workingElement.setKey(key);
                    localBenchMap.remove(workingElement);
                }
                localMeasurment++;
                iterationNr++;
            }
            measurment = localMeasurment;
            avoidOptOutSum = localAvoidOptOutSum;
        }
    }

    public void setUp() throws Exception{
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
                Elem elem = new Elem(randomGen.nextInt(keyRangeSize));
                benchMap.putIfAbsent(elem, elem);
            }
        } else if(benchMap instanceof contention.datastructures.lockBased.LockBasedFriendlyTreeMap){
            ((contention.datastructures.lockBased.LockBasedFriendlyTreeMap)benchMap).startMaintenance();
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

    public SetLocalMixBench(String setType,
                            int nrOfThreads,
                            int nrOfWarmUpRuns,
                            double warmUpRunSeconds,
                            int nrOfMeasurmentRuns,
                            double measurmentRunSeconds,
                            ResultReportType reportType,
                            int keyRangeSize,
                            int prefillNrOfOps,
                            double percentageReads,
                            int nextKeyRangeSize){
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
        this.nextKeyRangeSize = nextKeyRangeSize + 1;
    }

    public static void main(String [] args){
        if(args.length !=11){
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
            System.err.println("int nextKeyRangeSize");
            System.err.println("");
            System.err.println("Example:");
            System.err.println("");
            System.err.println("java -server -cp ../classes/:. se.uu.bench.SetLocalMixBench se.uu.collection.CATreeMap 4 5 3 ALL 1000000 100000");
            System.exit(0);
        }
        SetLocalMixBench bench = new SetLocalMixBench(args[0],// String setType
                                                      new Integer(args[1]),// int nrOfThreads
                                                      new Integer(args[2]),//int nrOfWarmUpRuns
                                                      new Double(args[3]),//double warmUpRunSeconds
                                                      new Integer(args[4]),//int nrOfMeasurmentRuns
                                                      new Double(args[5]),//double measurmentRunSeconds
                                                      Enum.valueOf(Benchmark.ResultReportType.class, args[6]),// ResultReportType reportType
                                                      new Integer(args[7]),//int keyRangeSize
                                                      new Integer(args[8]),//int prefillNrOfOps);
                                                      new Double(args[9]),
                                                      new Integer(args[10]));//double percentageReads
                                           
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
