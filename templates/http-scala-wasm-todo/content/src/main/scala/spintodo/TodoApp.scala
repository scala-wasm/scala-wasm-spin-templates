package spintodo

import scala.scalajs.WitUtils._
import scala.scalajs.{wit => wm}

import org.typelevel.jawn.ast.{JArray, JBool, JNum, JObject, JString, JValue}
import spintodo.fermyon.spin.sqlite
import spintodo.fermyon.spin.sqlite.{Connection, Value}

object TodoApp {
  def listTodos(): Either[String, Array[Todo]] = withConnection { conn =>
    execute(conn, "SELECT id, title, completed, created_at FROM todos ORDER BY id", Array.empty)
      .flatMap(result => rowsToTodos(result.rows))
  }

  def createTodo(title: String): Either[String, Todo] = withConnection { conn =>
    for {
      _ <- execute(conn, "INSERT INTO todos (title) VALUES (?)", Array(Value.Text(title)))
      result <- execute(conn,
        "SELECT id, title, completed, created_at FROM todos WHERE id = last_insert_rowid()",
        Array.empty)
      todo <- result.rows.headOption.map(todoFromRow).getOrElse(
        Left("failed to load inserted todo"))
    } yield todo
  }

  def deleteTodo(id: Long): Either[String, Unit] = withConnection { conn =>
    execute(conn, "DELETE FROM todos WHERE id = ?", Array(Value.Integer(id))).map(_ => ())
  }

  def todosJsonValue(todos: Array[Todo]): JValue =
    JArray.fromSeq(todos.toSeq.map(todoJsonValue))

  def todoJsonValue(todo: Todo): JValue =
    JObject.fromSeq(Seq(
        "id" -> JNum(todo.id),
        "title" -> JString(todo.title),
        "completed" -> JBool(todo.completed),
        "created_at" -> JString(todo.createdAt)))

  private def withConnection[A](body: Connection => Either[String, A]): Either[String, A] =
    unwrap(Connection.open("default")).flatMap { conn =>
      try body(conn)
      finally conn.close()
    }

  private def execute(conn: Connection, statement: String,
      parameters: Array[Value]): Either[String, sqlite.QueryResult] =
    unwrap(conn.execute(statement, parameters))

  private def unwrap[A](result: wm.Result[A, sqlite.Error]): Either[String, A] =
    toEither(result).left.map(err => s"sqlite error: $err")

  private def rowsToTodos(rows: Array[sqlite.RowResult]): Either[String, Array[Todo]] = {
    val todos = new Array[Todo](rows.length)
    var i = 0
    while (i < rows.length) {
      todoFromRow(rows(i)) match {
        case Right(todo) => todos(i) = todo
        case Left(error) => return Left(error)
      }
      i += 1
    }
    Right(todos)
  }

  private def todoFromRow(row: sqlite.RowResult): Either[String, Todo] = {
    val values = row.values
    if (values.length != 4)
      Left("unexpected sqlite row shape")
    else
      for {
        id <- expectInteger(values(0), "id")
        title <- expectText(values(1), "title")
        completed <- expectInteger(values(2), "completed")
        createdAt <- expectText(values(3), "created_at")
      } yield Todo(id, title, completed != 0L, createdAt)
  }

  private def expectInteger(value: Value, column: String): Either[String, Long] = value match {
    case Value.Integer(value) => Right(value)
    case _                   => Left(s"expected integer column $column")
  }

  private def expectText(value: Value, column: String): Either[String, String] = value match {
    case Value.Text(value) => Right(value)
    case _                 => Left(s"expected text column $column")
  }
}
