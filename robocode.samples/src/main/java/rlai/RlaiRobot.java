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
 * Wrapper for Robocode events that includes the event type for serialization via gson.
 */
class EventWrapper {

    public Event event;
    public String type;

    public EventWrapper(Event event) {
        this.event = event;
        this.type = event.getClass().getSimpleName();
    }
}

/**
 * Robot that interfaces with the RLAI REST server.
 */
public class RlaiRobot extends Robot {

    private boolean _exitThread;
    private final HashMap<String, Object> _state;
    private final ArrayList<EventWrapper> _events;
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
        _events = new ArrayList<>();
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
                String action_name = (String) action.get("name");
                double action_value = (double) action.get("value");

                switch (action_name) {
                    case "fire":
                        fire(action_value);
                        break;
                    case "ahead":
                        ahead(action_value);
                        break;
                    case "back":
                        back(action_value);
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
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onBulletHit(BulletHitEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onBulletHitBullet(BulletHitBulletEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onBulletMissed(BulletMissedEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onDeath(DeathEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
            _exitThread = true;
        }
    }

    public void onHitByBullet(HitByBulletEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onHitRobot(HitRobotEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onHitWall(HitWallEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onRobotDeath(RobotDeathEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onRoundEnded(RoundEndedEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onScannedRobot(ScannedRobotEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
        }
    }

    public void onWin(WinEvent event) {
        synchronized (_events) {
            _events.add(new EventWrapper(event));
            _exitThread = true;
        }
    }
}