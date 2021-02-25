package rlai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.glassfish.jersey.client.ClientConfig;
import robocode.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Robot that interfaces with the RLAI REST server.
 */
public class RlaiRobot extends Robot {

    private boolean _exitThread;
    private final HashMap<String, Object> _state;
    private final HashMap<String, ArrayList<Event>> _events;
    private final Gson _gson;
    private final Invocation.Builder _resetInvocationBuilder;
    private final Invocation.Builder _getActionInvocationBuilder;
    private final Invocation.Builder _setStateInvocationBuilder;

    /**
     * Constructor
     */
    public RlaiRobot() {

        _exitThread = false;
        _state = new HashMap<>();
        _events = new HashMap<>();
        _gson = new GsonBuilder().serializeNulls().create();

        // initialize rest invocation builders
        ClientConfig config = new ClientConfig();
        Client client = ClientBuilder.newClient(config);
        URI rlaiRestHost = UriBuilder.fromUri("http://127.0.0.1:12345").build();

        _resetInvocationBuilder = client.target(rlaiRestHost).
                path("reset-for-new-run").
                request().
                accept(MediaType.APPLICATION_JSON);

        _getActionInvocationBuilder = client.target(rlaiRestHost).
                path("get-action").
                request().
                accept(MediaType.APPLICATION_JSON);

        _setStateInvocationBuilder = client.target(rlaiRestHost).
                path("set-state").
                request().
                accept(MediaType.APPLICATION_JSON);
    }

    /**
     * Thread target. Runs the robot by obtaining actions from the RLAI REST server and sending state information back.
     */
    public void run() {

        resetForNewRun();

        while (!_exitThread) {

            Map<String, Object> action = getAction();

            try {

                // get and execute the next action
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
                        fire((double) actionValue);
                        break;

                    // stop/resume
                    case "stop":
                        stop((boolean) actionValue);
                        break;
                    case "resume":
                        resume();
                        break;
                }
            }

            // ensure that the server always receives the state following execution of the action, even if we threw an
            // exception while executing the action (e.g., due to being killed). the server is blocking waiting for the
            // state update and will lock up if the state is not received.
            finally {
                setState();
            }
        }

        // race condition:  if a terminal condition is encountered (robot death or win) just after the setState call
        // above, then we might potentially not send the terminal condition to the server, locking it up. send a final
        // state message to ensure that the server terminates the episode.
        setState();
    }

    /**
     * Reset the robot for a new run (round).
     */
    private void resetForNewRun() {

        synchronized (_events) {

            _exitThread = false;

            // clear any events before sending the new state to the server
            _events.clear();
            Entity<String> payload = getStatePayload();
            _resetInvocationBuilder.put(payload, String.class);
        }
    }

    /**
     * Get the next action from the server.
     *
     * @return Action dictionary.
     */
    private Map<String, Object> getAction() {

        String actionResponseJSON = _getActionInvocationBuilder.get(String.class);
        Map<String, Map<String, Object>> actionResponseMap = _gson.fromJson(actionResponseJSON, Map.class);

        return actionResponseMap.get("action");
    }

    /**
     * Set state at the server.
     */
    private void setState() {

        synchronized (_events) {

            Entity<String> payload = getStatePayload();
            _setStateInvocationBuilder.put(payload, String.class);

            // all events were sent to the server. clear them so they don't get sent again.
            _events.clear();
        }
    }

    /**
     * Get state payload for sending to the server.
     *
     * @return Payload entity.
     */
    private Entity<String> getStatePayload() {

        updateState();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("state", _state);
        payload.put("events", _events);

        String payload_json = _gson.toJson(payload);

        return Entity.json(payload_json);
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
        synchronized (_events) {
            addEvent(event);
            _exitThread = true;
        }
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
        synchronized (_events) {
            addEvent(event);
            _exitThread = true;
        }
    }

    private void addEvent(Event event) {
        synchronized (_events) {
            String type = event.getClass().getSimpleName();
            ArrayList<Event> list;
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