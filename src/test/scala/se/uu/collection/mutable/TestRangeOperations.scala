package se.uu.collection.mutable


import scala.actors.Futures._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.util.Random
import org.scalatest.FunSpec
import scala.collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

import java.util.function.BiFunction
import se.uu.collection.CATreeMap
import se.uu.collection.CATreeMapOpt
import se.uu.collection.CATreeMapSafeOpt
import se.uu.collection.CATreeMapSTD
import se.uu.collection.CATreeMapSTDR
import se.uu.collection.ImmTreapCATreeMapSTDR
import se.uu.collection.LockFreeImmTreapCATreeMapSTDR
import se.uu.collection.FatCATreeMapSTDR
import se.uu.collection.CATreeMapLFCAS
import se.uu.collection.RangeUpdateMap
import java.util.function.Consumer

class TestRangeOperations extends FunSpec {

  // describe("A CATreeSTDR map") {
  //   def mapCreator:RangeUpdateMap[Int,Int] = (new CATreeMapSTDR[Int,Int]())
  //   testMap(mapCreator _, true)
  //   testMap(mapCreator _, false)
  // }
  // describe("A FatCATreeMapSTDR map") {
  //   def mapCreator:RangeUpdateMap[Int,Int] =  new FatCATreeMapSTDR[Int,Int]()
  //   //testMap(mapCreator _, true)
  //   testMap(mapCreator _, false)
  // }
  describe("A ImmTreapCATreeMapSTDR map") {
    def mapCreator:RangeUpdateMap[Int,Int] =  new ImmTreapCATreeMapSTDR[Int,Int]()
    //testMap(mapCreator _, true)
    testMap(mapCreator _, false)
  }

  describe("A LockFreeImmTreapCATreeMapSTDR map") {
    def mapCreator:RangeUpdateMap[Int,Int] =  new LockFreeImmTreapCATreeMapSTDR[Int,Int]()
    //testMap(mapCreator _, true)
    testMap(mapCreator _, false)
  }

