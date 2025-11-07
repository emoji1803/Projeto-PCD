package pt.iskahoot.common.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Lightweight wrapper around JSON messages exchanged between client and server.
 * Each message is encoded as a single JSON object followed by a newline.
 */
public final class Message {

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    private final String type;
    private final JsonObject payload;

    public Message(String type, JsonObject payload) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.payload = payload == null ? new JsonObject() : payload;
    }

    public String type() {
        return type;
    }

    public JsonObject payload() {
        return payload;
    }

    public String toJson() {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", type);
        wrapper.add("payload", payload);
        return GSON.toJson(wrapper);
    }

    public static Message fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        JsonObject wrapper = GSON.fromJson(json, JsonObject.class);
        String type = wrapper.get("type").getAsString();
        JsonObject payload = wrapper.has("payload") && wrapper.get("payload").isJsonObject()
            ? wrapper.getAsJsonObject("payload")
            : new JsonObject();
        return new Message(type, payload);
    }

    public static Message of(String type) {
        return new Message(type, new JsonObject());
    }
}
