# SPEC-004 — Pekko Device Farm Simulator

**Project:** Comanche  
**Description:** AI-Assisted AIOps and Operational Visibility Platform for IoT Devices  
**Reference Organization:** Davy Jones Burger  
**Status:** Draft  
**Version:** 0.2  
**Type:** Simulator Design Specification  
**Depends On:** SPEC-001, SPEC-002, SPEC-003  
**Related Specifications:** SPEC-005 (MQTT), SPEC-006 (Kafka), SPEC-007 (Detection and Incidents)

---

## 1. Purpose

This specification defines the design, responsibilities, runtime behaviour, fault simulation, configuration, and acceptance criteria for the Apache Pekko-based IoT Device Farm Simulator.

The simulator SHALL reproduce the observable behaviour of self-ordering kiosks and POS terminals operating across multiple Davy Jones Burger outlets.

It SHALL provide a controlled, reproducible environment for testing and demonstrating Comanche's monitoring, incident detection, AI investigation, and human-approved remediation workflows.

The simulator is an external system, not part of the Comanche operational platform.

---

## 2. Objectives

The simulator SHALL:

1. Simulate multiple independently operating IoT devices.
2. Generate realistic device telemetry.
3. Simulate customer ordering activity.
4. Model lunch, dinner, and off-peak workload variations.
5. Support progressive technical degradation.
6. Support deterministic fault injection.
7. Communicate with Comanche exclusively through MQTT.
8. Receive authorized device commands.
9. Simulate command execution and recovery.
10. Support reproducible demonstration scenarios.

The simulator SHALL NOT perform Comanche's monitoring, abnormality detection, AI investigation, or incident management responsibilities.

---

## 3. Architectural Boundary

The simulator SHALL be implemented as a separate Java application using Apache Pekko.

Recommended module name:

`comanche-device-simulator`

### 3.1 External Communication

```text
+--------------------------------------+
|        Pekko Device Simulator        |
|                                      |
|  KioskActor  KioskActor  PosActor     |
|      |           |          |        |
|      +-----------+----------+        |
|                  |                   |
|             MQTT Adapter             |
+------------------+-------------------+
                   |
                   | MQTT
                   v
+--------------------------------------+
|          Eclipse Mosquitto           |
+------------------+-------------------+
                   |
                   v
+--------------------------------------+
|         Comanche IoT Gateway         |
+--------------------------------------+
```

### 3.2 Isolation Requirements

**REQ-SIM-001**  
The simulator SHALL be independently deployable.

**REQ-SIM-002**  
Comanche platform services SHALL NOT depend on Pekko libraries.

**REQ-SIM-003**  
Simulator actors SHALL NOT directly invoke Comanche backend services.

**REQ-SIM-004**  
The simulator SHALL NOT access Comanche's PostgreSQL database.

**REQ-SIM-005**  
The simulator SHALL NOT publish directly to Kafka.

**REQ-SIM-006**  
All device-facing communication SHALL use the MQTT contracts defined in SPEC-005.

---

## 4. Actor Architecture

The simulator SHALL use Apache Pekko Typed Actors.

### 4.1 Actor Hierarchy

```text
ActorSystem
    |
    +-- DeviceFarmSupervisor
           |
           +-- KioskActor
           +-- KioskActor
           +-- KioskActor
           |
           +-- PosTerminalActor
           +-- PosTerminalActor
           |
           +-- MqttBridgeActor
           |
           +-- ScenarioController
```

The hierarchy is conceptual. MQTT transport and scenario orchestration MAY be implemented using dedicated actors or supporting application components.

### 4.2 Device Actors

Each simulated physical device SHALL have one primary device actor.

The actor SHALL own the mutable simulation state of that device.

No other device actor SHALL directly mutate its state.

### 4.3 Site Representation

A Site SHALL initially be a configuration and grouping concept.

A SiteActor is NOT required for the MVP.