  private def testMap(mapCreator: (() => RangeUpdateMap[Int, Int]), rangeUpdate:Boolean){


    describe("When doing random insert, delete and range " + (if (rangeUpdate) "update" else "query") + " in parallel it should not crash") {
      val randomGenerator = new Random()
      var a = 0
      val maxSize = 1000000
      val maxRangeSize = 200
      for(size <- List(/**/0.001,0.01, 0.1, 1.0/**/)){

        val hashMap = HashMap[Int, Int]()
        while(hashMap.size != (maxSize*size).toInt/2){
          val value = ((maxSize*size).toInt * randomGenerator.nextDouble()).toInt
          hashMap.+=((value, value))
        }
        val lmap = mapCreator()
        hashMap.foreach ( (t2) => lmap.put(t2._1, t2._2))
        println("SIZE OF lmap  " + lmap.size())
        def doWork() {
          var allRangeSizes = 0
          var lastKey = -1
          var rangeStart = 0
          var sizeOfRange = 0
          var ranges = 0
          class AppFunction extends BiFunction[Int,Int,Int] with Consumer[Int]{
            def accept(key:Int){
              allRangeSizes = allRangeSizes +1
              //print(key + " ")
              // if(lastKey >= key){
              //   println("LAST " + lastKey + " KEY " + key);
              //   println("LAST " + lastKey + " KEY " + key);
              //   System.exit(0);
              // }
              assert(lastKey < key);
	      assert(key <= (rangeStart + sizeOfRange));
	      assert(key >= rangeStart);
	      lastKey = key;
            }
            def apply(key:Int, value:Int):Int = {
              accept(key)
	      return value +1;
            }
          }
          val fun = new AppFunction();

          for (i <- 1 to (1000000).toInt) {
            val value = ((maxSize*size).toInt * randomGenerator.nextDouble()).toInt
            val randomNum = randomGenerator.nextDouble()
            if (randomNum > 0.66666) {
              lmap.put(value, value)
            } else if(randomNum > 0.33333){
              lmap.remove(value)
            }else {
              lastKey = -1
              rangeStart = ((maxSize*size-maxRangeSize).toInt * randomGenerator.nextDouble()).toInt
              sizeOfRange = (maxRangeSize.toDouble * randomGenerator.nextDouble()).toInt
              ranges = ranges + 1
              if(rangeUpdate){
                lmap.rangeUpdate(rangeStart, rangeStart + sizeOfRange, fun)
              }else{
                lmap.subSet(rangeStart, rangeStart + sizeOfRange, fun)
                //println()
              }
            }
          }
          val averageRangeSize = (allRangeSizes.toDouble/ranges)
          assert((maxRangeSize /4 -3) < averageRangeSize && (maxRangeSize /4 +3) > averageRangeSize)
          println("AVERAGE RANGE SIZE " +averageRangeSize + " " + ranges );
        }
        
        val work = Array.fill(8){
          future(doWork())
          //doWork()
        }

        awaitAll(10000000, work: _*)

        println("lmap size after test "+ lmap.size());


        val lmap2 = mapCreator()

        (0 to ((maxSize*size).toInt)).foreach ( (t2) => lmap2.put(t2, t2))

        println("SIZE OF lmap2  " + lmap2.size())

        def doWork2() {
          var allRangeSizes = 0
          var lastKey = -1
          var rangeStart = 0
          var sizeOfRange = 0
          var ranges = 0
          var thisRangeSize = 0
          class AppFunction extends BiFunction[Int,Int,Int] with Consumer[Int]{
            def accept(key:Int){
              allRangeSizes = allRangeSizes +1
              thisRangeSize = thisRangeSize +1
              //print(key + " ")
              // if(lastKey >= key){
              //   println("LAST " + lastKey + " KEY " + key);
              //   println("LAST " + lastKey + " KEY " + key);
              //   System.exit(0);
              // }
              assert(lastKey < key);
	      assert(key <= (rangeStart + sizeOfRange));
	      assert(key >= rangeStart);
	      lastKey = key;
            }
            def apply(key:Int, value:Int):Int = {
              accept(key)
	      return value +1;
            }
          }
          val fun = new AppFunction();

          for (i <- 1 to (1000000).toInt) {
            val value = ((maxSize*size).toInt * randomGenerator.nextDouble()).toInt
            val randomNum = randomGenerator.nextDouble()
            if (randomNum > 0.666666666) {
              lmap2.put(value, value)


            } else if (randomNum > 0.33333333) {
              //lmap2.put(value, value)
              lastKey = -1
              rangeStart = ((maxSize*size-maxRangeSize).toInt * randomGenerator.nextDouble()).toInt
              sizeOfRange = 10//(maxRangeSize.toDouble * randomGenerator.nextDouble()).toInt
              ranges = ranges + 1
              thisRangeSize = 0
              if(rangeUpdate){
                lmap2.rangeUpdate(rangeStart, rangeStart + sizeOfRange, fun)
              }else{
                lmap2.subSet(rangeStart, rangeStart + sizeOfRange, fun)
                assert(thisRangeSize === 11)
                //println(thisRangeSize)
              }


            } else {
              lastKey = -1
              rangeStart = ((maxSize*size-maxRangeSize).toInt * randomGenerator.nextDouble()).toInt
              sizeOfRange = 50//(maxRangeSize.toDouble * randomGenerator.nextDouble()).toInt
              ranges = ranges + 1
              thisRangeSize = 0
              if(rangeUpdate){
                lmap2.rangeUpdate(rangeStart, rangeStart + sizeOfRange, fun)
              }else{
                lmap2.subSet(rangeStart, rangeStart + sizeOfRange, fun)
                //println()
                //println(thisRangeSize)
                if(thisRangeSize != 51){
                  println("GOT RANGE SIZE " + thisRangeSize + " expected 51")
                  System.exit(0);
                }
                assert(thisRangeSize === 51, " start: " + rangeStart + " end " + (rangeStart + sizeOfRange))
              }
            }
          }
          val averageRangeSize = (allRangeSizes.toDouble/ranges)
          //assert((maxRangeSize /4 -3) < averageRangeSize && (maxRangeSize /4 +3) > averageRangeSize)
          println("AVERAGE RANGE SIZE (50) " +averageRangeSize + " " + ranges );
        }

        val work2 = Array.fill(8){
          future(doWork2())
          //doWork()
        }

        awaitAll(10000000, work2: _*)

        println("lmap size after test 2 "+ lmap.size());


      }
    }
  }
}
