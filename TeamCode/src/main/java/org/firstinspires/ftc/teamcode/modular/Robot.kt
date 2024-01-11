package org.firstinspires.ftc.teamcode.modular

import com.qualcomm.robotcore.hardware.DcMotor
import com.qualcomm.robotcore.hardware.DcMotorSimple
import com.qualcomm.robotcore.hardware.Gamepad
import com.qualcomm.robotcore.hardware.HardwareMap
import com.qualcomm.robotcore.hardware.Servo
import com.qualcomm.robotcore.hardware.TouchSensor
import org.firstinspires.ftc.robotcore.external.Telemetry
import org.firstinspires.ftc.robotcore.external.matrices.GeneralMatrixF
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.reflect.KMutableProperty1

typealias FloatButtonType = KMutableProperty1<Gamepad, Float>
typealias BooleanButtonType = KMutableProperty1<Gamepad, Boolean>

class Robot(val telemetry: Telemetry) {
    private var speedMultiplier = 1f
    private lateinit var armBaseMotor: DcMotor
    private lateinit var spindleDrive: DcMotor
    private lateinit var drivetrainMotors: Array<DcMotor>
    private lateinit var lclaw: Servo
    private lateinit var rclaw: Servo
    private lateinit var pitchWrist: Servo
    private lateinit var rollWrist: Servo
    private lateinit var armMotorStick: FloatButton
    private lateinit var wristPitchStick: FloatButton
    private lateinit var wristRollStick: FloatButton
    private lateinit var spindleExtendStick: FloatButton
    private lateinit var spindleRetractStick: FloatButton
    private lateinit var armBottom: TouchSensor
    private lateinit var drivetrainButtons: Array<FloatButton>
    private var currentGamepad1 = Gamepad()
    private var pastGamepad1 = Gamepad()
    private var currentGamepad2 = Gamepad()
    private var pastGamepad2 = Gamepad()
    private val registeredBooleanInputs = HashMap<GamepadButton, (BooleanState) -> Unit>()

    fun initialize(hardwareMap: HardwareMap) {
        val leftFront = hardwareMap.dcMotor["lfdrive"]
        val rightFront = hardwareMap.dcMotor["rfdrive"]
        val leftBack = hardwareMap.dcMotor["lbdrive"]
        val rightBack = hardwareMap.dcMotor["rbdrive"]
        this.armBaseMotor = hardwareMap.dcMotor["armbasedrive"]
        this.spindleDrive = hardwareMap.dcMotor["spindledrive"]
        this.pitchWrist = hardwareMap.servo["verticalwrist"]
        this.rollWrist = hardwareMap.servo["horizontalwrist"]
        this.lclaw = hardwareMap.servo["lclawservo"]
        this.rclaw = hardwareMap.servo["rclawservo"]
        this.armBottom = hardwareMap.touchSensor["armbottom"]
        this.drivetrainMotors = arrayOf(leftFront, rightFront, leftBack, rightBack)

        // faster than using encoders
        this.drivetrainMotors.forEach { it.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER }


        arrayOf(DcMotor.RunMode.STOP_AND_RESET_ENCODER, DcMotor.RunMode.RUN_USING_ENCODER).forEach {
            it.let {
                this.spindleDrive.mode = it
                this.armBaseMotor.mode = it
            }
        }

        // slows down faster when directional input is let off
        this.drivetrainMotors.forEach { it.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE }
        this.armBaseMotor.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE
        this.spindleDrive.zeroPowerBehavior = DcMotor.ZeroPowerBehavior.BRAKE

        // go forward on all pos inputs
        leftFront.direction = DcMotorSimple.Direction.REVERSE
        rightFront.direction = DcMotorSimple.Direction.FORWARD
        leftBack.direction = DcMotorSimple.Direction.REVERSE
        rightBack.direction = DcMotorSimple.Direction.FORWARD

        this.armBaseMotor.direction = DcMotorSimple.Direction.REVERSE
        this.spindleDrive.direction = DcMotorSimple.Direction.REVERSE

        this.setClaw(Claw.LEFT, ClawState.CLOSED)
        this.setClaw(Claw.RIGHT, ClawState.CLOSED)

        this.telemetry.addLine("Initialized devices")
        this.telemetry.update()
    }

    fun tick(gamepad1: Gamepad, gamepad2: Gamepad) {
        this.updateGamepads(gamepad1, gamepad2)
        this.registeredBooleanInputs.forEach { (button: GamepadButton, consumer: (BooleanState) -> Unit) ->
            this.handleBooleanButtonTick(button as BooleanButton, consumer)
        }
        this.updateDrivetrain()
        this.updateArm()
        this.telemetry.addLine(if (this.armBottom.isPressed) "sensor pressed" else "sensor not pressed")

    }

    private fun updateArm() {
        this.armBaseMotor.power = -this.armMotorStick.get() * 0.5
        // mounted backwards
        val spindlePosition = this.spindleDrive.currentPosition
        if (spindlePosition >= -5) {
            this.spindleDrive.power =
                (this.spindleExtendStick.get() - this.spindleRetractStick.get()) * 0.35
        } else {
            this.spindleDrive.power = 0.04
        }
        telemetry.addLine(spindlePosition.toString())
        updateWrist()
    }

    private fun updateWrist() {
        this.pitchWrist.position =
            (this.pitchWrist.position + this.wristPitchStick.get() * 0.002).coerceIn(0.25..0.9)
        // telemetry.addLine(pitchWrist.position.toString())
        this.rollWrist.position =
            (this.rollWrist.position + this.wristRollStick.get() * 0.005).coerceIn(0.0..0.54)
        // telemetry.addLine(rollWrist.position.toString())
    }