A SiteActor MAY be introduced later only when site-level simulation behaviour justifies it.

---

## 5. Actor Responsibilities

### 5.1 DeviceFarmSupervisor

The DeviceFarmSupervisor SHALL:

- Load validated simulator configuration.
- Create configured device actors.
- Associate devices with sites.
- Coordinate simulator startup.
- Coordinate graceful shutdown.
- Report simulator-level failures.
- Apply the configured simulation seed.

It SHALL NOT own the operational state of individual devices.

### 5.2 KioskActor

Each KioskActor SHALL:

- Maintain kiosk technical state.
- Maintain kiosk operational state.
- Maintain printer state.
- Maintain application and network state.
- Simulate customer ordering sessions.
- Generate telemetry.
- Generate device events.
- Process internal simulation ticks.
- Apply configured fault scenarios.
- Receive authorized device commands.
- Simulate remediation effects.

### 5.3 PosTerminalActor

Each PosTerminalActor SHALL:

- Maintain POS technical state.
- Maintain POS operational state.
- Maintain printer state where applicable.
- Maintain payment-terminal status.
- Maintain application and network state.
- Simulate POS activity.
- Generate telemetry.
- Process internal ticks.
- Apply supported fault scenarios.
- Process authorized commands.

### 5.4 MQTT Bridge

The MQTT bridge SHALL:

- Establish MQTT connectivity.
- Publish telemetry and device events.
- Subscribe to configured command topics.
- Validate incoming command envelopes.
- Route commands to the correct device actor.
- Publish command acknowledgements and outcomes.
- Handle broker reconnects.

The MQTT bridge SHALL NOT authorize remediation.

### 5.5 ScenarioController

The ScenarioController SHALL:

- Load predefined scenarios.
- Schedule fault injection.
- Target configured devices.
- Support reproducible demonstration runs.
- Report scenario progress.

Scenario-control messages SHALL remain internal to the simulator.

---

## 6. Device Actor State

Each device actor SHALL maintain independent state.

### 6.1 Common State

Candidate attributes:

```text
deviceId
siteId
deviceType

operationalState
applicationStatus
networkStatus

cpuUtilization
memoryUtilization

currentFaults
lastTick
simulationTime

randomGenerator
```

### 6.2 Kiosk-Specific State

```text
printerTemperature
printerErrorRate
printerPaperLevel
printerStatus

touchscreenStatus

currentOrderSession
```

### 6.3 POS-Specific State

```text
paymentTerminalStatus
printerStatus
printerPaperLevel

currentTransactionSession
```

### 6.4 State Ownership

**REQ-SIM-007**  
Each device actor SHALL own its simulation state.

**REQ-SIM-008**  
Device actors SHALL communicate through messages rather than shared mutable state.

**REQ-SIM-009**  
Device state transitions SHALL be deterministic for a given configuration, random seed, and ordered input sequence.

---

## 7. Actor Messages

The simulator SHOULD define typed internal messages.

### 7.1 Common Messages

```java
public sealed interface DeviceMessage {

    record Tick(long simulationTick)
            implements DeviceMessage {}

    record InjectFault(FaultType faultType)
            implements DeviceMessage {}

    record ClearFault(FaultType faultType)
            implements DeviceMessage {}

    record ExecuteCommand(DeviceCommand command)
            implements DeviceMessage {}

    record GetSnapshot(
            ActorRef<DeviceSnapshot> replyTo)
            implements DeviceMessage {}
}
```

This is an illustrative contract; exact Java types and message organization SHALL be finalized during implementation.

### 7.2 Message Rules

- Tick messages advance simulated behaviour.
- InjectFault activates a configured fault.
- ClearFault removes a simulator-controlled fault.
- ExecuteCommand represents a command received through the MQTT boundary.
- GetSnapshot supports internal simulator diagnostics.

### 7.3 Internal-Only Messages

The following messages SHALL NOT be exposed as Comanche device-control APIs:

```text
InjectFault
ClearFault
SetPrinterTemperature
ForceDeviceFailure
AdvanceSimulationClock
```

These are simulator controls, not physical-device commands.

---

## 8. Simulation Clock

The simulator SHALL use a configurable tick-based execution model.

### 8.1 Configuration

Candidate parameters:

```yaml
simulation:
  tick-interval-ms: 1000
  telemetry-interval-ticks: 5
  time-scale: 1.0
  random-seed: 20261008
```

### 8.2 Tick Processing

On each tick, a device actor SHALL:

1. Advance its simulation time.
2. Evaluate current workload.
3. Update operational state.
4. Apply active fault effects.
5. Update telemetry measurements.
6. Evaluate pending command effects.
7. Emit required events.
8. Publish telemetry when due.

### 8.3 Time Scale

The simulator MAY support accelerated simulation time.

If accelerated time is implemented, message timestamps SHALL remain consistent with the simulation clock.

The MVP MAY use real-time simulation only.

### 8.4 Tick Ordering

A device actor SHALL process messages sequentially through its mailbox.

Deterministic test scenarios SHALL control tick and fault-injection ordering.

---

## 9. Randomness and Reproducibility

The simulator SHALL support configurable seeded randomness.

### 9.1 Seed Derivation

Each device SHOULD use a deterministic random stream derived from:

```text
globalSeed + deviceId
```

A stable hashing or seed-derivation algorithm SHALL be used.

The implementation SHALL NOT rely on Java's process-dependent object identity or scheduling order to derive seeds.

### 9.2 Reproducibility

For identical:

- Configuration.
- Random seed.
- Initial state.
- Ordered tick sequence.
- Ordered injected events.
- Command sequence.

the simulator SHALL produce equivalent device-state transitions and telemetry values.

Wall-clock scheduling jitter and external MQTT delivery timing MAY vary.

### 9.3 Testing

Deterministic tests SHOULD use controlled ticks rather than real-time sleeps.

---

## 10. Workload Simulation

The simulator SHALL model realistic customer activity without implementing a complete restaurant ordering system.

### 10.1 Workload Profiles

Each Site SHOULD support configurable workload periods:

```text
OFF_PEAK
LUNCH_PEAK
DINNER_PEAK
```

### 10.2 Customer Arrival Probability

The simulator MAY use configurable customer-arrival probabilities.

Example:

| Period | Example Arrival Probability Per Eligible Tick |
|---|---:|
| Off-peak | 0.05 |
| Lunch peak | 0.35 |
| Dinner peak | 0.30 |

These are illustrative values, not fixed business requirements.

### 10.3 Order Session

A kiosk SHALL simulate the following normal lifecycle:

```text
IDLE
  |
  v
TAKING_ORDER
  |
  v
PROCESSING_PAYMENT
  |
  v
COMPLETING_ORDER
  |
  v
IDLE
```

### 10.4 Session Durations

Candidate configurable durations:

| State | Example Duration |
|---|---|
| TAKING_ORDER | 20–90 seconds |
| PROCESSING_PAYMENT | 3–15 seconds |
| COMPLETING_ORDER | 2–10 seconds |

Durations MAY be sampled from deterministic seeded distributions.

### 10.5 Operational State Events

The simulator SHALL publish operational-state changes.

The Operations Service SHALL use these events to derive time-based utilization.

### 10.6 Workload Constraints

**REQ-SIM-010**  
A device SHALL NOT start a second customer session while an existing session is active.

**REQ-SIM-011**  
An OUT_OF_SERVICE device SHALL NOT start a customer session.

**REQ-SIM-012**  
A degraded device MAY continue serving customers when its current fault does not prevent operation.

**REQ-SIM-013**  
Workload intensity SHALL be configurable by site and time period.

---

## 11. Telemetry Generation

Each device actor SHALL generate telemetry reflecting its current simulated condition.

