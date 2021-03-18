package sample.rlai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import robocode.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Event for firing a bullet.
 */
class BulletFiredEvent {

    /**
     * Reference to bullet.
     */
    private final Bullet bullet;

    /**
     * Get bullet that was fired.
     *
     * @return Bullet.
     */
    public Bullet getBullet() { return bullet; }

    /**
     * Time (turn) in which the bullet was fired.
     */
    private final long time;

    /**
     * Get time (turn) in which the bullet was fired.
     *
     * @return Time (turn).
     */
    public long getTime() { return time; }

    /**
     * Constructor.
     *
     * @param bullet Bullet.
     * @param time Time (turn).
     */
    public BulletFiredEvent(Bullet bullet, long time) {
        this.bullet = bullet;
        this.time = time;
    }
}

/**
 * Robot that interfaces with the RLAI TCP environment server.
 */
public class RlaiRobot extends Robot {

    private Socket _socket;
    private PrintWriter _clientWriter;
    private BufferedReader _clientReader;

    private final HashMap<String, Object> _state;
    private final HashMap<String, ArrayList<Object>> _events;
    private final Gson _gson;

    /**
     * Constructor
     */
    public RlaiRobot() {
        _state = new HashMap<>();
        _events = new HashMap<>();
        _gson = new GsonBuilder().serializeNulls().create();
    }

    /**
     * Thread target. Runs the robot by obtaining actions from the RLAI TCP server and sending state information back.
     */
    public void run() {

        setAdjustRadarForGunTurn(true);

        try {
            resetForNewRun();
        } catch (IOException e) {
            System.out.println("Exception while resetting:  " + e.getClass().getSimpleName());
            return;
        }

        try {
            while (true) {

                // get and execute the next action
                Map<String, Object> action;
                try {
                    action = readAction();
                } catch (IOException e) {
                    System.out.println("Exception while getting action from server:  " + e.getClass().getSimpleName());
                    break;
                }

                if (action == null) {
                    System.out.println("RLAI server returned null action. Terminating RLAI robot thread.");
                    break;
                }

                String actionName = (String) action.get("name");
                Object actionValue = action.get("value");

                switch (actionName) {

                    case "doNothing":
                        doNothing();
                        break;

                    // robot movement
                    case "ahead":
                        ahead((double) actionValue);
                        break;
                    case "back":
                        back((double) actionValue);
                        break;
                    case "turnLeft":
                        turnLeft((double) actionValue);
                        break;
                    case "turnRight":
                        turnRight((double) actionValue);
                        break;

                    // radar movement and scanning
                    case "turnRadarLeft":
                        turnRadarLeft((double) actionValue);
                        break;
                    case "turnRadarRight":
                        turnRadarRight((double) actionValue);
                        break;
                    case "setAdjustRadarForRobotTurn":
                        setAdjustRadarForRobotTurn((boolean) actionValue);
                        break;
                    case "setAdjustRadarForGunTurn":
                        setAdjustRadarForGunTurn((boolean) actionValue);
                        break;
                    case "scan":
                        scan();
                        break;

                    // gun movement and firing
                    case "turnGunLeft":
                        turnGunLeft((double) actionValue);
                        break;
                    case "turnGunRight":
                        turnGunRight((double) actionValue);
                        break;
                    case "setAdjustGunForRobotTurn":
                        setAdjustGunForRobotTurn((boolean) actionValue);
                        break;
                    case "fire":
                        Bullet bullet = fireBullet((double) actionValue);
                        if (bullet != null) {
                            BulletFiredEvent bulletFiredEvent = new BulletFiredEvent(bullet, getTime());
                            addEvent(bulletFiredEvent);
                        }
                        break;

                    // stop/resume
                    case "stop":
                        stop((boolean) actionValue);
                        break;
                    case "resume":
                        resume();
                        break;
                }

                writeState();
            }
        }
        finally {
            try {
                _socket.close();
            } catch (IOException e) {
                System.out.println("Exception while closing socket:  " + e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Reset the robot for a new run (round).
     */
    private void resetForNewRun() throws IOException {

        SocketChannel channel = SocketChannel.open(new InetSocketAddress("127.0.0.1", 54321));
        _socket = channel.socket();
        _clientWriter = new PrintWriter(_socket.getOutputStream(), true);
        _clientReader = new BufferedReader(new InputStreamReader(_socket.getInputStream()));

        synchronized (_events) {
            String payload = getStatePayload();
            _clientWriter.println(payload);
        }
    }

    /**
     * Read the next action from the server.
     *
     * @return Action dictionary.
     */
    private Map<String, Object> readAction() throws IOException {

        Type actionMapType = new TypeToken<Map<String, Object>>() {}.getType();
        String actionResponseJSON = _clientReader.readLine();
        return _gson.fromJson(actionResponseJSON, actionMapType);
    }

    /**
     * Write current state to the server.
     */
    private void writeState() {

        synchronized (_events) {

            String payload = getStatePayload();
            _clientWriter.println(payload);

            // all events were sent to the server. clear them so they don't get sent again.
            _events.clear();
        }
    }

    /**
     * Get state payload for sending to the server.
     *
     * @return Payload String.
     */
    private String getStatePayload() {

        updateState();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("state", _state);
        payload.put("events", _events);

        return _gson.toJson(payload);
    }

    /**
     * Update the state map.
     */
    private void updateState() {

        _state.put("battle_field_height", getBattleFieldHeight());
        _state.put("battle_field_width", getBattleFieldWidth());
        _state.put("energy", getEnergy());
        _state.put("gun_cooling_rate", getGunCoolingRate());
        _state.put("gun_heading", getGunHeading());
        _state.put("gun_heat", getGunHeat());
        _state.put("heading", getHeading());
        _state.put("height", getHeight());
        _state.put("num_rounds", getNumRounds());
        _state.put("num_sentries", getNumSentries());
        _state.put("others", getOthers());
        _state.put("radar_heading", getRadarHeading());
        _state.put("round_num", getRoundNum());
        _state.put("sentry_border_size", getSentryBorderSize());
        _state.put("time", getTime());
        _state.put("velocity", getVelocity());
        _state.put("width", getWidth());
        _state.put("x", getX());
        _state.put("y", getY());

    }

    public void onBattleEnded(BattleEndedEvent event) {
        addEvent(event);
    }

    public void onBulletHit(BulletHitEvent event) {
        addEvent(event);
    }

    public void onBulletHitBullet(BulletHitBulletEvent event) {
        addEvent(event);
    }

    public void onBulletMissed(BulletMissedEvent event) {
        addEvent(event);
    }

    public void onDeath(DeathEvent event) {
        addEvent(event);
    }

    public void onHitByBullet(HitByBulletEvent event) {
        addEvent(event);
    }

    public void onHitRobot(HitRobotEvent event) {
        addEvent(event);
    }

    public void onHitWall(HitWallEvent event) {
        addEvent(event);
    }

    public void onRobotDeath(RobotDeathEvent event) {
        addEvent(event);
    }

    public void onRoundEnded(RoundEndedEvent event) {
        addEvent(event);
    }

    public void onScannedRobot(ScannedRobotEvent event) {
        addEvent(event);
    }

    public void onWin(WinEvent event) {
        addEvent(event);
    }

    private void addEvent(Object event) {
        synchronized (_events) {
            String type = event.getClass().getSimpleName();
            ArrayList<Object> list;
            if (_events.containsKey(type)) {
                list = _events.get(type);
            } else {
                list = new ArrayList<>();
                _events.put(type, list);
            }
            list.add(event);
        }
    }
}