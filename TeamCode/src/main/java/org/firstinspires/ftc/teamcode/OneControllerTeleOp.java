package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import com.qualcomm.robotcore.hardware.DcMotor;
//hardware



/* As of Sunday, 12/11/2022
 * TO-DO:
 * - slides code (motor + elbow servos)
 * - field centric controls (tutorial on gm0)
 * - consolidate slides motor and elbow servos under Slides object
 *
 * IN PROGRESS:
 *
 * COMPLETE:
 * - smooth acceleration robot centric drive
 * - temporary slides code (1 motor)
 * - set servos to 0 on initialization
 */


@TeleOp
public class OneControllerTeleOp extends LinearOpMode {

    public double clamp(double x, double min, double max) {
        return x > max ? max : (Math.max(x, min));
    }

    static boolean smooth_controls = false;
    static boolean reverse_controls = false;

    @Override
    public void runOpMode() throws InterruptedException {

        Hardware BigBird = new Hardware(hardwareMap, telemetry);
        Drivetrain dt = BigBird.getDrivetrain();

        BigBird.init();
        waitForStart();

        DrivetrainSpeedUpdateThread driveSpeed = new DrivetrainSpeedUpdateThread(BigBird, dt.FL, dt.BL, dt.FR, dt.BR);
        driveSpeed.start();

        SlidesThread slidesThread = new SlidesThread(BigBird);
        slidesThread.start();

        GrabberThread grabberThread = new GrabberThread(BigBird);
        grabberThread.start();

        while (opModeIsActive()) {

            telemetry.addData("claw position: ", BigBird.grabber.claw.getPosition());
            telemetry.addData("wrist position: ", BigBird.grabber.wrist.getPosition());
            telemetry.addData("slides: ", BigBird.slides.getCurrentPosition());
            telemetry.addData("slides target: ", BigBird.slides.getTargetPosition());
            telemetry.addData("elbow: ", BigBird.elbow1.getPosition());

            telemetry.update();
        }
    }

    public class DrivetrainSpeedUpdateThread extends Thread {

        Hardware BigBird;
        DcMotor FL;
        DcMotor BL;
        DcMotor FR;
        DcMotor BR;

        public DrivetrainSpeedUpdateThread(Hardware BigBird, DcMotor m1, DcMotor m2, DcMotor m3, DcMotor m4) {
            this.BigBird = BigBird;
            FL = m1;
            BL = m2;
            FR = m3;
            BR = m4;
        }

        @Override
        public void run() {
            double speedIncrement = .05;

            while (opModeIsActive()) {

                // smooth controls
                if (gamepad1.back) {
                    smooth_controls = true;
                    reverse_controls = false;
                }
                // default control mode
                else if (gamepad1.right_bumper) {
                    smooth_controls = false;
                    reverse_controls = false;
                }
                // reverse controls
                else if (gamepad1.left_bumper) {
                    smooth_controls = false;
                    reverse_controls = true;
                }

                double y = -gamepad1.left_stick_y; // Remember, this is reversed!
                double x = gamepad1.left_stick_x;
                double rx = (reverse_controls) ? -gamepad1.right_stick_x : gamepad1.right_stick_x;
                double slow = .2;

                if (gamepad1.left_trigger > .3) {
                    y *= slow;
                    x *= slow;
                    rx *= slow;
                }

                double FLTargetSpeed = clamp(y + x + rx, -1, 1);
                double BLTargetSpeed = clamp(y - x + rx, -1, 1);
                double FRTargetSpeed = clamp(y - x - rx, -1, 1);
                double BRTargetSpeed = clamp(y + x - rx, -1, 1);

                if (smooth_controls) { // Smooth acceleration & deceleration
                    adjustMotorPower(FL, FLTargetSpeed, speedIncrement);
                    adjustMotorPower(BL, BLTargetSpeed, speedIncrement);
                    adjustMotorPower(FR, FRTargetSpeed, speedIncrement);
                    adjustMotorPower(BR, BRTargetSpeed, speedIncrement);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                /*
                else if (constant_controls) { // Move one tile in a cardinal direction
                    if (gamepad1.dpad_up) {
                        BigBird.dt.driveForward(1, .8);
                    }
                    else if (gamepad1.dpad_down) {
                        BigBird.dt.driveBackward(1, .8);
                    }
                    else if (gamepad1.dpad_left) {
                        BigBird.dt.strafeLeft(1, .8);
                    }
                    else if (gamepad1.dpad_right) {
                        BigBird.dt.strafeRight(1, .8);
                    }
                }

                 */
                else if (reverse_controls) { // Normal controls
                    setMotorPowers(BigBird, -FLTargetSpeed, -BLTargetSpeed, -FRTargetSpeed, -BRTargetSpeed);
                }
                else { // Normal controls
                    setMotorPowers(BigBird, FLTargetSpeed, BLTargetSpeed, FRTargetSpeed, BRTargetSpeed);
                }
            }
        }
    }

    public class SlidesThread extends Thread {
        Hardware BigBird;
        DcMotor slides;
        SlidesTarget slidesPosition = null;

