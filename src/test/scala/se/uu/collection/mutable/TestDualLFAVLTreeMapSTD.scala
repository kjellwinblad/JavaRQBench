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



import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.util.Random
import org.scalatest.FunSpec
import scala.collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import se.uu.collection.DualLFAVLTreeMapSTD;
import scala.concurrent._
import ExecutionContext.Implicits.global
import se.uu.collection.CATreeMap

class TestDualLFAVLTreeMapSTD extends FunSpec {
  describe("A DualLFAVLTreeMapSTD") {
    val dualMap = new DualLFAVLTreeMapSTD[Integer, Integer]()
    val map = dualMap.asScala
    val refMap = new TrieMap[Integer,Integer]()
    it("Should initially not be lock-free") {
      assert(!dualMap.isLockFreeMode())
    }
    it("Should be possible to insert elements") {    
      for(i <- 1 to 10){
        refMap += ((i,i))
        map += ((i,i))
      }
      assert(refMap === map)
    }
    it("Should be possible to transfer to a lock free map") {    
      dualMap.transformToLockFree()
      assert(dualMap.isLockFreeMode())
    }
    it("Lock free map should behave as original map") {    
      val lfMap = dualMap.getLockFreeMap()
      for((k,v) <- refMap){
        assert(lfMap.get(k) === refMap.get(k).get)
      }
      assert(lfMap.size() === refMap.size())
    }
    it("should be possible to transfer lock free map to original") {    
      dualMap.transformToLocked()
      assert(!dualMap.isLockFreeMode())    
    }
    it("Should be as before after conversion") {    
      assert(refMap === map)
      assert(refMap.size() === dualMap.size())
    }
    it("Should be possible to decrease and add elements to the lock free map") { 
      dualMap.transformToLockFree()
      val lfMap = dualMap.getLockFreeMap()
      lfMap.put(11,11)
      refMap += ((11,11))
      dualMap.increaseLocalSize()
      lfMap.put(12,12)
      refMap += ((12,12))
      dualMap.increaseLocalSize()
      lfMap.put(13,13)
      refMap += ((13,13))
      dualMap.increaseLocalSize()
      lfMap.remove(13)
      refMap -= (13)
      dualMap.decreaseLocalSize()
      dualMap.transformToLocked()
      assert(refMap.size() === dualMap.size())
      assert(refMap === map)
    }
    it("Should be possible to increase and decrease local size") {    
      dualMap.transformToLockFree()
      assert(dualMap.increaseLocalSize() == 1)
      assert(dualMap.increaseLocalSize() == 2)
      assert(dualMap.increaseLocalSize() == 3)
      assert(dualMap.increaseLocalSize() == 4)
      assert(dualMap.increaseLocalSize() == 5)
      assert(dualMap.decreaseLocalSize() == 4)
      assert(dualMap.readLocalSizeSum() == 4)
      assert(dualMap.decreaseLocalSize() == 3)
      assert(dualMap.decreaseLocalSize() == 2)
      assert(dualMap.decreaseLocalSize() == 1)
      assert(dualMap.decreaseLocalSize() == 0)
    }
    it("Should be possible to wait for write to finish") {    
      val f: Future[Unit] = future { 
        dualMap.indicateWriteStart();
        Thread.sleep(300)
        dualMap.indicateWriteEnd();
      }
      Thread.sleep(100)
      dualMap.waitNoOngoingWrite()
    }
  }
}
