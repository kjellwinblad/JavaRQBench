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

package se.uu.collection.mutable

import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.util.Random
import org.scalatest.FunSpec
import se.uu.collection._;
import scala.collection.JavaConverters._

class MapSpec extends FunSpec {

/*   describe("A LinearHashMap") {
     def mapCreator:Map[Int,Int] = (new se.uu.collection.ConcDCountLinearHashMap[Int,Int]()).asScala
     testMap(mapCreator _)
   }
   */
   // describe("A ImmTreapCATreeMapSTDR") {
   //   def mapCreator:Map[Int,Int] = (new se.uu.collection.ImmTreapCATreeMapSTDR[Int,Int]()).asScala
   //   testMap(mapCreator _)
   // }

     describe("A LockFreeImmTreapCATreeMapSTDR") {
     def mapCreator:Map[Int,Int] = (new se.uu.collection.LockFreeImmTreapCATreeMapSTDR[Int,Int]()).asScala
     testMap(mapCreator _)
   }



  /*
   describe("A ConcurrentLinearHashMap") {
   def mapCreator:Map[Int,Int] = new ConcurrentLinearHashMap[Int,Int]()
   testMap(mapCreator _)
   }

   describe("A FCLinearHashMap") {
   def mapCreator:Map[Int,Int] = new FCLinearHashMap[Int,Int]()
   testMap(mapCreator _)
   }

   describe("A SLinearHashMap") {
   def mapCreator:Map[Int,Int] = SLinearHashMap[Int,Int]()
   testMap(mapCreator _)
   }

   /*  describe("A FSTLinearHashMap") {
   def mapCreator:Map[Int,Int] = new FCTLinearHashMap()
   testMap(mapCreator _)
   }
   */
    describe("A FSTLinearHashMap Tree node") {
   def mapCreator:Map[Int,Int] = FCTLinearHashMap.treeMap[Int](64)
   testMap(mapCreator _)
   }

   describe("A ELinearHashMap node") {
   def mapCreator:Map[Int,Int] = new ELinearHashMap
   testMap(mapCreator _)
   }



   describe("A EXLinearHashMap node") {
   def mapCreator:Map[Int,Int] = new EXLinearHashMap
    testMap(mapCreator _)
   }
   

   describe("A SLinearHashMap") {
   def mapCreator:Map[Int,Int] = SLinearHashMap[Int,Int]()
   testMap(mapCreator _)
   }

   describe("A LEXLinearHashMap node") {
   def mapCreator:Map[Int,Int] = new LEXLinearHashMap
   testMap(mapCreator _)
   }
   */

  // describe("A StatisticsMap node") {
  //   def mapCreator:Map[Int,Int] = new StatisticsMap
  //   testMap(mapCreator _)
  // }

  // describe("A ATreeMap node") {
  //   def mapCreator:Map[Int,Int] = new ATreeMap
  //   testMap(mapCreator _)
  // }

  // describe("A AVLTreeMap") {
  //   def mapCreator:Map[Int,Int] = (new AVLTreeMap[Int,Int]()).asScala
  //   testMap(mapCreator _)
  // }

  // describe("A CATreeMapSafeOpt") {
  //   def mapCreator:Map[Int,Int] = (new CATreeMapSafeOpt[Int,Int]()).asScala
  //   testMap(mapCreator _)
  // }

  // describe("A FatSkipListMap") {
  //   def mapCreator:Map[Int,Int] = (new FatSkipListMap[Int,Int]()).asScala
  //   testMap(mapCreator _)
  // }


