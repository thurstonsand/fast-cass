package com.weather.scalacass.util

import org.cassandraunit.utils.EmbeddedCassandraServerHelper

abstract class CassandraUnitTester extends CassandraTester {
  override def beforeAll() = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(CassandraUnitInfo.cassYaml, 30000L)
    _client = Some(CassandraClient(List("localhost"), Some(EmbeddedCassandraServerHelper.getNativeTransportPort)))
  }

  override def afterAll() = {
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def beforeEach(): Unit = {}
  def afterEach(): Unit = {}

  before {
    beforeEach()
  }

  after {
    afterEach()
  }
}
