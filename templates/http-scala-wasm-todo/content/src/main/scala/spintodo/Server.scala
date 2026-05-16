package spintodo

import scala.scalajs.wasi.http.types._
import scala.scalajs.wit.annotation._

import spintodo.exports.wasi.http.IncomingHandler

@WitImplementation
object Server extends IncomingHandler {
  override def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit =
    ServerHandler.handle(request, outParam)
}
