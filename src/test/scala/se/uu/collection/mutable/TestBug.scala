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

class TestBug extends FunSpec {

//   for(k <- 1 to 100){
//     val map = new CATreeMap[Int,Int]()
//     val refMap = new TrieMap[Int,Int]()
//     for(n <- (4000 to 19000 by 100)){
//       (1 to n).par.foreach((i) => {
// //        refMap.+=((i, i))
//         map.+=((i, i))
//       })
//     }
//   }
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

  describe("A CATree map") {
//    def mapCreator:Map[Int,Int] = (new CATreeMap[Int,Int]()).asScala
//    testMap(mapCreator _)
  }

  //describe("A SLinearHashMap node") {
  //  def mapCreator:Map[Int,Int] = SLinearHashMap[Int,Int]()
  //  testMap(mapCreator _)
  //}


  private def testMap(mapCreator: (() => Map[Int, Int])){

    for(n <- (4000 to 19000 by 100)){
      println("DOING " + n)
      describe("when " + n + " elements are inserted in parallel") {
        val map = mapCreator()
        val refMap = new TrieMap[Int,Int]()

        (1 to n).par.foreach((i) => {
          refMap.+=((i, i))
          map.+=((i, i))
        })

        // println("BEFORE PRINT========================")
        // println(map.asInstanceOf[ATreeMap[Int,Int]].printRootStructure())
        // println("WHOLE TREE")
        // println(map.asInstanceOf[ATreeMap[Int,Int]].toStringWhole())
        // println("END PRINT========================")

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
//println("INSERT A")
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
//println("LOOKUP A")
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
//    println("DELETE A")

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
        
        val work = Array.fill(4){
          future(doWork())
          //doWork()
        }

        awaitAll(10000000, work: _*)
        
      }
    }
   }
}
