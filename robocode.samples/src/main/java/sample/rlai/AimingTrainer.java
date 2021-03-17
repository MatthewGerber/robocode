package sample.rlai;


import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;


/**
 * Aiming trainer.
 * <p>
 * This robot moves around in increasingly complex ways, to train an aiming control agent.
 *
 * @author Matthew S. Gerber (original)
 */
public class AimingTrainer extends AdvancedRobot {

	boolean movingForward;

	public void run() {

		movingForward = true;

		while (true) {
			if (movingForward) {
				ahead(5);
			} else {
				back(5);
			}
			for (int i = 0; i < 500 - getRoundNum(); ++i) {
				doNothing();
			}
		}
	}

	public void onHitWall(HitWallEvent e) {
		reverseDirection();
	}

	public void reverseDirection() {
		movingForward = !movingForward;
	}

	public void onHitRobot(HitRobotEvent e) {
		if (e.isMyFault()) {
			reverseDirection();
		}
	}
}