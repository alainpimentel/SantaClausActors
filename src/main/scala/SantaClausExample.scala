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
  makeSantaWork(numReindeer = 9, numElves = 10)

  case object Order

  sealed trait Helper extends Actor {
    import ExecutionContext.Implicits.global

    val id: Int
    val job: Unit

    def randomDelay = math.random * 10000 toLong

    def receive = {
      case Order =>
        job;
        context.system.scheduler.scheduleOnce(Duration.create(randomDelay: Long, TimeUnit.MILLISECONDS), sender, IdleElf(self))
    }
  }

  class Elf(val id: Int) extends Helper {
    val job = println("elf %s meeting in the study." format(id))
  }

  class Reindeer(val id: Int) extends Helper {
    val job = println("Reindeer %s delivering toys." format(id))
  }

  sealed trait NorthPoleMessage
  case object Jolly extends NorthPoleMessage
  case object Work extends NorthPoleMessage
  case class loop(elves: Group[Elf], reindeers: Group[Reindeer]) extends NorthPoleMessage
  case class KnockKnock[T <% Helper](group: Group[T]) extends NorthPoleMessage




  def makeSantaWork(numReindeer: Int, numElves: Int): Unit = {
    val system = ActorSystem("SantaClausProblem")
    val santa = system.actorOf(Props(
      new Santa()),
      name = "santa")
    //santa ! Jolly
    val secretary = system.actorOf(Props(
      new Secretary(numElves, numReindeer, santa)),
      name = "secretary")


    for (i <- 1 until numReindeer + 1) {
      val reindeerWorker = system.actorOf(Props(
        new Reindeer(i)),
        name = "reindeer-" + i.toString )
      //reindeerWorker ! Work
      secretary ! IdleReindeer(reindeerWorker)
    }

    for (i <- 1 until numElves + 1) {
      val elfWorker = system.actorOf(Props(
        new Elf(i)),
        name = "elf-" + i.toString )
      // ! Work
      secretary ! IdleElf(elfWorker)
    }
  }


  case class Group[ActorRef](capacity: Int, helpers: List[ActorRef] = List()) {
    def hasSpace = helpers.size < capacity
  }

  case class IdleReindeer(reindeer: ActorRef)
  case class IdleElf(elf: ActorRef)

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


    def giveOrderAndWait(helpers: List[ActorRef]) = {
      println("\r\nHo! Ho! Ho! Let's %s!" format(
        if(helpers.isInstanceOf[List[Reindeer]]) "deliver toys"
        else "meet in my study"))
      implicit val timeout = Timeout(2.seconds)
      val futures: Seq[Future[Any]] = helpers map {  _ ask Order }
      Future sequence futures onComplete {_ => self ! Jolly}
//      val futuresSeq:Future[Seq[Any]] = Future sequence futures
//      val x:Timeout = Timeout(2000)
//      val seqOfFutures:TraversableOnce[Future[Any]] = Seq(futuresSeq, Timeout(2000))
//      val timeout =
//        akka.pattern.after(FiniteDuration.apply(2000, TimeUnit.MILLISECONDS), using = context.system.scheduler) {
//          Future.successful(Unit) }
//      val futureWithTimeout =
//        Future firstCompletedOf Seq(Future sequence futures onComplete {_ => self ! Jolly}, timeout);
//      futureWithTimeout onComplete { _ => self ! Jolly }
//      val result: Future[Seq[Option[Helper]]] = Future.sequence(futureWithTimeout)
//      result.onSuccess{ case _ => self ! Jolly }

    }

    def waiting: Receive = {
      case Jolly => { unstashAll(); context.unbecome() }
      case _ => stash()
    }

    context.setReceiveTimeout(0 milliseconds)
    def receive = {
      case ReceiveTimeout =>
        context.become(({
          case Group(_, helpers: List[ActorRef]) => giveOrderAndWait(helpers)}: Receive))
      case Group(9, helpers: List[ActorRef]) => giveOrderAndWait(helpers)
      case Jolly =>
        context.become(waiting, discardOld = false)
    }
  }


}


