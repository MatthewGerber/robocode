package rlai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.glassfish.jersey.client.ClientConfig;
import robocode.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.util.HashMap;
import java.util.Map;

public class RlaiRobot extends Robot {

    private HashMap<String, Object> _state;
    private boolean _runThread;

    private Gson _gson;
    private WebTarget _target;

    public RlaiRobot() {

        _state = new HashMap<>();
        _state.put("x", null);
        _state.put("y", null);
        _state.put("scanned_robot_event", null);
        _state.put("dead", false);
        _state.put("win", false);

        _runThread = false;

        _gson = new GsonBuilder().serializeNulls().create();

        ClientConfig config = new ClientConfig();
        Client client = ClientBuilder.newClient(config);
        _target = client.target(UriBuilder.fromUri("http://127.0.0.1:12345").build());
    }

    public void run() {

        resetForNewRun();

        while (_runThread) {

            Map action_response = _gson.fromJson(getAction(), Map.class);
            String action_name = (String) action_response.get("action");
            double action_value = (double) action_response.get("value");

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

            setState();
        }
    }

    private void resetForNewRun() {

        _state.put("dead", false);
        _state.put("win", false);
        _runThread = true;

        updateState();

        String state_json = _gson.toJson(_state);
        Entity<String> state_entity = Entity.json(state_json);

        _target.path("reset-for-new-run").
                request().
                accept(MediaType.APPLICATION_JSON).
                put(state_entity);
    }

    private void setState() {

        updateState();

        String payload_json = _gson.toJson(_state);
        Entity<String> state_entity = Entity.json(payload_json);

        _target.path("set-state").
                request().
                accept(MediaType.APPLICATION_JSON).
                put(state_entity);
    }

    private String getAction() {

        String response = _target.path("get-action").
                request().
                accept(MediaType.APPLICATION_JSON).
                get(String.class);

        return response;
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
