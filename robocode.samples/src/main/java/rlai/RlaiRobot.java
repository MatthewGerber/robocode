package rlai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.glassfish.jersey.client.ClientConfig;
import robocode.DeathEvent;
import robocode.Robot;
import robocode.RoundEndedEvent;
import robocode.ScannedRobotEvent;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Robot that interfaces with the rlai REST server.
 */
public class RlaiRobot extends Robot {

    private final HashMap<String, Object> _state;
    private boolean _runThread;
    private final Gson _gson;
    private final Invocation.Builder _resetInvocationBuilder;
    private final Invocation.Builder _getActionInvocationBuilder;
    private final Invocation.Builder _setStateInvocationBuilder;

    public RlaiRobot() {

        _state = new HashMap<>();
        _state.put("x", null);
        _state.put("y", null);
        _state.put("scanned_robot_event", null);
        _state.put("dead", false);
        _state.put("win", false);
        _runThread = false;
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

        while (_runThread) {

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
            finally {
                setState();
            }
        }
    }

    private void resetForNewRun() {

        _state.put("dead", false);
        _state.put("win", false);
        _runThread = true;

        updateState();

        String state_json = _gson.toJson(_state);
        Entity<String> state_entity = Entity.json(state_json);

        _resetInvocationBuilder.put(state_entity, String.class);
    }

    private void setState() {

        updateState();

        String payload_json = _gson.toJson(_state);
        Entity<String> state_entity = Entity.json(payload_json);

        _setStateInvocationBuilder.put(state_entity, String.class);
    }

    private Map<String, Object> getAction() {

        String actionResponseJSON = _getActionInvocationBuilder.get(String.class);
        Map<String, Map<String, Object>> actionResponseMap = _gson.fromJson(actionResponseJSON, Map.class);
        return actionResponseMap.get("action");

    }

    private void updateState() {

        _state.put("x", getX());
        _state.put("y", getY());

    }

    public void onScannedRobot(ScannedRobotEvent event) {

        _state.put("scanned_robot_event", event);

    }

    public void onDeath(DeathEvent event) {

        _state.put("dead", true);

    }

    public void onRoundEnded(RoundEndedEvent event) {

        boolean win = !(boolean)_state.get("dead");
        _state.put("win", win);
        _runThread = false;
    }
}