        public SlidesThread(Hardware BigBird) {
            this.BigBird = BigBird;
            slides = BigBird.slides;
            slidesPosition = Hardware.slidesPosition;
        }

        public void run() {
            boolean front = true;
            boolean elbow_changed = false;
            while (opModeIsActive()) {
                // flip elbow and wrist
                if (gamepad1.a && !elbow_changed) {
                    if (front) {
                        try {BigBird.flipElbowAndWrist(true);} catch (InterruptedException e) {}
                        front = false;
                    }
                    else {
                        try {BigBird.flipElbowAndWrist(false);} catch (InterruptedException e) {}
                        front = true;
                    }
                    elbow_changed = true;
                }
                else if (!gamepad1.a) {
                    elbow_changed = false;
                }

                // move elbow up or down slightly
                BigBird.elbowMove(clamp(slidesPosition.elbow_position - (gamepad1.right_trigger * .07) + (gamepad1.left_trigger * .07), 0, 1));

                if (gamepad1.dpad_up) {
                    slidesPosition = SlidesTarget.BACK_HIGH;
                    if (front) {
                        front = false;
                    }
                    try {
                        BigBird.flipElbowAndWrist(true, slidesPosition.elbow_position);
                    } catch (InterruptedException ignored) {
                    }
                } else if (gamepad1.dpad_left) {
                    slidesPosition = SlidesTarget.BACK_MIDDLE;
                    if (front) {
                        front = false;
                    }
                    try {
                        BigBird.flipElbowAndWrist(true, slidesPosition.elbow_position);
                    } catch (InterruptedException ignored) {
                    }
                } else if (gamepad1.dpad_right) {
                    slidesPosition = SlidesTarget.BACK_LOW;
                    if (front) {
                        front = false;
                    }
                    try {
                        BigBird.flipElbowAndWrist(true, slidesPosition.elbow_position);
                    } catch (InterruptedException ignored) {
                    }
                } else if (gamepad1.dpad_down) {
                    slidesPosition = SlidesTarget.FRONT_GROUND;
                    if (!front) {
                        front = true;
                    }
                    try {

                        BigBird.slides.setTargetPosition(slidesPosition.slides_position);
                        BigBird.flipElbowAndWrist(false);
                    } catch (InterruptedException ignored) {
                    }
                }
                BigBird.slides.setTargetPosition(slidesPosition.slides_position);

                if (BigBird.slides.getCurrentPosition() > slidesPosition.slides_position) {
                    BigBird.slides.setPower(clamp(0.2 + (BigBird.slides.getCurrentPosition() / (double) (SlidesTarget.BACK_HIGH.slides_position)), 0, 1));
                }
                else if (BigBird.slides.getCurrentPosition() < slidesPosition.slides_position) {
                    BigBird.slides.setPower(1);
                }
                else if (BigBird.slides.getCurrentPosition() == slidesPosition.slides_position) {
                    BigBird.slides.setPower(0);
                }
                // BigBird.elbowMove(slidesPosition.elbow_position);
            }

        }
    }

    public class GrabberThread extends Thread {
        Hardware BigBird;
        Grabber grabber;

        public GrabberThread(Hardware BigBird) {
            this.BigBird = BigBird;
            grabber = BigBird.grabber;
        }

        public void run() {
            boolean claw_changed = false; //Outside of loop()
            boolean wrist_changed = false;
            while (opModeIsActive()) {

                // Toggle x button to open/close claw
                if (gamepad1.x && !claw_changed) {
                    if (grabber.isClosed) {
                        grabber.openClaw();
                    } else {
                        grabber.closeClaw();
                    }
                    claw_changed = true;
                } else if (!gamepad1.x) {
                    claw_changed = false;
                }

                // Toggle y button to flip grabber
                if (gamepad1.y && !wrist_changed) {
                    if (grabber.isFlipped) {
                        grabber.rightGrabberFace();
                    } else {
                        grabber.flipGrabberFace();
                    }
                    wrist_changed = true;
                } else if (!gamepad1.y) {
                    wrist_changed = false;
                }


            }
        }
    }


    public void adjustMotorPower(DcMotor motor, double targetSpeed, double speedIncrement) {
        double speed = motor.getPower();
        if (speed > targetSpeed) {
            motor.setPower((speed > targetSpeed + speedIncrement) ? speed - speedIncrement : targetSpeed);
        } else if (speed < targetSpeed) {
            motor.setPower((speed < targetSpeed - speedIncrement) ? speed + speedIncrement : targetSpeed);
        }
    }

    public void setMotorPowers(Hardware BigBird, double FLspeed, double BLspeed, double FRspeed, double BRspeed) {
        BigBird.dt.FL.setPower(FLspeed);
        BigBird.dt.FR.setPower(FRspeed);
        BigBird.dt.BL.setPower(BLspeed);
        BigBird.dt.BR.setPower(BRspeed);
    }

    public void resetSlides(Hardware BigBird) {
        BigBird.slides.setTargetPosition(SlidesTarget.FRONT_GROUND.slides_position);
        BigBird.elbow1.setPosition(SlidesTarget.FRONT_GROUND.elbow_position);
        BigBird.elbow2.setPosition(SlidesTarget.FRONT_GROUND.elbow_position);
    }


}