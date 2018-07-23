package com.weather.scalacass.util

import org.scalatest.{ FlatSpec, Matchers, BeforeAndAfter, BeforeAndAfterAll, OptionValues, EitherValues, TryValues }

abstract class CassandraTester extends FlatSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with OptionValues with EitherValues with TryValues {
  private[util] var _client: Option[CassandraClient] = _
  def client = _client getOrElse sys.error("client must be only be used after beforeAll. Did you override it?")
  implicit def cluster = client.cluster
  implicit def session = client.session
}