    private fun updateGamepads(gamepad1: Gamepad, gamepad2: Gamepad) {
        try {
            this.pastGamepad1.copy(this.currentGamepad1)
            this.pastGamepad2.copy(this.currentGamepad2)
            this.currentGamepad1.copy(gamepad1)
            this.currentGamepad2.copy(gamepad2)
        } catch (ignored: Exception) {
        }
    }

    fun registerDrivetrainButtons(
        x: FloatButton,
        y: FloatButton,
        rotateL: FloatButton,
        rotateR: FloatButton
    ) {
        this.drivetrainButtons = arrayOf(x, y, rotateL, rotateR)
    }

    private fun updateDrivetrain() {
        // matrix that holds the directions the wheels need to go
        // for movement in the x, y and rotational axes
        val directionMatrix = GeneralMatrixF(
            4, 3, floatArrayOf(
                1f, -1f, -1f,
                1f, 1f, 1f,
                1f, 1f, -1f,
                1f, -1f, 1f
            )
        )

        // matrix that hold the state of controller input that is translated to wheel speeds
        val inputMatrix = GeneralMatrixF(
            3,
            1,
            this.drivetrainButtons.take(2).map { it.get() }.map { it * -1 }.plus(
                (this.drivetrainButtons[2].get() + -this.drivetrainButtons[3].get()) * this.speedMultiplier.sign
            ).toFloatArray()
        )

        // 4 length float that holds the speeds for each wheel
        // calculated by summing the weights of the wheel movement
        // for each axis via matrix multiplication
        val wheelSpeeds = (directionMatrix.multiplied(inputMatrix) as GeneralMatrixF).data

        // normalize the wheel speeds to within 0..1 and apply the new speeds
        val maxSpeed = wheelSpeeds.maxOfOrNull { it.absoluteValue }!!
        wheelSpeeds.map { if (maxSpeed > 1) it / maxSpeed else it }.zip(this.drivetrainMotors)
            .forEach { it.second.power = it.first.toDouble() * this.speedMultiplier }
    }

    private fun handleBooleanButtonTick(button: BooleanButton, consumer: (BooleanState) -> Unit) {
        val state = this.getState(button)
        consumer(state)
    }

//    fun registerButton(button: GamepadButton, consumer: (BooleanState) -> Unit) {
//        this.registeredBooleanInputs[button] = consumer
//    }

    fun registerButton(button: GamepadButton, consumer: Function1<Robot, Unit>) {
        val wrappingConsumer: (BooleanState) -> Unit = {
            if (it == BooleanState.RISING_EDGE) {
                consumer(this)
            }
        }

        this.registeredBooleanInputs[button] = wrappingConsumer
    }

    fun registerButton(button: GamepadButton, consumer: () -> Unit) {
        val wrappingConsumer: (BooleanState) -> Unit = {
            if (it == BooleanState.RISING_EDGE) {
                telemetry.addLine(button.toString())
                telemetry.update()
                consumer()
            }
        }

        this.registeredBooleanInputs[button] = wrappingConsumer
    }

    fun switchDirection() {
        this.speedMultiplier *= -1
    }

    enum class Claw {
        RIGHT, LEFT
    }

    enum class ClawState {
        CLOSED, OPEN
    }

    enum class BooleanState {
        RISING_EDGE, FALLING_EDGE, INVARIANT
    }

    private fun getState(button: BooleanButton): BooleanState {
        if (button.get() && !button.getPrev()) return BooleanState.RISING_EDGE
        else if (!button.get() && button.getPrev()) return BooleanState.FALLING_EDGE
        return BooleanState.INVARIANT
    }

    fun registerArmButtons(
        baseStick: FloatButton,
        spindleExtendStick: FloatButton,
        spindleRetractStick: FloatButton,
        wristPitchStick: FloatButton,
        wristRollStick: FloatButton
    ) {
        this.armMotorStick = baseStick
        this.spindleExtendStick = spindleExtendStick
        this.spindleRetractStick = spindleRetractStick
        this.wristPitchStick = wristPitchStick
        this.wristRollStick = wristRollStick
    }

    fun setClaw(claw: Claw, state: ClawState) {
        when (claw) {
            Claw.LEFT -> this.setClawInternal(this.lclaw, state)
            Claw.RIGHT -> this.setClawInternal(this.rclaw, state)
        }
    }

    private fun setClawInternal(claw: Servo, state: ClawState) {
        when (state) {
            ClawState.OPEN -> claw.position = 0.8
            ClawState.CLOSED -> claw.position = 0.5
        }
    }

    abstract inner class GamepadButton(private val number: Int) {
        abstract fun get(): Any
        abstract fun getPrev(): Any

        fun getGamepadNum(): Int {
            return this.number
        }

        fun getGamepad(): Gamepad {
            return when (this.number) {
                0 -> this@Robot.currentGamepad1
                1 -> this@Robot.currentGamepad2
                else -> throw IllegalStateException()

            }
        }

        fun getPrevGamepad(): Gamepad {
            return when (this.number) {
                0 -> this@Robot.pastGamepad1
                1 -> this@Robot.pastGamepad2
                else -> throw IllegalStateException()

            }
        }
    }

    inner class FloatButton(private val button: FloatButtonType, number: Int) :
        Robot.GamepadButton(number) {
        override fun get(): Float {
            return this.button.get(this.getGamepad())
        }

        override fun getPrev(): Float {
            return this.button.get(this.getPrevGamepad())
        }
    }

    inner class BooleanButton(private val button: BooleanButtonType, number: Int) :
        Robot.GamepadButton(number) {
        override fun get(): Boolean {
            return this.button.get(this.getGamepad())
        }

        override fun getPrev(): Boolean {
            return this.button.get(this.getPrevGamepad())
        }
    }
}