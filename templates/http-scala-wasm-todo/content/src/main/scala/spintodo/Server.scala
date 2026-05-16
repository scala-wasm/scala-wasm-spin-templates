package spintodo

import scala.collection.mutable
import scala.scalajs.WitUtils._
import scala.scalajs.{wit => wm}
import scala.scalajs.wasi.http.types._
import scala.scalajs.wit.annotation._
import scala.util.control.NonFatal

import spintodo.exports.wasi.http.IncomingHandler
import spintodo.fermyon.spin.sqlite
import spintodo.fermyon.spin.sqlite.{Connection, Value}

@WitImplementation
object Server extends IncomingHandler {
  override def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit =
    TodoApp.handle(request, outParam)
}

object TodoApp {
  def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit = {
    try {
      val result = route(request)
      send(outParam, result.status, result.contentType, result.body)
    } catch {
      case err: RequestError =>
        send(outParam, err.status, "text/plain; charset=utf-8", err.getMessage)
      case NonFatal(err) =>
        send(outParam, 500, "text/plain; charset=utf-8", err.toString())
    }
  }

  private def route(request: IncomingRequest): Response = {
    val path = normalizePath(toOption(request.pathWithQuery()).getOrElse("/"))

    methodName(request.method()) match {
      case "GET" if path == "/" || path == "/todos" =>
        Response(200, "application/json", todosJson(listTodos()))

      case "POST" if path == "/" || path == "/todos" =>
        val title = extractTitle(readBody(request)).getOrElse(
            throw RequestError(400, "expected request body or JSON object with title"))
        Response(201, "application/json", todoJson(createTodo(title)))

      case "DELETE" if path.startsWith("/todos/") =>
        val id = parseId(path.substring("/todos/".length))
        deleteTodo(id)
        Response(200, "application/json", "{\"deleted\":true}")

      case _ =>
        throw RequestError(404, "not found")
    }
  }

  private def listTodos(): Array[Todo] = withConnection { conn =>
    execute(conn, "SELECT id, title, completed, created_at FROM todos ORDER BY id", Array.empty)
      .rows
      .map(todoFromRow)
  }

  private def createTodo(title: String): Todo = withConnection { conn =>
    execute(conn, "INSERT INTO todos (title) VALUES (?)", Array(Value.Text(title)))
    execute(conn,
        "SELECT id, title, completed, created_at FROM todos WHERE id = last_insert_rowid()",
        Array.empty).rows.headOption.map(todoFromRow).getOrElse(
        throw RequestError(500, "failed to load inserted todo"))
  }

  private def deleteTodo(id: Long): Unit = withConnection { conn =>
    execute(conn, "DELETE FROM todos WHERE id = ?", Array(Value.Integer(id)))
    ()
  }

  private def withConnection[A](body: Connection => A): A = {
    val conn = unwrap(Connection.open("default"))
    try body(conn)
    finally conn.close()
  }

  private def execute(conn: Connection, statement: String,
      parameters: Array[Value]): sqlite.QueryResult =
    unwrap(conn.execute(statement, parameters))

  private def unwrap[A](result: wm.Result[A, sqlite.Error]): A =
    toEither(result).fold(err => throw RequestError(500, s"sqlite error: $err"), identity)

  private def todoFromRow(row: sqlite.RowResult): Todo = {
    val values = row.values
    if (values.length != 4)
      throw RequestError(500, "unexpected sqlite row shape")

    Todo(
        id = expectInteger(values(0), "id"),
        title = expectText(values(1), "title"),
        completed = expectInteger(values(2), "completed") != 0L,
        createdAt = expectText(values(3), "created_at"))
  }

  private def expectInteger(value: Value, column: String): Long = value match {
    case Value.Integer(value) => value
    case _                   => throw RequestError(500, s"expected integer column $column")
  }

  private def expectText(value: Value, column: String): String = value match {
    case Value.Text(value) => value
    case _                 => throw RequestError(500, s"expected text column $column")
  }

  private def readBody(request: IncomingRequest): String = {
    val bytes = (for {
      body <- toEither(request.consume())
      inputStream <- toEither(body.stream())
    } yield {
      val in = mutable.ArrayBuffer.empty[Byte]
      var eof = false

      while (!eof) {
        toEither(inputStream.blockingRead(1024L)) match {
          case Right(chunk) =>
            if (chunk.length == 0) eof = true
            else in ++= chunk
          case Left(_) =>
            eof = true
        }
      }

      in.toArray
    }).getOrElse(throw RequestError(400, "failed to read request body"))

    new String(bytes, "UTF-8")
  }

