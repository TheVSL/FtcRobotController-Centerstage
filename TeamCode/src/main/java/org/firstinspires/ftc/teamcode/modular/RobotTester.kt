package org.firstinspires.ftc.teamcode.modular

import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.hardware.Gamepad

@Suppress("unused")
@TeleOp(name = "Robot Tester", group = "danger")
class RobotTester : OpMode() {
    var robot = Robot(this.telemetry)

    override fun init() {
        this.robot.initialize(this.hardwareMap)
        this.robot.registerButton(this.robot.BooleanButton(Gamepad::a, 1), Robot::switchDirection)
        this.robot.registerButton(this.robot.BooleanButton(Gamepad::a, 2)) { ->
            this.robot.setClaw(Robot.Claw.LEFT, Robot.ClawState.OPEN)
        }
        this.robot.registerButton(this.robot.BooleanButton(Gamepad::b, 2)) { ->
            this.robot.setClaw(Robot.Claw.LEFT, Robot.ClawState.CLOSED)
        }
        this.robot.registerButton(this.robot.BooleanButton(Gamepad::x, 2)) { ->
            this.robot.setClaw(Robot.Claw.RIGHT, Robot.ClawState.OPEN)
        }
        this.robot.registerButton(this.robot.BooleanButton(Gamepad::y, 2)) { ->
            this.robot.setClaw(Robot.Claw.RIGHT, Robot.ClawState.CLOSED)
        }
        this.robot.registerDrivetrainButtons(
            this.robot.FloatButton(Gamepad::right_stick_y, 1),
            this.robot.FloatButton(Gamepad::right_stick_x, 1),
            this.robot.FloatButton(Gamepad::left_trigger, 1),
            this.robot.FloatButton(Gamepad::right_trigger, 1)
        )
        this.robot.registerArmButtons(
            this.robot.FloatButton(Gamepad::left_stick_y, 2),
            this.robot.FloatButton(Gamepad::right_trigger, 2),
            this.robot.FloatButton(Gamepad::left_trigger, 2),
            this.robot.FloatButton(Gamepad::right_stick_y, 2),
            this.robot.FloatButton(Gamepad::right_stick_x, 2)
        )
    }

    override fun loop() {
        robot.tick(gamepad1, gamepad2)
    }

}
