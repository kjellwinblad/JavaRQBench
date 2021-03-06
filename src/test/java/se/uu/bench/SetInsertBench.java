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


public class SetInsertBench extends Benchmark{

    private int keyRangeSize;

    private int nrOfOperations;

    private Map<Elem,Elem> benchMap = null;

    private class InsertWorkerThread extends WorkerThread{

        public void setUp(){}
        public void run(){
            ThreadLocalRandom randomGen = ThreadLocalRandom.current();
            while(!shallStart()){
                Thread.yield();
            }
            int thisThreadNumberOfOps = nrOfOperations / nrOfThreads; 
            for(int i = 0; i < thisThreadNumberOfOps; i++){
                //do operation here
                Elem elem = new Elem(randomGen.nextInt(keyRangeSize));
                benchMap.put(elem, elem);
                measurment++;
                doLocalWork(256);
            }
            try{
                waitBarrier.await();
            }catch(Exception e){
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }
    }


    public void setUp() throws Exception{
        @SuppressWarnings("unchecked")
        Class<Map<Elem,Elem>> cls = (Class<Map<Elem,Elem>>)Class.forName(setType);
        if(setType.equals("trees.logicalordering.LogicalOrderingAVL")){
            benchMap = new trees.logicalordering.LogicalOrderingAVL<Elem,Elem>(new Elem(Integer.MIN_VALUE/2),
                                                                               new Elem(Integer.MAX_VALUE/2));
        }else{
            benchMap = cls.newInstance();
        }
        threads = new InsertWorkerThread[nrOfThreads];
        for(int i = 0; i < nrOfThreads; i++){
            threads[i] = new InsertWorkerThread();
            threads[i].setUp();
            threads[i].start();
        }
    }

    public long tearDown() throws Exception{
        long results = super.tearDown();
        ThreadLocalRandom randomGen = ThreadLocalRandom.current();
        Elem e = benchMap.get(new Elem(randomGen.nextInt(keyRangeSize)));
        globalAvoidOptOutSum = (e == null ? 3 : e.getKey());
        benchMap = null;
        return results;
    }

    public SetInsertBench(String setType,
                          int nrOfThreads,
                          int nrOfWarmUpRuns,
                          int nrOfMeasurmentRuns,
                          ResultReportType reportType,
                          int keyRangeSize,
                          int nrOfOperations){
        super(false,
              nrOfWarmUpRuns,
              0,
              nrOfMeasurmentRuns,
              0,
              reportType,
              nrOfThreads,
              setType);
        this.keyRangeSize = keyRangeSize;
        this.nrOfOperations = nrOfOperations;
    }

    public static void main(String [] args){
        if(args.length !=7){
            System.err.println("Parameters:");
            System.err.println("");
            System.err.println("String setType,");
            System.err.println("int nrOfThreads,");
            System.err.println("int nrOfWarmUpRuns,");
            System.err.println("int nrOfMeasurmentRuns,");
            System.err.println("ResultReportType reportType, (ALL)");
            System.err.println("int keyRangeSize");
            System.err.println("int nrOfOperations");
            System.err.println("");
            System.err.println("Example:");
            System.err.println("");
            System.err.println("java -cp ../classes/:. se.uu.bench.SetInsertBench se.uu.collection.CATreeMap 4 5 3 ALL 1000000 100000");
            System.exit(0);
        }
        SetInsertBench bench = new SetInsertBench(args[0],// String setType
                                                  new Integer(args[1]),// int nrOfThreads
                                                  new Integer(args[2]),//int nrOfWarmUpRuns
                                                  new Integer(args[3]),//int nrOfMeasurmentRuns
                                                  Enum.valueOf(Benchmark.ResultReportType.class, args[4]),// ResultReportType reportType
                                                  new Integer(args[5]),
                                                  new Integer(args[6]));//int keyRangeSize
        
        try{
            bench.start();
        }catch(Exception e){
            System.err.println(e.getMessage());
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
    
}