### 11.1 Common Metrics

Candidate metrics:

```text
system.cpu.utilization
system.memory.utilization
system.temperature

application.status
network.status
```

### 11.2 Kiosk Metrics

```text
printer.temperature
printer.errorRate
printer.paperLevel
printer.status

touchscreen.status
```

### 11.3 POS Metrics

```text
paymentTerminal.status
printer.status
printer.paperLevel
```

### 11.4 Normal Variation

Normal telemetry SHOULD exhibit bounded variation.

Example:

```text
Printer temperature:
47°C → 49°C → 48°C → 51°C
```

Telemetry SHALL NOT remain unrealistically constant unless the metric is naturally static.

### 11.5 Correlated Measurements

Related measurements SHOULD behave consistently.

Examples:

- Higher activity MAY increase CPU utilization.
- Higher printer usage MAY increase printer temperature.
- Printing MAY reduce paper level.
- Printer degradation MAY increase printer error rate.
- Application failure SHALL affect application status.

### 11.6 Telemetry Frequency

Telemetry publication frequency SHALL be configurable.

The simulator SHOULD support lower publication frequencies for larger device farms.

---

## 12. Fault Model

The simulator SHALL support fault scenarios affecting device behaviour over time.

### 12.1 FaultType

Candidate values:

```java
public enum FaultType {
    PRINTER_OVERHEATING,
    PRINTER_ERROR_INCREASE,
    PAPER_DEPLETION,
    APPLICATION_FAILURE,
    NETWORK_DISCONNECTION,
    PAYMENT_TERMINAL_FAILURE
}
```

### 12.2 Fault Lifecycle

A fault MAY transition through:

```text
INACTIVE
   |
   v
DEVELOPING
   |
   v
ACTIVE
   |
   v
RECOVERING
   |
   v
CLEARED
```

### 12.3 Progressive Faults

Progressive faults SHALL modify telemetry over multiple ticks.

The simulator SHALL NOT merely publish a single abnormal value and immediately reset it.

### 12.4 Fault Persistence

A fault SHALL remain active until:

- Its configured scenario completes.
- A valid remediation changes its state.
- An internal simulator-control event clears it.

The simulator SHALL NOT automatically clear every fault after a fixed duration.

---

## 13. Primary Fault Scenario — Printer Degradation

The primary demonstration scenario SHALL simulate progressive receipt-printer degradation.

### 13.1 Initial State

```text
Device:
KIOSK-TAM-003

Application:
RUNNING

Network:
CONNECTED

Printer:
NORMAL

Operational State:
IDLE or actively serving customers
```

### 13.2 Temperature Progression

Example:

```text
51°C
  ↓
58°C
  ↓
65°C
  ↓
72°C
```

### 13.3 Error Rate Progression

Example:

```text
1%
 ↓
4%
 ↓
10%
 ↓
19%
```

### 13.4 Expected Behaviour

The simulator SHALL:

1. Start from normal printer conditions.
2. Activate a printer degradation fault.
3. Increase printer temperature progressively.
4. Increase printer error rate.
5. Continue publishing telemetry.
6. Continue customer activity while the fault permits.
7. Transition to OUT_OF_SERVICE if the configured fault reaches a disabling condition.
8. Await valid remediation or scenario intervention.

### 13.5 Monitoring Responsibility

The simulator SHALL NOT create Comanche alerts or incidents.

Comanche's deterministic detection engine SHALL detect abnormal conditions independently.

### 13.6 Recovery

After receiving a valid `RESTART_PRINTER_SUBSYSTEM` command, the simulator SHALL apply a configured remediation outcome.

For the successful demonstration path:

- Printer subsystem enters a recovering state.
- Printer temperature decreases.
- Printer error rate decreases.
- Printer status returns to NORMAL.
- Operational state MAY return to IDLE.
- Subsequent telemetry reflects the recovery.

The simulator SHALL NOT directly mark the Comanche incident as resolved.

