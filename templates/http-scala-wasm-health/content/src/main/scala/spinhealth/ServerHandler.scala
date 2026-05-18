package spinhealth

import scala.scalajs.WitUtils._
import scala.scalajs.{wit => wm}
import scala.scalajs.wasi.http.types._
import scala.util.control.NonFatal

object ServerHandler {
  def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit = {
    try {
      val path = normalizePath(toOption(request.pathWithQuery()).getOrElse("/"))

      request.method() match {
        case Method.Get if path == "/health" =>
          send(outParam, 200, "application/json", """{"status":"ok"}""")
        case _ =>
          send(outParam, 404, "text/plain; charset=utf-8", "not found")
      }
    } catch {
      case NonFatal(err) =>
        send(outParam, 500, "text/plain; charset=utf-8", err.toString())
    }
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

  private def normalizePath(pathWithQuery: String): String = {
    val queryIndex = pathWithQuery.indexOf('?')
    val path =
      if (queryIndex >= 0) pathWithQuery.substring(0, queryIndex)
      else pathWithQuery

    if (path.length > 1 && path.endsWith("/")) path.substring(0, path.length - 1)
    else path
  }
}
