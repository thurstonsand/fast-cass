---
layout: docs
title: "Custom Encoders/Decoders"
section: "c3"
---
```tut:invisible
import com.datastax.driver.core.{Cluster, Session}
import com.weather.scalacass.{Result, ScalaSession, CCCassFormatEncoder, CCCassFormatDecoder}
import com.weather.scalacass.syntax._
import com.datastax.driver.core.Row
import com.weather.scalacass.scsession.SCStatement.RightBiasedEither

implicit val cluster = Cluster.builder.addContactPoint("localhost").build()
implicit val session: Session = cluster.connect()

val sSession: ScalaSession = ScalaSession("mykeyspace")
sSession.createKeyspace("replication = {'class':'SimpleStrategy', 'replication_factor' : 1}").execute()

case class MyTable(s: String, i: Int, l: Long)
val createStatement = sSession.createTable[MyTable]("mytable", 1, 0)
createStatement.execute()

val createSpecialTable = sSession.rawStatement("CREATE TABLE specialtable (s varchar PRIMARY KEY, i int, special_long bigint")
createSpecialTable.execute()

val insertStatement = sSession.insert("mytable", MyTable("a_unique_id", 1234, 5678L))
insertStatement.execute()
```
# Custom Encoders/Decoders

In case you need to apply a transformation during the extraction process, don't have a 1-to-1 mapping of case class
names to cassandra table names, or are trying to use a type not included in the ScalaCass library, you can just define a
custom encoder and decoder for any type. We will define a `UniqueId` class as an example for how you might customize 
it. Let's say this class will only accept ids less than 15 characters long.

```tut:silent
abstract case class UniqueId(id: String)
object UniqueId {
  def apply(untestedId: String): UniqueId = 
    if (untestedId.length > 15) throw new IllegalArgumentException("id must be less than 15 characters long")
    else new UniqueId(untestedId) {}
}
```

You can provide a custom type in 3 ways:

## Map over an existing type

This is the easier way to create a custom type, since it is based on an existing decoder/encoder. You first retrieve an
existing encoder/decoder via the `CassFormatEncoder`/`CassFormatDecoder`'s `apply` method.

```tut
import com.weather.scalacass.{CassFormatDecoder, CassFormatEncoder}
implicit val uniqueIdDecoder: CassFormatDecoder[UniqueId] = CassFormatDecoder[String].map[UniqueId](UniqueId.apply)
implicit val uniqueIdEncoder: CassFormatEncoder[UniqueId] = CassFormatEncoder[String].map[UniqueId](uniqueId => uniqueId.id)
```

With these implicits in scope, you can now use the `UniqueId` type directly when encoding a Row. 

First, insert a row:

```tut
case class Insertable(s: UniqueId, i: Int, l: Long)
val insertStatement = sSession.insert("mytable", Insertable(UniqueId("a_unique_id"), 1234, 5678L))
insertStatement.execute()
```

Then, select that row:

```tut
case class Query(s: UniqueId)
val selectStatement = sSession.selectOneStar("mytable", Query(UniqueId("a_unique_id")))
val res = selectStatement.execute()
```

Then, extract using `UniqueId`:

```tut
res.map(_.map(_.as[UniqueId]("s")))
```

Of course, UniqueId might throw an exception, which may not be the behavior you want, so you can optionally use 
`flatMap` for operations that might fail, which uses a `Result[T]` type, which is just an alias to `Either[Throwable, T]`:

```tut:silent
abstract case class SafeUniqueId(id: String)
object SafeUniqueId {
  def apply(untestedId: String): Result[SafeUniqueId] =
    if (untestedId.length > 15) Left(new IllegalArgumentException("id must be less than 15 characters long"))
    else Right(new SafeUniqueId(untestedId) {})
}
```

And with this definition, let's redefine the encoder/decoder:

```tut
implicit val safeUniqueIdDecoder: CassFormatDecoder[SafeUniqueId] = CassFormatDecoder[String].flatMap[SafeUniqueId](SafeUniqueId.apply)
implicit val safeUniqueIdEncoder: CassFormatEncoder[SafeUniqueId] = CassFormatEncoder[String].map[SafeUniqueId](safeId => safeId.id)
```
So, let's go through the same steps this time, except inject an id that is too long for extraction

```tut
case class UnsafeInsertable(s: String, i: Int, l: Long)
val unsafeInsertStatement = sSession.insert("mytable", UnsafeInsertable("this_id_is_definitely_too_long_to_be_safe", 1234, 5678L))
unsafeInsertStatement.execute()
```

And then select that row:

```tut
case class UnsafeQuery(s: String)
val unsafeSelectStatement = sSession.selectOneStar("mytable", UnsafeQuery("this_id_is_definitely_too_long_to_be_safe"))
val unsafeRes = unsafeSelectStatement.execute()
```

