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
 * Robot that interfaces with the rlai REST server.
 */
public class RlaiRobot extends Robot {

    private final HashMap<String, Object> _state;
    private final ArrayList<Event> _events;
    private final Gson _gson;
    private final Invocation.Builder _resetInvocationBuilder;
    private final Invocation.Builder _getActionInvocationBuilder;
    private final Invocation.Builder _setStateInvocationBuilder;

    public RlaiRobot() {
        
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

    public void run() {

        resetForNewRun();

        boolean exitThread = false;

        while (!exitThread) {

            Map<String, Object> action = getAction();

            try {

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

            // ensure that the server always receives the state for the action, even if we threw an exception while
            // executing the action (e.g., due to being killed). the server is blocking waiting for it and will enter
            // an invalid state if the state is not received.
            finally {
                setState();
            }

            // if the round ended then exit the thread
            if (_state.get("round_ended_event") != null) {
                exitThread = true;
            }
        }
    }

    private void resetForNewRun() {

        updateState();
        _events.clear();

        String state_json = _gson.toJson(_state);
        Entity<String> state_entity = Entity.json(state_json);

        _resetInvocationBuilder.put(state_entity, String.class);
    }

    private Map<String, Object> getAction() {

        String actionResponseJSON = _getActionInvocationBuilder.get(String.class);
        Map<String, Map<String, Object>> actionResponseMap = _gson.fromJson(actionResponseJSON, Map.class);
        return actionResponseMap.get("action");

    }

    private void setState() {

        updateState();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("state", _state);
        payload.put("events", _events);

        String payload_json = _gson.toJson(payload);
        Entity<String> payload_entity = Entity.json(payload_json);

        _setStateInvocationBuilder.put(payload_entity, String.class);

        // all events were sent to the server. clear them so they don't get sent again.
        _events.clear();
    }

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
        _events.add(event);
    }

    public void onBulletHit(BulletHitEvent event) {
        _events.add(event);
    }

    public void onBulletHitBullet(BulletHitBulletEvent event) {
        _events.add(event);
    }

    public void onBulletMissed(BulletMissedEvent event) {
        _events.add(event);
    }

    public void onDeath(DeathEvent event) {
        _events.add(event);
    }

    public void onHitByBullet(HitByBulletEvent event) {
        _events.add(event);
    }

    public void onHitRobot(HitRobotEvent event) {
        _events.add(event);
    }

    public void onHitWall(HitWallEvent event) {
        _events.add(event);
    }

    public void onRobotDeath(RobotDeathEvent event) {
        _events.add(event);
    }

    public void onRoundEnded(RoundEndedEvent event) {
        _events.add(event);
    }

    public void onScannedRobot(ScannedRobotEvent event) {
        _events.add(event);
    }

    public void onWin(WinEvent event) {
        _events.add(event);
    }
}