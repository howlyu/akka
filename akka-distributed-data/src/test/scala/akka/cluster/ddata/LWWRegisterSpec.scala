/**
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.cluster.ddata

import akka.actor.Address
import akka.cluster.UniqueAddress
import akka.cluster.ddata.Replicator.Changed
import org.scalatest.Matchers
import org.scalatest.WordSpec

class LWWRegisterSpec extends WordSpec with Matchers {
  import LWWRegister.defaultClock

  val node1 = UniqueAddress(Address("akka.tcp", "Sys", "localhost", 2551), 1)
  val node2 = UniqueAddress(node1.address.copy(port = Some(2552)), 2)

  "A LWWRegister" must {
    "use latest of successive assignments" in {
      val r = (1 to 100).foldLeft(LWWRegister(node1, 0, defaultClock)) {
        case (r, n) ⇒
          r.value should be(n - 1)
          r.withValue(node1, n, defaultClock)
      }
      r.value should be(100)
    }

    "merge by picking max timestamp" in {
      val clock = new LWWRegister.Clock {
        val i = Iterator.from(100)
        override def nextTimestamp(current: Long): Long = i.next()
      }
      val r1 = LWWRegister(node1, "A", clock)
      r1.timestamp should be(100)
      val r2 = r1.withValue(node2, "B", clock)
      r2.timestamp should be(101)
      val m1 = r1 merge r2
      m1.value should be("B")
      m1.timestamp should be(101)
      val m2 = r2 merge r1
      m2.value should be("B")
      m2.timestamp should be(101)
    }

    "merge by picking least address when same timestamp" in {
      val clock = new LWWRegister.Clock {
        override def nextTimestamp(current: Long): Long = 100
      }
      val r1 = LWWRegister(node1, "A", clock)
      val r2 = LWWRegister(node2, "B", clock)
      val m1 = r1 merge r2
      m1.value should be("A")
      val m2 = r2 merge r1
      m2.value should be("A")
    }

    "use monotonically increasing defaultClock" in {
      (1 to 100).foldLeft(LWWRegister(node1, 0, defaultClock)) {
        case (r, n) ⇒
          r.value should be(n - 1)
          val r2 = r.withValue(node1, n, defaultClock)
          r2.timestamp should be > r.timestamp
          r2
      }
    }

    "have unapply extractor" in {
      val r1 = LWWRegister(node1, "a", defaultClock)
      val LWWRegister(value1) = r1
      val value2: String = value1
      Changed("key", r1) match {
        case Changed("key", LWWRegister(value3)) ⇒
          val value4: Any = value3
          value4 should be("a")
      }
    }

    "can be used as first-write-wins-register" in {
      import LWWRegister.reverseClock
      val r = (1 to 100).foldLeft(LWWRegister(node1, 0, reverseClock)) {
        case (r, n) ⇒
          r.value should be(0)
          val newRegister = r.merge(r.withValue(node1, n, reverseClock))
          newRegister should be(r)
          newRegister
      }
      r.value should be(0)
    }
  }
}