And finally, try to extract it:

```tut
unsafeRes.map(_.map(_.attemptAs[SafeUniqueId]("s")))
```

## Using `forProduct$arity` for case classes

When parsing the entire row into a case class, sometimes it may not be possible to encapsulate specialized logic
using the basic encoders. In these cases, `forProduct$arity` can be used as a way to have complete control over
how values are extracted out of/inserted into a row. They can also be used when names in a Cassandra row do not match
the names in the case class. Since it only applies on operations to an entire row, the functions are available
on `CCCassFormatEncoder` and `CCCassFormatDecoder`. The functions take a format of 
`forProduct1`/`forProduct2`/...`forProduct22`, and you choose the one that matches the number of arguments you wish
to extract/insert into a row.

```tut
object Wrapper {
  case class SpecialInsert(s: String, i: Int, specialLong: Long)
  object SpecialInsert {
    implicit val ccDecoder: CCCassFormatDecoder[SpecialInsert] =
      CCCassFormatDecoder.forProduct3("s", "i", "special_long")((s: String, i: Int, specialLong: Long) => SpecialInsert(s, i, specialLong))
    implicit val ccEncoder: CCCassFormatEncoder[SpecialInsert] =
      CCCassFormatEncoder.forProduct3("s", "i", "special_long")((sc: SpecialInsert) => (sc.s, sc.i, sc.specialLong))
  }
}
import Wrapper._ // Wrapper is necessary for this interpreter, and should be excluded in your code
```

And now the SpecialInsert is ready to be used:

```tut
val specialInsertStatement = sSession.insert("specialtable", SpecialInsert("asdf", 1234, 5678L))
specialInsertStatement.execute()
```

Renaming is not the only usage of `forProduct$arity`, nor is it strictly required to create one for a case class.

## Create a new encoder/decoder from scratch

You might use a new encoder/decoder from scratch if you've added a user type to Cassandra itself, and want to use the 
library to read from it. However, let's continue with the `UniqueId` example, as above.

```tut:invisible
// shadow these previous implicits so we can create new ones
val safeUniqueIdDecoder = ""
val safeUniqueIdEncoder = ""
```

For decoder
* `type From` is the Java type that is extracted from Cassandra directly, from which you will convert to a Scala type
* `val typeToken` is the special class instance for that type
  * `TypeToken` is used over `classOf` because it can correctly encode type parameters to `Map`s, `List`s, and `Set`s
  * `CassFormatDecoder` provides 3 helper functions for these types: `CassFormatDecoder.mapOf`, `.listOf`, and `.setOf`
* `def f2t` defines the transformation from the Java type to the Scala type
* `def extract` defines the way to extract the Java type from the Cassandra `Row`
* `def tupleExtract` is the same as `extract`, but for tuples

```tut
import com.google.common.reflect.TypeToken, com.datastax.driver.core.{Row, TupleValue}
implicit val safeUniqueIdDecoder: CassFormatDecoder[SafeUniqueId] = new CassFormatDecoder[SafeUniqueId] {
  type From = String
  val typeToken = TypeToken.of(classOf[String])
  def f2t(f: String): Result[SafeUniqueId] = SafeUniqueId(f)
  def extract(r: Row, name: String): From = r.getString(name)
  def tupleExtract(tup: TupleValue, pos: Int): From = tup.getString(pos)
}
```

For encoder
* `type From` is the Scala type which you are encoding from
* `val cassDataType` is the Cassandra type which you are converting to
* `def encode` is the way that you encode that conversion, meaning the Scala -> Java conversion

```tut
import com.datastax.driver.core.DataType
implicit val safeUniqueIdEncoder: CassFormatEncoder[SafeUniqueId] = new CassFormatEncoder[SafeUniqueId] {
  type From = String
  val cassDataType: DataType = DataType.varchar()
  def encode(f: SafeUniqueId): Result[String] = Right(f.id)
}
```

And as before,

```tut:invisible
case class UnsafeInsertable(s: String, i: Int, l: Long)
val unsafeInsertStatement = sSession.insert("mytable", UnsafeInsertable("this_id_is_definitely_too_long_to_be_safe", 1234, 5678L))
unsafeInsertStatement.execute()
```
```tut
case class UnsafeQuery(s: String)
val unsafeSelectStatement = sSession.selectOneStar("mytable", UnsafeQuery("this_id_is_definitely_too_long_to_be_safe"))
val unsafeRes = unsafeSelectStatement.execute()
unsafeRes.map(_.map(_.attemptAs[SafeUniqueId]("s")))
```
```tut:invisible
sSession.dropKeyspace.execute()
sSession.close()
cluster.close()
```