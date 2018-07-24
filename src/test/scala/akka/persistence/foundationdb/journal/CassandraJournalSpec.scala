/*
 * Copyright (C) 2016-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.persistence.foundationdb.journal

import akka.actor.Actor
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.JournalProtocol.{ ReplayMessages, WriteMessageFailure, WriteMessages, WriteMessagesFailed }

import scala.concurrent.duration._
import akka.persistence.journal._
import akka.persistence.foundationdb.FoundationDbLifecycle
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory

object CassandraJournalConfiguration {
  lazy val config = ConfigFactory.parseString(
    s"""
      |cassandra-journal.keyspace=CassandraJournalSpec
      |cassandra-snapshot-store.keyspace=CassandraJournalSpecSnapshot
    """.stripMargin
  ).withFallback(FoundationDbLifecycle.config)

  lazy val perfConfig = ConfigFactory.parseString(
    """
    akka.actor.serialize-messages=off
    cassandra-journal.keyspace=CassandraJournalPerfSpec
    cassandra-snapshot-store.keyspace=CassandraJournalPerfSpecSnapshot
    """
  ).withFallback(config)

  lazy val protocolV3Config = ConfigFactory.parseString(
    s"""
      cassandra-journal.protocol-version = 3
      cassandra-journal.enable-events-by-tag-query = off
      cassandra-journal.keyspace=CassandraJournalProtocolV3Spec
      cassandra-snapshot-store.keyspace=CassandraJournalProtocolV3Spec
    """
  ).withFallback(config)

  lazy val compat2Config = ConfigFactory.parseString(
    s"""
      cassandra-journal.cassandra-2x-compat = on
      cassandra-journal.keyspace=CassandraJournalCompat2Spec
      cassandra-snapshot-store.keyspace=CassandraJournalCompat2Spec
    """
  ).withFallback(config)
}

class CassandraJournalSpec extends JournalSpec(CassandraJournalConfiguration.config) with FoundationDbLifecycle {

  override def supportsRejectingNonSerializableObjects = false
  "A Cassandra Journal" must {

    "be able to replay messages after serialization failure" in {
      // there is no chance that a journal could create a data representation for type of event
      val notSerializableEvent = new Object {
        override def toString = "not serializable"
      }
      val msg = PersistentRepr(
        payload = notSerializableEvent,
        sequenceNr = 6,
        persistenceId = pid,
        sender = Actor.noSender,
        writerUuid = writerUuid
      )

      val probe = TestProbe()

      journal ! WriteMessages(List(AtomicWrite(msg)), probe.ref, actorInstanceId)
      val err = probe.expectMsgPF() {
        case WriteMessagesFailed(cause) => cause
      }
      probe.expectMsg(WriteMessageFailure(msg, err, actorInstanceId))

      journal ! ReplayMessages(5, 5, 1, pid, probe.ref)
      probe.expectMsg(replayedMessage(5))
    }
  }
}

/**
 * Cassandra 2.2.0 or later should support protocol version V4, but as long as we
 * support 2.1.6+ we do some compatibility testing with V3.
 */


class CassandraJournalPerfSpec extends JournalPerfSpec(CassandraJournalConfiguration.perfConfig) with FoundationDbLifecycle {

  override def awaitDurationMillis: Long = 20.seconds.toMillis

  override def supportsRejectingNonSerializableObjects = false
}