---

## 14. Other Fault Scenarios

### 14.1 Paper Depletion

Paper level SHALL decrease as simulated printing occurs.

When paper level reaches a configured critical threshold, the simulator MAY emit a low-paper event.

At zero paper level, printing SHOULD fail.

A printer restart SHALL NOT replenish paper.

### 14.2 Application Failure

The application SHALL transition to STOPPED or FAILED.

Customer activity SHALL stop.

A supported application restart command MAY restore operation.

### 14.3 Network Disconnection

The device SHALL simulate loss of MQTT connectivity.

While disconnected, the device MAY continue local simulation activity.

The platform SHALL determine connectivity loss through its own monitoring rules.

### 14.4 Payment Terminal Failure

The payment terminal SHALL become unavailable.

The simulator SHALL model the effect on payment processing and customer-session completion.

### 14.5 Fault Independence

Multiple faults MAY coexist where practical.

The MVP MAY restrict complex overlapping fault scenarios to reduce implementation complexity.

---

## 15. MQTT Publication

The simulator SHALL publish device telemetry, status, and events using SPEC-005 contracts.

Candidate topic families:

```text
comanche/devices/{deviceId}/telemetry

comanche/devices/{deviceId}/status

comanche/devices/{deviceId}/events
```

The exact topic names SHALL be governed by SPEC-005.

### 15.1 Publication Rules

**REQ-SIM-014**  
Published messages SHALL contain stable device identity.

**REQ-SIM-015**  
Published messages SHALL contain event timestamps.

**REQ-SIM-016**  
Published messages SHALL use versioned payload schemas.

**REQ-SIM-017**  
The simulator SHALL NOT publish Comanche internal Kafka events.

---

## 16. MQTT Command Handling

The simulator SHALL subscribe to its configured command topics.

Candidate topic:

```text
comanche/devices/{deviceId}/commands
```

### 16.1 Supported Commands

The MVP SHOULD support:

```text
RESTART_PRINTER_SUBSYSTEM

RESTART_APPLICATION
```

Additional commands MAY be introduced through explicit specifications.

### 16.2 Command Processing

On receiving a command, the simulator SHALL:

1. Validate the command envelope.
2. Validate the target device.
3. Validate the command type.
4. Check command duplication.
5. Route the command to the device actor.
6. Execute the supported simulated behaviour.
7. Publish a command acknowledgement or result.

### 16.3 Command Identity

Every command SHALL include a unique commandId.

The simulator SHALL maintain sufficient recent command history to avoid executing duplicate commands within the configured deduplication window.

### 16.4 Command Outcome

Candidate outcomes:

```text
ACCEPTED
REJECTED
SUCCEEDED
FAILED
```

A command acknowledgement SHALL NOT imply that Comanche has verified device recovery.

### 16.5 Security Boundary

The simulator SHALL NOT accept AI investigation output as an executable command.

Only messages received through the configured device-command MQTT contract SHALL be considered device commands.

MQTT broker authorization SHALL restrict command publication to the authorized Comanche gateway identity.

---

## 17. MQTT Connectivity

The simulator SHALL support broker connection management.

### 17.1 Reconnection

On MQTT disconnection, the MQTT adapter SHALL attempt controlled reconnection.

### 17.2 Device Behaviour During Disconnection

Device actors MAY continue advancing their local simulation state.

The simulator SHALL NOT falsely publish telemetry while disconnected.

### 17.3 Message Buffering

The MVP MAY use bounded outbound buffering.

Buffer limits and overflow behaviour SHALL be configurable or explicitly documented.

The simulator SHALL NOT require unlimited in-memory message retention.

### 17.4 Command Delivery

Command delivery guarantees SHALL follow SPEC-005.

The simulator SHALL tolerate duplicate MQTT delivery.

---

## 18. Scenario Configuration

The simulator SHALL support configuration-driven scenarios.

### 18.1 Example Configuration