  private def testMap(mapCreator: (() => Map[Int, Int])){
    describe("when created") {
      val map = mapCreator()
      it("should have size 0") {
        assert(map.size === 0)
      }
    }
    
    describe("when one element is added") {
      val map = mapCreator()

      map.+=((1, 42))

      it("should have size 1") {
        assert(map.size === 1)
      }

      it("should return the element on lookup") {
        assert(map.get(1) === Some(42))
      }
    }

    for(n <- 1 to 5){
      describe("when " + n + " elements are inserted") {
        val map = mapCreator()
        val refMap = new scala.collection.mutable.HashMap[Int,Int]()

        for (i <- 1 to n) {
          refMap.+=((i, i))
          map.+=((i, i))
        }

        it("should result in the same thing as when performing the actions on a HashSet") {
          assert(refMap.iterator.toSet === map.iterator.toSet)
        }

        it("should be possible to retrive an inserted element") {
          for (i <- 1 to n) {
            assert(map.get(i) === Some(i), "retrive after insert")
          }
        }

      }
    }

    for(u <- List(1,2,3,4,10,100,1000,10000, 100000/**/)){

      describe("when " + u + " elements are added and then removed") {
        val map = mapCreator()
        println("CREATE MAP WITH " + u);
        for (i <- 1 to u) {
          map.+=((i, i))
        }

        for (i <- 1 to u) {
          val res = map.get(i)
          assert(res === Some(i), "retrive after insert")
        }
        
        for (i <- 1 to u) {
          val sizeBefore = map.size

          map.-=(i)

          val getRes = map.get(99117)

          val sizeAfter = map.size

          assert(sizeBefore > sizeAfter)
        }
        
        for (i <- 1 to u) {
          var v:Option[Int] = None
          v = map.get(i)
          assert(v === None, "retrive after delete")
        }
        

        it("should have size 0 after all elements are deleted") {
          assert(map.size === 0)
        }

        it("should also return None on lookup") {
          for (i <- 1 to 10000) {
            assert(map.get(i) === None)
          }
        }

      }

    }



    describe("when one element is added and deleted") {
      val map = mapCreator()

      map.+=((1, 42))

      map.-=(1)

      it("should have size 0") {
        assert(map.size === 0)
      }

      it("should return None on lookup") {
        assert(map.get(1) === None)
      }
    }

    describe("when 10000 elements are added") {
      val map = mapCreator()

      for (i <- 1 to 10000) {
        map.+=((i, i))
      }

      it("should have size 10000") {
        assert(map.size === 10000)
      }

      it("should return the inserted elements on lookup") {
        for (i <- 1 to 10000) {
          assert(map.get(i) === Some(i))
        }
      }

    }

    describe("This pattern should not fail") {

      val lmap = mapCreator()
      val hmap = new HashMap[Int, Int]()
      var value = 0
      //++ 3
      value = 3
      lmap.+=((value, value))
      hmap.+=((value, value))
      //-- 2
      value = 2
      lmap.-=(value)
      hmap.-=(value)
      //++ 0
      value = 0
      lmap.+=((value, value))
      hmap.+=((value, value))
      //++ 1
      value = 1
      lmap.+=((value, value))
      hmap.+=((value, value))
      //++ 1
      value = 1
      lmap.+=((value, value))
      hmap.+=((value, value))
      //++ 3
      value = 3
      lmap.+=((value, value))
      hmap.+=((value, value))
      //-- 3
      value = 3
      lmap.-=(value)
      hmap.-=(value)
      //++ 3
      value = 3
      lmap.+=((value, value))
      hmap.+=((value, value))
      //++ 0
      value = 0
      lmap.+=((value, value))
      hmap.+=((value, value))
      //++ 0
      value = 0
      lmap.+=((value, value))
      hmap.+=((value, value))
      it("should give the same result as Scala HashMap ") {
        assert((Set() ++ lmap) === (Set() ++ hmap))
      }

      it("should be empty after deletion"){
        for ((k, v) <- hmap) {
          lmap.-=(k)
        }
        assert(Set[Int]().toStream === (Set[Int]() ++ lmap).toStream)
      }

    }

    describe("When doing random insert and delete") {
      val randomGenerator = new Random()

      for(iteration <- (1 to 20)){
        for(size <- List(0.0001, 0.001, 0.01/*, 0.1, 1.0*/)){
          
          val lmap = mapCreator()
          val hmap = new HashMap[Int, Int]()

          for (i <- 1 to (100000*size).toInt) {
            val value = ((40000*size).toInt * randomGenerator.nextDouble()).toInt
            if (randomGenerator.nextDouble() > 0.3) {
              lmap.+=((value, value))
              hmap.+=((value, value))
            } else {
              lmap.-=(value)
              hmap.-=(value)
            }
          }

          it("should give the same resutl as Scala HashMap " + size + "_" + iteration) {
            assert((Set() ++ lmap) === (Set() ++ hmap))
            for ((k, v) <- hmap) {
              assert(lmap.get(k) === hmap.get(k))
            }
          }
          it("should be empty after deletion " + size + "_" + iteration){
            for ((k, v) <- hmap) {
              lmap.-=(k)
            }
            assert((Set[Int]()) === (Set[Int]() ++ lmap))
          }
        }
      }
    }
  }

  import java.lang.{Integer => JInt}

  var avlTreeMap = new FatSkipListMap[JInt,JInt]()

  for(i <- (1 to 3000)){
    avlTreeMap.put(i, i)
    if(i >= 2){
      //Test split
      val writeBackSplitKey = new Array[Object](1)
      val writeBackRightTree = new Array[SplitableAndJoinableMap[JInt, JInt]](1)
      val leftTree = avlTreeMap.split(writeBackSplitKey, writeBackRightTree)
      val splitKey:JInt = writeBackSplitKey(0).asInstanceOf[JInt]
      val rightTree = writeBackRightTree(0)
      assert(leftTree.asScala.max._1 < splitKey)
      assert(rightTree.asScala.min._1 >= splitKey)
      assert(leftTree.asScala.max._1 < rightTree.asScala.min._1)
      assert((Set() ++ (leftTree.asScala ++ rightTree.asScala)) === (Set() ++ (1 to i).map((n)=>(n,n))))
      assert((leftTree.size + rightTree.size) === i)
      //Test join
      avlTreeMap = leftTree.join(rightTree).asInstanceOf[FatSkipListMap[JInt,JInt]]
      assert((Set() ++ avlTreeMap.asScala) === (Set() ++ (1 to i).map((n)=>(n,n))))
      assert((avlTreeMap.asScala.size) === i)
    }
  }
  import scala.util.Random

  for(i <- (1 to 1000)){
    val left = new FatSkipListMap[JInt,JInt]()
    val right = new FatSkipListMap[JInt,JInt]()
    val elementsInLeft = Random.nextInt(100)
    val elementsInRight = Random.nextInt(100)
    for(e <- (1 to elementsInLeft)){
      left.put(e,e)
    }
    for(e <- (1 to elementsInRight)){
      left.put(e+elementsInLeft,e+elementsInLeft)
    }
    val joined = left.join(right).asInstanceOf[FatSkipListMap[JInt,JInt]]
    assert((Set() ++ joined.asScala) === (Set() ++ (1 to (elementsInLeft + elementsInRight)).map((n)=>(n,n))))
    assert((joined.asScala.size) === (elementsInLeft + elementsInRight))
  }

}
