package sh.ano.yasfil

import scala.util._
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

object Client extends App {
  implicit val system = ActorSystem("IrcClient")
  implicit val materializer = ActorMaterializer()
  // TODO: Need to create constructor or some sort
  val connection = Tcp().outgoingConnection("irc.darenet.org", 6660)

  /**
    * Reads/writes byte strings from/to the upstream.
    * Writes/reads typed IRC commands to/from the downstream.
    */
  def serialization: BidiFlow[ByteString, IrcCommand, IrcCommand, ByteString, NotUsed] = {
    val read = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\r\n"), 65536))
      .map(IrcCommand.read)
      .mapConcat {
      case Success(cmd) => cmd :: Nil
      case Failure(cause) => Nil
    }

    val write = Flow[IrcCommand]
      .map(IrcCommand.write)
      .map(_ ++ ByteString("\r\n"))

    BidiFlow.fromFlows(read, write)
  }

  /**
    * Reads/writes IRC commands on all its ends.
    * Logs everything to console
    */
  def logging: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    def logger(prefix: String) = (cmd: IrcCommand) => {
      println(prefix + IrcCommand.write(cmd).utf8String)
      cmd
    }

    BidiFlow.fromFunctions(logger("> "), logger("< "))
  }

  // raw tcp stream
  // Flow[ByteString, ByteString, Future[OutgoingConnection]]
  connection
    // with (de)serialization
    // Flow[IrcCommand, IrcCommand, Future[OutgoingConnection]]
    .join(serialization)
    // with logging
    // Flow[IrcCommand, IrcCommand, Future[OutgoingConnection]]
    .join(logging)
    // combine input with output, while just listening and not saying anything
    // RunnableGraph[Future[OutgoingConnection]]
    .join(Flow[IrcCommand].filter(_ => false))
    // Future[OutgoingConnection]
    .run()
}
