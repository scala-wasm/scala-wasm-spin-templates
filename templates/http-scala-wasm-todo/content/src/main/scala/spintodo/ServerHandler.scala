package spintodo

import scala.collection.mutable
import scala.scalajs.WitUtils._
import scala.scalajs.{wit => wm}
import scala.scalajs.wasi.http.types._
import scala.util.control.NonFatal

import org.typelevel.jawn.ast.{JBool, JObject, JParser, JValue}

object ServerHandler {
  def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit = {
    try {
      route(request) match {
        case Right(result) =>
          sendJson(outParam, result.status, result.body)
        case Left(error) =>
          sendText(outParam, 500, error)
      }
    } catch {
      case err: RequestError =>
        sendText(outParam, err.status, err.getMessage)
      case NonFatal(err) =>
        sendText(outParam, 500, err.toString())
    }
  }

  private def route(request: IncomingRequest): Either[String, JsonResponse] = {
    val path = normalizePath(toOption(request.pathWithQuery()).getOrElse("/"))

    request.method() match {
      case Method.Get if path == "/" || path == "/todos" =>
        TodoApp.listTodos().map(todos =>
          JsonResponse(200, TodoApp.todosJsonValue(todos)))

      case Method.Post if path == "/" || path == "/todos" =>
        val title = JParser.parseFromString(readBody(request)).toOption
          .flatMap(_.get("title").getString)
          .map(_.trim())
          .filter(_.nonEmpty)
          .getOrElse(throw RequestError(400, "expected JSON object with title"))
        TodoApp.createTodo(title).map(todo =>
          JsonResponse(201, TodoApp.todoJsonValue(todo)))

      case Method.Delete if path.startsWith("/todos/") =>
        val id = parseId(path.substring("/todos/".length))
        TodoApp.deleteTodo(id).map(_ =>
          JsonResponse(200, JObject.fromSeq(Seq("deleted" -> JBool(true)))))

      case _ =>
        throw RequestError(404, "not found")
    }
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

  private def sendJson(outParam: ResponseOutparam, status: Int, body: JValue): Unit =
    send(outParam, status, "application/json", body.render())

  private def sendText(outParam: ResponseOutparam, status: Int, body: String): Unit =
    send(outParam, status, "text/plain; charset=utf-8", body)

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
}
