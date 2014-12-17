import java.util.concurrent.TimeUnit

import akka.actor
import akka.actor._
import akka.dispatch.Futures
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.{FiniteDuration, Duration, DurationInt}



/**
 * Created by alain on 12/11/2014.
 */
object SantaClaus extends App {
  makeSantaWork(numReindeer = 9, numElves = 7)

  def makeSantaWork(numReindeer: Int, numElves: Int): Unit = {
    val system = ActorSystem("SantaClausProblem")
    val santa = system.actorOf(Props(
      new Santa()),
      name = "santa")

    val secretary = system.actorOf(Props(
      new Secretary(numElves, numReindeer, santa)),
      name = "secretary")

    for (i <- 1 until numElves + 1) {
      val elfWorker = system.actorOf(Props(
        new Elf(i, secretary)),
        name = "elf-" + i.toString )
      secretary ! IdleElf(elfWorker)
    }

    for (i <- 1 until numReindeer + 1) {
      val reindeerWorker = system.actorOf(Props(
        new Reindeer(i, secretary)),
        name = "reindeer-" + i.toString )
      secretary ! IdleReindeer(reindeerWorker)
    }
  }

  sealed trait NorthPoleMessage
  case object Jolly extends NorthPoleMessage
  case object OrderReindeer extends NorthPoleMessage
  case object OrderElf extends NorthPoleMessage
  case class IdleReindeer(reindeer: ActorRef) extends NorthPoleMessage
  case class IdleElf(elf: ActorRef) extends NorthPoleMessage
  case class Group[ActorRef](capacity: Int, helpers: List[ActorRef] = List()) extends NorthPoleMessage {
    def hasSpace = helpers.size < capacity
  }

  sealed trait Helper extends Actor {
    import ExecutionContext.Implicits.global

    val id: Int
    val job: Unit
    val secretary: ActorRef

    def randomDelay = math.random * 1000000 toLong

    def receive = {
      case OrderReindeer =>
        job;
        implicit val timeout = Timeout(randomDelay, TimeUnit.MILLISECONDS)
        val futures: Future[Any] =   secretary ask IdleReindeer(self) // This doesn't seem to do anything. Find a different way to create a future
        futures onComplete {_ => secretary ! IdleReindeer(self);println("Done with timeout %d: Reindeer", randomDelay)}
      case OrderElf =>
        job;
        implicit val timeout = Timeout(randomDelay, TimeUnit.MILLISECONDS)
        val futures: Future[Any] =   secretary ask IdleElf(self)
        futures onComplete {_ => secretary ! IdleElf(self);println("Done with timeout %d: Elf", randomDelay)}
    }
  }

  class Elf(val id: Int, val secretary: ActorRef) extends Helper {
    val job = println("elf %s meeting in the study." format(id))
  }

  class Reindeer(val id: Int, val secretary: ActorRef) extends Helper {
    val job = println("Reindeer %s delivering toys." format(id))
  }

  class Secretary(numElves: Int, numReindeer: Int, santaHandler: ActorRef) extends Actor {

    private def createBehavior(elves: Group[ActorRef], reindeers: Group[ActorRef]): Receive = {
      case IdleReindeer(reindeer: ActorRef) => context become createBehavior (elves, addToGroup(reindeer, reindeers))
      case IdleElf(elf: ActorRef) => context become createBehavior (addToGroup(elf, elves), reindeers)
    }

    private def addToGroup[ActorRef](helper: ActorRef, group: Group[ActorRef]) = {
      // Append helper to list
      val updatedGroup = group.copy(helpers = helper :: group.helpers)
      if (updatedGroup.hasSpace) updatedGroup else {
        // TODO FIx this
        println("waking santa")
        santaHandler ! updatedGroup
        group.copy(helpers = List[ActorRef]())
      }
    }

    def receive = createBehavior (Group[ActorRef](3), Group[ActorRef](9))
  }

  class Santa() extends Actor with Stash{
    import ExecutionContext.Implicits.global

    def giveOrderAndWaitReindeer(helpers: List[ActorRef]) = {
      println("\r\nHo! Ho! Ho! Let's deliver toys!")
      implicit val timeout = Timeout(30.seconds)
      val futures: Seq[Future[Any]] = helpers map {  _ ask OrderReindeer }
      Future sequence futures onComplete {_ => self ! Jolly}
    }

    def giveOrderAndWaitElf(helpers: List[ActorRef]) = {
      println("\r\nHo! Ho! Ho! Let's meet in the study!")
      implicit val timeout = Timeout(30.seconds)
      val futures: Seq[Future[Any]] = helpers map {  _ ask OrderElf }
      Future sequence futures onComplete {_ => self ! Jolly}
    }

    def waiting: Receive = {
      case Jolly => { unstashAll(); context.unbecome() }
      case _ => stash()
    }

    context.setReceiveTimeout(0 milliseconds)
    def receive = {
      case Group(9, helpers: List[ActorRef]) => giveOrderAndWaitReindeer(helpers)
      case ReceiveTimeout =>
        context.become(({
          case Group(9, helpers: List[ActorRef]) => giveOrderAndWaitReindeer(helpers)
          case Group(3, helpers: List[ActorRef]) => giveOrderAndWaitElf(helpers)}: Receive))
      case Jolly =>
        context.become(waiting, discardOld = false)
    }
  }
}