```yaml
simulation:
  tick-interval-ms: 1000
  telemetry-interval-ticks: 5
  random-seed: 20261008

sites:
  - site-id: SITE-TAMPINES
    name: Tampines
    timezone: Asia/Singapore

    devices:
      - device-id: KIOSK-TAM-001
        type: SELF_ORDER_KIOSK

      - device-id: KIOSK-TAM-002
        type: SELF_ORDER_KIOSK

      - device-id: KIOSK-TAM-003
        type: SELF_ORDER_KIOSK

      - device-id: POS-TAM-001
        type: POS_TERMINAL

scenarios:
  - scenario-id: PRINTER-DEMO-001
    device-id: KIOSK-TAM-003
    fault-type: PRINTER_OVERHEATING
    start-after-ticks: 60
    progression:
      temperature:
        - 51
        - 58
        - 65
        - 72
      error-rate:
        - 0.01
        - 0.04
        - 0.10
        - 0.19
```

This is an illustrative configuration, not a finalized configuration schema.

### 18.2 Configuration Validation

The simulator SHALL reject invalid configuration, including:

- Duplicate device IDs.
- Unknown device types.
- Invalid site references.
- Negative tick intervals.
- Invalid fault types.
- Invalid telemetry ranges.
- Fault scenarios targeting unknown devices.

---

## 19. Simulator Control Interface

The simulator MAY expose a local administrative control interface for demonstration and testing.

Candidate actions:

```text
Start Simulation
Stop Simulation
Pause Simulation
Resume Simulation
Inject Fault
Clear Fault
View Device Snapshot
```

This interface SHALL remain separate from Comanche's operational APIs.

It SHALL NOT be accessible to the AIOps agent.

For the MVP, scenario control MAY be provided through configuration or a command-line interface.

A dedicated simulator-control web UI is NOT required.

---

## 20. Logging and Diagnostics

The simulator SHOULD produce structured logs for:

- Simulator startup.
- Device actor creation.
- MQTT connection changes.
- Fault injection.
- Device state transitions.
- Command receipt.
- Command execution.
- Recovery transitions.
- Simulator shutdown.

Logs SHOULD include:

```text
deviceId
siteId
eventType
simulationTick
eventTime
```

The simulator SHOULD allow developers to inspect internal actor state without exposing internal controls to Comanche.

---

## 21. Performance and Scale

The simulator SHOULD support the following targets:

| Environment | Device Count |
|---|---:|
| Basic development | 5–10 |
| Capstone demonstration | 10–20 |
| Normal simulation | 50–100 |
| Optional scalability experiment | Approximately 1,000 |

### 21.1 Resource Efficiency

The simulator SHOULD:

- Avoid one operating-system thread per device.
- Use Pekko actor scheduling.
- Avoid blocking device actor message handlers.
- Use bounded queues where applicable.
- Reuse shared MQTT transport infrastructure where practical.

### 21.2 Backpressure

The MQTT adapter SHOULD avoid unbounded accumulation of outgoing telemetry.

If the broker cannot keep up, the simulator SHALL apply a documented bounded buffering or message-dropping policy.

Command outcomes SHOULD receive higher delivery priority than disposable high-frequency telemetry where feasible.

### 21.3 Scale Claims

The optional 1,000-device experiment SHALL NOT be treated as proof of production readiness.

---

## 22. Error Handling

### 22.1 Actor Failure

The DeviceFarmSupervisor SHALL apply an explicit supervision policy.

Unexpected actor failures SHALL be logged.

Actor restart behaviour SHALL be documented, including whether state is restored or reset.

### 22.2 Invalid Commands

Unsupported or malformed commands SHALL be rejected without changing device state.

### 22.3 MQTT Failure

The simulator SHALL attempt controlled reconnect and SHALL not terminate all device actors solely because MQTT becomes temporarily unavailable.

### 22.4 Invalid Scenarios

