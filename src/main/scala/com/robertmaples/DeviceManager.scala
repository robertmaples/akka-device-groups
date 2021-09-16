package com.robertmaples

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Signal
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps

import com.robertmaples.{Device, DeviceGroup, DeviceGroupQuery}

object DeviceManager {
  sealed trait Command
  final case class RequestTrackDevice(
      groupId: String,
      deviceId: String,
      replyTo: ActorRef[DeviceRegistered]
  ) extends DeviceManager.Command
      with DeviceGroup.Command
  final case class DeviceRegistered(device: ActorRef[Device.Command])
  final case class RequestDeviceList(
      requestId: Long,
      groupId: String,
      replyTo: ActorRef[ReplyDeviceList]
  ) extends DeviceManager.Command
      with DeviceGroup.Command
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])
  private final case class DeviceGroupTerminated(groupId: String) extends DeviceManager.Command

  final case class RequestAllTemperatures(
      requestId: Long,
      groupId: String,
      replyTo: ActorRef[RespondAllTemperatures]
  ) extends DeviceGroupQuery.Command
      with DeviceGroup.Command
      with DeviceManager.Command
  final case class RespondAllTemperatures(
      requestId: Long,
      temperatures: Map[String, TemperatureReading]
  )

  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading

  def apply(): Behavior[Command] =
    Behaviors.setup(new DeviceManager(_))
}

class DeviceManager(context: ActorContext[DeviceManager.Command])
    extends AbstractBehavior[DeviceManager.Command](context) {
  import DeviceManager._

  private var groupIdToActor = Map.empty[String, ActorRef[DeviceGroup.Command]]

  context.log.info("DeviceManager started")

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case trackMsg @ RequestTrackDevice(groupId, _, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(ref) =>
            ref ! trackMsg
          case None =>
            context.log.info("Creating device group actor for {}", groupId)
            val groupActor = context.spawn(DeviceGroup(groupId), s"group-$groupId")
            context.watchWith(groupActor, DeviceGroupTerminated(groupId))
            groupActor ! trackMsg
            groupIdToActor += groupId -> groupActor
        }
        this

      case req @ RequestDeviceList(requestId, groupId, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(ref) => ref ! req
          case None      => replyTo ! ReplyDeviceList(requestId, Set.empty)
        }
        this

      case DeviceGroupTerminated(groupId) =>
        context.log.info("Device group actor for {} has been terminated", groupId)
        groupIdToActor -= groupId
        this
    }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = { case PostStop =>
    context.log.info("DeviceManager stopped")
    this
  }
}
