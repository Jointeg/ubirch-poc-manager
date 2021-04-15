package com.ubirch.db.tables
import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.models.ExampleData
import monix.eval.Task

trait ExampleDataRepository {
  def createExampleData(exampleData: ExampleData): Task[Unit]
  def updateExampleData(exampleData: ExampleData): Task[Unit]
  def removeExampleData(id: Int): Task[Unit]
  def getExampleData(id: Int): Task[ExampleData]
}

class ExampleDataTable @Inject() (quillJdbcContext: QuillJdbcContext) extends ExampleDataRepository {
  import quillJdbcContext.ctx._
  private def createExampleDataQuery(exampleData: ExampleData) =
    quote {
      query[ExampleData].insert(lift(exampleData))
    }

  private def updateExampleDataQuery(exampleData: ExampleData) =
    quote {
      query[ExampleData].filter(_.id == lift(exampleData.id)).update(lift(exampleData))
    }

  private def removeExampleDataQuery(id: Int) =
    quote {
      query[ExampleData].filter(_.id == lift(id)).delete
    }

  private def getExampleDataQuery(id: Int) =
    quote {
      query[ExampleData].filter(_.id == lift(id))
    }

  override def createExampleData(exampleData: ExampleData): Task[Unit] = Task(run(createExampleDataQuery(exampleData)))
  override def updateExampleData(exampleData: ExampleData): Task[Unit] = Task(run(updateExampleDataQuery(exampleData)))
  override def removeExampleData(id: Int): Task[Unit] = Task(run(removeExampleDataQuery(id)))
  override def getExampleData(id: Int): Task[ExampleData] = Task(run(getExampleDataQuery(id))).map(_.head)
}