Invalid scenario configuration SHALL fail startup with a meaningful error.

### 22.5 Simulation Consistency

A failed command SHALL NOT automatically clear an active fault.

---

## 23. Testing Strategy

The simulator SHALL include automated tests.

### 23.1 Unit Tests

Tests SHOULD cover:

- Normal operational-state transitions.
- Customer-session lifecycle.
- Printer temperature progression.
- Printer error-rate progression.
- Paper depletion.
- Application failure.
- Network disconnection.
- Command validation.
- Command deduplication.
- Successful remediation.
- Failed remediation.

### 23.2 Actor Tests

Pekko actor tests SHOULD verify:

- Sequential message processing.
- Tick-driven state transitions.
- Fault injection.
- Snapshot retrieval.
- Command routing.
- Actor supervision where implemented.

### 23.3 Determinism Tests

Given identical configuration, seed, and ordered inputs, tests SHALL verify equivalent output state sequences.

Tests SHOULD NOT rely on uncontrolled real-time sleeps for deterministic behaviour.

### 23.4 MQTT Integration Tests

Integration tests SHOULD verify:

- Telemetry publication.
- Device event publication.
- Command subscription.
- Command acknowledgement.
- Broker reconnection.
- Duplicate command handling.

### 23.5 End-to-End Tests

An end-to-end test SHOULD demonstrate:

```text
Pekko Kiosk
    ↓
MQTT Telemetry
    ↓
Comanche Gateway
    ↓
Kafka
    ↓
Operations Service
    ↓
Alert / Incident
```

A separate end-to-end test SHOULD demonstrate approved command execution and telemetry-based recovery verification.

---

## 24. Simulator Requirements Summary

**REQ-SIM-018**  
One primary Pekko actor SHALL represent one simulated device.

**REQ-SIM-019**  
Each device actor SHALL maintain independent technical and operational state.

**REQ-SIM-020**  
The simulator SHALL model normal kiosk ordering activity.

**REQ-SIM-021**  
Workload profiles SHALL support configurable peak and off-peak behaviour.

**REQ-SIM-022**  
Telemetry SHALL reflect simulated device state.

**REQ-SIM-023**  
Progressive faults SHALL modify telemetry across multiple ticks.

**REQ-SIM-024**  
The simulator SHALL support reproducible seeded scenarios.

**REQ-SIM-025**  
Fault injection SHALL remain an internal simulator capability.

**REQ-SIM-026**  
The simulator SHALL publish telemetry and events through MQTT.

**REQ-SIM-027**  
The simulator SHALL receive authorized commands through MQTT.

**REQ-SIM-028**  
Unsupported commands SHALL NOT modify device state.

**REQ-SIM-029**  
Duplicate commands SHALL NOT cause unintended repeated execution.

**REQ-SIM-030**  
A successful command SHALL NOT directly resolve a Comanche incident.

**REQ-SIM-031**  
The simulator SHALL support controlled MQTT reconnection.

**REQ-SIM-032**  
The simulator SHALL support configuration-driven sites and devices.

**REQ-SIM-033**  
The simulator SHALL support graceful shutdown.

**REQ-SIM-034**  
The simulator SHALL support automated actor and integration testing.

---

## 25. Acceptance Criteria

**AC-004-01**  
The simulator starts with a valid configuration and creates the expected number of device actors.

**AC-004-02**  
Each device actor maintains independent state.

**AC-004-03**  
A kiosk transitions through IDLE, TAKING_ORDER, PROCESSING_PAYMENT, COMPLETING_ORDER, and back to IDLE.

**AC-004-04**  
Lunch and dinner workload profiles produce different activity patterns from off-peak operation.

**AC-004-05**  
Normal telemetry exhibits bounded, realistic variation.

**AC-004-06**  
The simulator publishes telemetry to MQTT.

**AC-004-07**  
The simulator publishes operational-state changes.

