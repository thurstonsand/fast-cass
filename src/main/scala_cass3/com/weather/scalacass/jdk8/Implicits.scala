package com.weather.scalacass.jdk8

import com.weather.scalacass.{CassFormatDecoder, CassFormatEncoder}
import com.weather.scalacass.CassFormatDecoderVersionSpecific.codecCassFormatDecoder
import CassFormatEncoder.sameTypeCassFormatEncoder
import java.time.{Instant, LocalDate, LocalTime, ZonedDateTime}

import com.datastax.driver.core.{DataType, Cluster}

object Implicits {
  implicit val timeEncoder: CassFormatEncoder[LocalTime] = sameTypeCassFormatEncoder(DataType.time)
  implicit val timeDecoder: CassFormatDecoder[LocalTime] = codecCassFormatDecoder(classOf[LocalTime])

  implicit val dateEncoder: CassFormatEncoder[LocalDate] = sameTypeCassFormatEncoder(DataType.date)
  implicit val dateDecoder: CassFormatDecoder[LocalDate] = codecCassFormatDecoder(classOf[LocalDate])

  implicit val instantEncoder: CassFormatEncoder[Instant] = sameTypeCassFormatEncoder(DataType.timestamp)
  implicit val instantDecoder: CassFormatDecoder[Instant] = codecCassFormatDecoder(classOf[Instant])

  // not sure if this is ok, or if I should ask for a cluster instance
  implicit def zonedDateTimeEncoder(implicit cluster: Cluster): CassFormatEncoder[ZonedDateTime] =
    sameTypeCassFormatEncoder(cluster.getMetadata.newTupleType(DataType.timestamp, DataType.varchar))
  implicit val zonedDateTimeDecoder: CassFormatDecoder[ZonedDateTime] = codecCassFormatDecoder(classOf[ZonedDateTime])
}
