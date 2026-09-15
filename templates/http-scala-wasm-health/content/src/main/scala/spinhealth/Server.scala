package spinhealth

import scala.scalajs.wit.annotation.{WitExport, WitName, WitScope}
import spinhealth.wasi.http.types.{IncomingRequest, ResponseOutparam}

object Server {
  @WitExport(WitScope("wasi", "http", "incoming-handler", "0.2.0"), "handle")
  def handle(
      @WitName("request") request: IncomingRequest,
      @WitName("response-out") responseOut: ResponseOutparam): Unit =
    ServerHandler.handle(request, responseOut)
}
