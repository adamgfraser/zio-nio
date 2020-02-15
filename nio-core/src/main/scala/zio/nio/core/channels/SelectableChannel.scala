package zio.nio.core.channels

import java.io.IOException
import java.net.{ ServerSocket => JServerSocket, Socket => JSocket }
import java.nio.channels.{
  SelectableChannel => JSelectableChannel,
  ServerSocketChannel => JServerSocketChannel,
  SocketChannel => JSocketChannel
}
import java.nio.{ ByteBuffer => JByteBuffer }

import zio.nio.core.channels.SelectionKey.Operation
import zio.nio.core.channels.spi.SelectorProvider
import zio.nio.core.{ Buffer, SocketAddress, SocketOption }
import zio.{ IO, UIO }

trait SelectableChannel extends Channel {
  protected val channel: JSelectableChannel

  final val provider: UIO[SelectorProvider] =
    IO.effectTotal(new SelectorProvider(channel.provider()))

  final val validOps: UIO[Set[Operation]] =
    IO.effectTotal(channel.validOps())
      .map(Operation.fromInt(_))

  final val isRegistered: UIO[Boolean] =
    IO.effectTotal(channel.isRegistered())

  final def keyFor(sel: Selector): UIO[Option[SelectionKey]] =
    IO.effectTotal(Option(channel.keyFor(sel.selector)).map(new SelectionKey(_)))

  final def register(sel: Selector, ops: Set[Operation], att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops), att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, ops: Set[Operation]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, Operation.toInt(ops))))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation, att: Option[AnyRef]): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op.intVal, att.orNull)))
      .refineToOrDie[IOException]

  final def register(sel: Selector, op: Operation): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op.intVal)))
      .refineToOrDie[IOException]

  final def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  final val isBlocking: UIO[Boolean] =
    IO.effectTotal(channel.isBlocking())

  final val blockingLock: UIO[AnyRef] =
    IO.effectTotal(channel.blockingLock())
}

final class SocketChannel(override protected[channels] val channel: JSocketChannel)
    extends SelectableChannel
    with GatheringByteChannel
    with ScatteringByteChannel {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineToOrDie[Exception].unit

  final val shutdownInput: IO[IOException, Unit] =
    IO.effect(channel.shutdownInput()).refineToOrDie[IOException].unit

  final val shutdownOutput: IO[IOException, Unit] =
    IO.effect(channel.shutdownOutput()).refineToOrDie[IOException].unit

  final val socket: UIO[JSocket] =
    IO.effectTotal(channel.socket())

  final val isConnected: UIO[Boolean] =
    IO.effectTotal(channel.isConnected)

  final val isConnectionPending: UIO[Boolean] =
    IO.effectTotal(channel.isConnectionPending)

  final def connect(remote: SocketAddress): IO[IOException, Boolean] =
    IO.effect(channel.connect(remote.jSocketAddress)).refineToOrDie[IOException]

  final val finishConnect: IO[IOException, Boolean] =
    IO.effect(channel.finishConnect()).refineToOrDie[IOException]

  final val remoteAddress: IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.getRemoteAddress())).refineToOrDie[IOException]

  final def read(b: Buffer[Byte]): IO[IOException, Int] =
    IO.effect(channel.read(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final def write(b: Buffer[Byte]): IO[Exception, Int] =
    IO.effect(channel.write(b.buffer.asInstanceOf[JByteBuffer])).refineToOrDie[IOException]

  final val localAddress: IO[IOException, Option[SocketAddress]] =
    IO.effect(Option(channel.getLocalAddress()).map(new SocketAddress(_)))
      .refineToOrDie[IOException]
}

object SocketChannel {

  final def fromJava(javaSocketChannel: JSocketChannel): SocketChannel =
    new SocketChannel(javaSocketChannel)

  final val open: IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open())).refineToOrDie[IOException]

  final def open(remote: SocketAddress): IO[IOException, SocketChannel] =
    IO.effect(new SocketChannel(JSocketChannel.open(remote.jSocketAddress))).refineToOrDie[IOException]
}

final class ServerSocketChannel(override protected val channel: JServerSocketChannel) extends SelectableChannel {

  final def bind(local: SocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress)).refineToOrDie[IOException].unit

  final def bind(local: SocketAddress, backlog: Int): IO[IOException, Unit] =
    IO.effect(channel.bind(local.jSocketAddress, backlog)).refineToOrDie[IOException].unit

  final def setOption[T](name: SocketOption[T], value: T): IO[Exception, Unit] =
    IO.effect(channel.setOption(name.jSocketOption, value)).refineToOrDie[Exception].unit

  final val socket: UIO[JServerSocket] =
    IO.effectTotal(channel.socket())

  /**
   * Accepts a socket connection.
   *
   * Not you must manually manage the lifecyle of the returned socket, calling `close` when you're finished with it.
   *
   * @return None if this socket is in non-blocking mode and no connection is currently available to be accepted.
   */
  final def accept: IO[IOException, Option[SocketChannel]] =
    IO.effect(Option(channel.accept()).map(new SocketChannel(_))).refineToOrDie[IOException]

  final val localAddress: IO[IOException, SocketAddress] =
    IO.effect(new SocketAddress(channel.getLocalAddress())).refineToOrDie[IOException]
}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException]

  def fromJava(javaChannel: JServerSocketChannel): ServerSocketChannel =
    new ServerSocketChannel(javaChannel)
}