**AC-004-08**  
A configured printer fault progressively increases temperature and error rate.

**AC-004-09**  
A degraded kiosk can continue serving customers when the fault permits.

**AC-004-10**  
A critical fault can transition a kiosk to OUT_OF_SERVICE.

**AC-004-11**  
The simulator does not create Comanche alerts or incidents directly.

**AC-004-12**  
A valid restart-printer command reaches the correct device actor.

**AC-004-13**  
The device publishes a command result.

**AC-004-14**  
Successful remediation causes subsequent telemetry to reflect recovery.

**AC-004-15**  
An unsupported command is rejected without changing device state.

**AC-004-16**  
Duplicate command delivery does not cause unintended repeated execution.

**AC-004-17**  
Identical seeded test runs produce equivalent state and telemetry sequences.

**AC-004-18**  
The simulator can reconnect after temporary MQTT broker disconnection.

**AC-004-19**  
The simulator runs independently of Comanche's Java service implementation.

**AC-004-20**  
A 10–20-device demonstration can run reliably on the developer machine.

---

## 26. Implementation Milestones

### Milestone 1 — Actor Foundation

Implement:

- Maven project.
- Pekko Typed ActorSystem.
- DeviceFarmSupervisor.
- KioskActor.
- PosTerminalActor.
- Configuration loading.
- Controlled tick scheduling.
- Actor tests.

**Deliverable:** Multiple devices operate independently in memory.

### Milestone 2 — Normal Operations

Implement:

- Device operational-state machine.
- Customer-session simulation.
- Peak/off-peak workload profiles.
- Normal telemetry generation.
- Seeded randomness.

**Deliverable:** Kiosks exhibit realistic ordering activity and telemetry.

### Milestone 3 — MQTT Integration

Implement:

- MQTT client adapter.
- Telemetry publication.
- Device event publication.
- Status publication.
- Command subscription.
- Reconnection handling.

**Deliverable:** Simulated devices appear through the MQTT boundary.

### Milestone 4 — Fault Scenarios

Implement:

- Printer degradation.
- Application failure.
- Network disconnection.
- Paper depletion.
- Scenario configuration.
- Deterministic fault tests.

**Deliverable:** Reproducible abnormal device behaviour.

### Milestone 5 — Remediation

Implement:

- MQTT command routing.
- Command validation.
- Command deduplication.
- Printer restart simulation.
- Application restart simulation.
- Command outcomes.
- Recovery telemetry.

**Deliverable:** A simulated device can respond to an authorized command and demonstrate recovery.

### Milestone 6 — End-to-End Validation

Integrate with:

- Mosquitto.
- IoT Gateway.
- Kafka.
- Operations Service.
- Detection Engine.
- Incident Management.

**Deliverable:** Complete printer degradation and recovery demonstration.

---

## 27. Open Design Decisions

The following implementation details remain to be finalized:

1. Exact Pekko version and Maven dependencies.
2. MQTT client library selection.
3. Actor supervision and state recovery policy.
4. Simulation clock implementation.
5. Final simulator configuration schema.
6. Exact telemetry publication intervals.
7. Device-specific metric ranges.
8. Command deduplication retention.
9. MQTT buffering and reconnect policy.
10. Fault overlap rules.
11. Simulator administrative control interface.
12. Performance-test methodology for larger device farms.

These decisions SHALL NOT expand the MVP beyond the scope established in SPEC-001.

---

## 28. Final Simulator Principle

The Pekko simulator SHALL behave like an external fleet of independently operating IoT devices.

It SHALL produce realistic telemetry, operational events, faults, and command responses through MQTT.

It SHALL NOT implement Comanche's detection, incident management, AI investigation, approval, or recovery-verification logic.

**Architectural statement:**

> Pekko simulates device behaviour. MQTT carries device messages. Comanche observes, detects, investigates, and manages incidents. Engineers authorize remediation. The simulator executes valid device commands and reports the resulting state.
