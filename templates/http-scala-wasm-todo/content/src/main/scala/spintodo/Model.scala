package spintodo

import org.typelevel.jawn.ast.JValue

final case class Todo(id: Long, title: String, completed: Boolean, createdAt: String)

final case class JsonResponse(status: Int, body: JValue)

final case class RequestError(status: Int, message: String)
    extends RuntimeException(message)