  private def send(outParam: ResponseOutparam, status: Int,
      contentType: String, responseBody: String): Unit = {
    val headers = toEither(Fields.fromList(Array(
        wm.Tuple2("content-type", contentType.getBytes("UTF-8"))))).getOrElse(Fields())
    val response = OutgoingResponse(headers)

    toEither(response.setStatusCode(status.toShort)).getOrElse(
        throw new Error(s"failed to set response status $status"))

    val body = toEither(response.body()).getOrElse(
        throw new Error("failed to obtain outgoing response body"))

    ResponseOutparam.set(outParam, new wm.Ok(response))

    val out = toEither(body.write()).getOrElse(
        throw new Error("failed to get outgoing stream"))
    toEither(out.blockingWriteAndFlush(responseBody.getBytes("UTF-8"))).getOrElse(
        throw new Error("failed to write response body"))

    out.close()
    toEither(OutgoingBody.finish(body, java.util.Optional.empty[Trailers]())).getOrElse(
        throw new Error("failed to finish outgoing body"))
  }

  private def methodName(method: Method): String = method match {
    case Method.Get          => "GET"
    case Method.Post         => "POST"
    case Method.Delete       => "DELETE"
    case other: Method.Other => other.value.toUpperCase()
    case _                   => method.toString().toUpperCase()
  }

  private def normalizePath(pathWithQuery: String): String = {
    val queryIndex = pathWithQuery.indexOf('?')
    val path =
      if (queryIndex >= 0) pathWithQuery.substring(0, queryIndex)
      else pathWithQuery

    if (path.length > 1 && path.endsWith("/")) path.substring(0, path.length - 1)
    else path
  }

  private def parseId(value: String): Long = {
    try value.toLong
    catch {
      case _: NumberFormatException => throw RequestError(400, "invalid todo id")
    }
  }

  private def extractTitle(body: String): Option[String] = {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) None
    else if (trimmed.startsWith("{"))
      extractJsonStringField(trimmed, "title").map(_.trim()).filter(_.nonEmpty)
    else Some(trimmed).filter(_.nonEmpty)
  }

  private def extractJsonStringField(json: String, field: String): Option[String] = {
    val keyIndex = json.indexOf("\"" + field + "\"")
    if (keyIndex < 0)
      return None

    val colonIndex = json.indexOf(':', keyIndex + field.length + 2)
    if (colonIndex < 0)
      return None

    val start = json.indexOf('"', colonIndex + 1)
    if (start < 0)
      return None

    val builder = new StringBuilder
    var i = start + 1
    var escaped = false
    while (i < json.length) {
      val ch = json.charAt(i)
      if (escaped) {
        builder.append(ch match {
          case '"'   => '"'
          case '\\'  => '\\'
          case 'n'   => '\n'
          case 'r'   => '\r'
          case 't'   => '\t'
          case other => other
        })
        escaped = false
      } else if (ch == '\\') {
        escaped = true
      } else if (ch == '"') {
        return Some(builder.toString())
      } else {
        builder.append(ch)
      }
      i += 1
    }

    None
  }

  private def todosJson(todos: Array[Todo]): String =
    todos.map(todoJson).mkString("[", ",", "]")

  private def todoJson(todo: Todo): String =
    "{" +
      "\"id\":" + todo.id + "," +
      "\"title\":\"" + escapeJson(todo.title) + "\"," +
      "\"completed\":" + todo.completed + "," +
      "\"created_at\":\"" + escapeJson(todo.createdAt) + "\"" +
      "}"

  private def escapeJson(value: String): String = {
    val builder = new StringBuilder
    value.foreach {
      case '"'  => builder.append("\\\"")
      case '\\' => builder.append("\\\\")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case ch   => builder.append(ch)
    }
    builder.toString()
  }

  private final case class Todo(id: Long, title: String,
      completed: Boolean, createdAt: String)

  private final case class Response(status: Int, contentType: String, body: String)

  private final case class RequestError(status: Int, message: String)
      extends RuntimeException(message)
}
