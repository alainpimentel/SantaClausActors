import akka.actor.Actor

import scala.concurrent.Future



val helpers = List(1,2,3,4)
helpers.head
val h = helpers map{ _ + 1 toDouble}
helpers

val futures: Seq[Future[Int]] = ???
