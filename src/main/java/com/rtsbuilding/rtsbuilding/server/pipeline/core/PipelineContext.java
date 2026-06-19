package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public class PipelineContext {

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = new TypedKey<Integer>(
        "workflowEntryId",
        Integer.class);

    private final EntityPlayerMP player;
    private final Map<String, Object> args;
    private final Map<String, Object> data = new HashMap<String, Object>();
    private PipelineResult result;

    public PipelineContext(EntityPlayerMP player, Map<String, Object> args) {
        if (player == null) throw new IllegalArgumentException("player");
        this.player = player;
        this.args = Collections
            .unmodifiableMap(new HashMap<String, Object>(args == null ? Collections.<String, Object>emptyMap() : args));
    }

    public EntityPlayerMP player() {
        return player;
    }

    public RtsStorageSession session() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    public Map<String, Object> args() {
        return args;
    }

    public <T> T getArg(TypedKey<T> key) {
        Object value = args.get(key.name());
        return value == null ? null
            : key.type()
                .cast(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArg(String key) {
        return (T) args.get(key);
    }

    public boolean hasArg(TypedKey<?> key) {
        return args.containsKey(key.name());
    }

    public <T> void setData(TypedKey<T> key, T value) {
        data.put(key.name(), value);
    }

    public <T> T getData(TypedKey<T> key) {
        Object value = data.get(key.name());
        return value == null ? null
            : key.type()
                .cast(value);
    }

    public boolean hasData(TypedKey<?> key) {
        return data.containsKey(key.name());
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    public void retainOnly(TypedKey<?>... keys) {
        Map<String, Object> retained = new HashMap<String, Object>();
        for (TypedKey<?> key : keys) if (data.containsKey(key.name())) retained.put(key.name(), data.get(key.name()));
        data.clear();
        data.putAll(retained);
    }

    public PipelineResult result() {
        return result;
    }

    public void setResult(PipelineResult result) {
        this.result = result;
    }
}
