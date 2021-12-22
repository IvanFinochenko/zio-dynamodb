package zio.dynamodb

import io.github.vigoo.zioaws.dynamodb.DynamoDb
import zio.stm.{ STM, TMap }
import zio.{ Has, ULayer, URLayer, ZIO }

trait DynamoDBExecutor {
  def execute[A](atomicQuery: DynamoDBQuery[A]): ZIO[Any, Throwable, A]
}

object DynamoDBExecutor {
  val live: URLayer[DynamoDb, Has[DynamoDBExecutor]] =
    ZIO.service[DynamoDb.Service].map(dynamo => DynamoDBExecutorImpl(dynamo)).toLayer

  lazy val test: ULayer[Has[DynamoDBExecutor] with Has[TestDynamoDBExecutor]] =
    (for {
      test <- (for {
                  tableMap       <- TMap.empty[String, TMap[PrimaryKey, Item]]
                  tablePkNameMap <- TMap.empty[String, String]
                } yield TestDynamoDBExecutorImpl(tableMap, tablePkNameMap)).commit
    } yield Has.allOf[DynamoDBExecutor, TestDynamoDBExecutor](test, test)).toLayerMany

  def test(tableDefs: TableNameAndPK*): ULayer[Has[DynamoDBExecutor] with Has[TestDynamoDBExecutor]] =
    (for {
      test <- (for {
                  tableMap       <- TMap.empty[String, TMap[PrimaryKey, Item]]
                  tablePkNameMap <- TMap.empty[String, String]
                  _              <- STM.foreach(tableDefs) {
                                      case (tableName, pkFieldName) =>
                                        for {
                                          _           <- tablePkNameMap.put(tableName, pkFieldName)
                                          pkToItemMap <- TMap.empty[PrimaryKey, Item]
                                          _           <- tableMap.put(tableName, pkToItemMap)
                                        } yield ()
                                    }
                } yield TestDynamoDBExecutorImpl(tableMap, tablePkNameMap)).commit
    } yield Has.allOf[DynamoDBExecutor, TestDynamoDBExecutor](test, test)).toLayerMany

}
