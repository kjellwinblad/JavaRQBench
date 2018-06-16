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

import se.uu.collection.CATreeMap
import se.uu.collection.CATreeMapOpt
import se.uu.collection.CATreeMapSafeOpt
import se.uu.collection.CATreeMapSTD
import se.uu.collection.CATreeMapSTDR
import se.uu.collection.FatCATreeMapSTDR
import se.uu.collection.CATreeMapLFCAS

class ConcurrentLinearHashMapSpec extends FunSpec {

  // describe("A LEXLinearHashMap node") {
  //   def mapCreator:Map[Int,Int] = new LEXLinearHashMap
  //   testMap(mapCreator _)
  // }

  // describe("A StatisticsMap node") {
  //   def mapCreator:Map[Int,Int] = new StatisticsMap
  //   testMap(mapCreator _)
  // }

  // describe("A ATreeMap node") {
  //   def mapCreator:Map[Int,Int] = new ATreeMap
  //   testMap(mapCreator _)
  // }

  // describe("A CATree map") {
  //   def mapCreator:Map[Int,Int] = (new CATreeMap[Int,Int]()).asScala
  //   testMap(mapCreator _)
  // }

  // describe("A CATreeSTDR map") {
  //   def mapCreator:Map[Int,Int] = (new CATreeMapSTDR[Int,Int]()).asScala
  //   testMap(mapCreator _)
  // }
  // var actualMap:FatCATreeMapSTDR[Int,Int] = null
  // describe("A FatCATreeMapSTDR map") {
  //   def mapCreator:Map[Int,Int] = {
  //     actualMap = new FatCATreeMapSTDR[Int,Int]()
  //       (actualMap).asScala
  //   }
  //   testMap(mapCreator _)
  // }

  // var actualMap:CATreeMapLFCAS[Int,Int] = null
  // describe("A CATreeMapLFCAS map") {
  //   def mapCreator:Map[Int,Int] = {
  //     actualMap = new CATreeMapLFCAS[Int,Int]()
  //       (actualMap).asScala
  //   }
  //   testMap(mapCreator _)
  // }

  // var actualMap:se.uu.collection.ConcurrentLinearHashMap[Int,Int] = null
  // describe("A ConcurrentLinearHashMap map") {
  //   def mapCreator:Map[Int,Int] = {
  //     actualMap = new se.uu.collection.ConcurrentLinearHashMap[Int,Int]()
  //       (actualMap).asScala
  //   }
  //   testMap(mapCreator _)
  // }

  // var actualMap:se.uu.collection.ConcDCountLinearHashMap[Int,Int] = null
  // describe("A ConcurrentLinearHashMap map") {
  //   def mapCreator:Map[Int,Int] = {
  //     actualMap = new se.uu.collection.ConcDCountLinearHashMap[Int,Int]()
  //       (actualMap).asScala
  //   }
  //   testMap(mapCreator _)
  // }


  var actualMap:se.uu.collection.ImmTreapCATreeMapSTDR[Int,Int] = null
  describe("A ImmTreapCATreeMapSTDR map") {
    def mapCreator:Map[Int,Int] = {
      actualMap = new se.uu.collection.ImmTreapCATreeMapSTDR[Int,Int]()
        (actualMap).asScala
    }
    testMap(mapCreator _)
  }

  //   var actualMap:se.uu.collection.LockFreeImmTreapCATreeMapSTDR[Int,Int] = null
  //   describe("A LockFreeImmTreapCATreeMapSTDR map") {
  //   def mapCreator:Map[Int,Int] = {
  //     actualMap = new se.uu.collection.LockFreeImmTreapCATreeMapSTDR[Int,Int]()
  //       (actualMap).asScala
  //   }
  //   testMap(mapCreator _)
  // }

  //describe("A SLinearHashMap node") {
  //  def mapCreator:Map[Int,Int] = SLinearHashMap[Int,Int]()
  //  testMap(mapCreator _)
  //}


  private def testMap(mapCreator: (() => Map[Int, Int])){

    for(n <- (1 to 19000 by 100)){
      describe("when " + n + " elements are inserted in parallel") {
        val map = mapCreator()
        val refMap = new TrieMap[Int,Int]()
        (1 to n).par.foreach((i) => {
          refMap.+=((i, i))
          map.+=((i, i))
        })

        it("should result in the same size as when performing the actions on a TrieMap") {
          assert(refMap.size === map.size)
        }

        it("should result in the same thing as when performing the actions on a TrieMap") {
         assert(refMap.iterator.toSet === map.iterator.toSet)
        }

        it("should be possible to retrive an inserted element") {
          for (i <- 1 to n) {
            assert(map.get(i) === Some(i), "retrive after insert")
          }
        }
      }
    }

    for(n <- (1 to 10001 by 1000)){

      describe("After " + n + " elements have been inserted") {
        val map = mapCreator()

        (1 to n).foreach((i) => {
          map.+=((i, i))
        })

        it("should be possible to lookup the elements in parallel") {
          (1 to n).par.foreach((i) => {
            assert(map.get(i) === Some(i))
          })
        }
        

      }

    }

    for(n <- (1 to 10001 by 1000)){

      describe("When a map containing " + n + " elememnts has been created and deleted in parallel") {
        val map = mapCreator()

        (1 to n).foreach((i) => {
          map.+=((i, i))
        })

        (1 to n).par.foreach((i) => {
          map.-=(i)
        })

        it("should be empty") {


          (1 to n).foreach((i) => {
            assert(map.get(i) === None)
          })

          assert(map.size === 0)

        }
        
      }

    }


    describe("When doing random insert and delete in parallel it should not crach") {
      val randomGenerator = new Random()
      var a = 0
      
      for(size <- List(/**/0.0001, 0.001, 0.01, 0.1, 1.0/**/)){
        
        val lmap = mapCreator()

        def doWork() {
          for (i <- 1 to (1000000*size).toInt) {
            val value = ((4000*size).toInt * randomGenerator.nextDouble()).toInt
            if (randomGenerator.nextDouble() > 0.3) {
              lmap.+=((value, value))
            } else {
              lmap.-=(value)
            }
          }
        }
        
        val work = Array.fill(6){
          future(doWork())
          //doWork()
        }

        awaitAll(10000000, work: _*)        
      }
    }
  }
}
