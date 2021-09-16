package com.robertmaples

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._

class DeviceGroupQuerySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DeviceManager._
  import DeviceGroupQuery.WrappedRespondTemperature

  "Device group query" must {
    "return temperature value for working devices" in {
      val requester = createTestProbe[RespondAllTemperatures]()

      val device1 = createTestProbe[Device.Command]()
      val device2 = createTestProbe[Device.Command]()

      val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

      val queryActor =
        spawn(DeviceGroupQuery(deviceIdToActor, requestId = 0, requester.ref, 3.seconds))

      device1.expectMessageType[Device.ReadTemperature]
      device2.expectMessageType[Device.ReadTemperature]

      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 1, "device1", Some(1.0))
      )
      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 2, "device2", Some(2.0))
      )

      val replies = Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0))
      requester.expectMessage(RespondAllTemperatures(requestId = 0, replies))
    }

    "return TemperatureNotAvailable for devices with no readings" in {
      val requester = createTestProbe[RespondAllTemperatures]()

      val device1 = createTestProbe[Device.Command]()
      val device2 = createTestProbe[Device.Command]()

      val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

      val queryActor =
        spawn(DeviceGroupQuery(deviceIdToActor, requestId = 0, requester.ref, 3.seconds))

      device1.expectMessageType[Device.ReadTemperature]
      device2.expectMessageType[Device.ReadTemperature]

      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 1, "device1", Some(1.0))
      )
      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 2, "device2", None)
      )

      val replies = Map("device1" -> Temperature(1.0), "device2" -> TemperatureNotAvailable)
      requester.expectMessage(RespondAllTemperatures(requestId = 0, replies))
    }

    "return DeviceNotAvailable if device stops before answering" in {
      val requester = createTestProbe[RespondAllTemperatures]()

      val device1 = createTestProbe[Device.Command]()
      val device2 = createTestProbe[Device.Command]()

      val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

      val queryActor =
        spawn(DeviceGroupQuery(deviceIdToActor, requestId = 0, requester.ref, 3.seconds))

      device1.expectMessageType[Device.ReadTemperature]
      device2.expectMessageType[Device.ReadTemperature]

      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 1, "device1", Some(1.0))
      )
      device2.stop()

      val replies = Map("device1" -> Temperature(1.0), "device2" -> DeviceNotAvailable)
      requester.expectMessage(RespondAllTemperatures(requestId = 0, replies))
    }

    "return temperature reading even if device stops after answering" in {
      val requester = createTestProbe[RespondAllTemperatures]()

      val device1 = createTestProbe[Device.Command]()
      val device2 = createTestProbe[Device.Command]()

      val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

      val queryActor =
        spawn(DeviceGroupQuery(deviceIdToActor, requestId = 0, requester.ref, 3.seconds))

      device1.expectMessageType[Device.ReadTemperature]
      device2.expectMessageType[Device.ReadTemperature]

      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 1, "device1", Some(1.0))
      )
      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 2, "device2", Some(2.0))
      )
      device2.stop()

      val replies = Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0))
      requester.expectMessage(RespondAllTemperatures(requestId = 0, replies))
    }

    "return DeviceTimedOut if device does not answer in time" in {
      val requester = createTestProbe[RespondAllTemperatures]()

      val device1 = createTestProbe[Device.Command]()
      val device2 = createTestProbe[Device.Command]()

      val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

      val queryActor =
        spawn(DeviceGroupQuery(deviceIdToActor, requestId = 0, requester.ref, 200.millis))

      device1.expectMessageType[Device.ReadTemperature]
      device2.expectMessageType[Device.ReadTemperature]

      queryActor ! WrappedRespondTemperature(
        Device.RespondTemperature(requestId = 1, "device1", Some(1.0))
      )

      val replies = Map("device1" -> Temperature(1.0), "device2" -> DeviceTimedOut)
      requester.expectMessage(RespondAllTemperatures(requestId = 0, replies))
    }
  }
}
