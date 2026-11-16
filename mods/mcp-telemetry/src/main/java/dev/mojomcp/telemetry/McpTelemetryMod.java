package dev.mojomcp.telemetry;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Client-side telemetry: publishes a JSON snapshot of the local player over a loopback socket. */
public class McpTelemetryMod implements ClientModInitializer {
    public static final int PORT = 3001;
    public static final int COMMAND_PORT = 3002;
    /** Allow-listed bot commands (no leading slash) waiting to be sent, one every few ticks. */
    static final ConcurrentLinkedQueue<String> COMMANDS = new ConcurrentLinkedQueue<>();
    private static final int MAX_ENTITIES = 40;
    private static final double ENTITY_RANGE = 48.0;

    static volatile String latest = "{\"in_world\":false}";
    private int tick = 0;

    @Override
    public void onInitializeClient() {
        TelemetryServer.start(PORT);
        CommandServer.start(COMMAND_PORT);
        latest = "{\"in_world\":false,\"note\":\"mod loaded, waiting for a world\"}";
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            if (tick % 5 == 0) drainCommand(mc); // at most ~4 commands per second
            if (tick % 4 != 0) return; // ~5 snapshots per second
            try {
                latest = snapshot(mc);
            } catch (Throwable t) {
                // Surface the exact failure (e.g. a game method that changed) instead of failing silently.
                JsonObject err = new JsonObject();
                err.addProperty("in_world", false);
                err.addProperty("error", t.getClass().getSimpleName() + ": " + t.getMessage());
                latest = err.toString();
            }
        });
    }

    private static void drainCommand(Minecraft mc) {
        String c = COMMANDS.poll();
        if (c == null || mc.player == null) return;
        try {
            mc.player.connection.sendCommand(c);
        } catch (Throwable ignored) {
            // never let a command problem affect the game
        }
    }

    private static String snapshot(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        JsonObject o = new JsonObject();
        if (p == null || level == null) {
            o.addProperty("in_world", false);
            return o.toString();
        }
        o.addProperty("in_world", true);
        o.addProperty("x", p.getX());
        o.addProperty("y", p.getY());
        o.addProperty("z", p.getZ());
        o.addProperty("yaw", p.getYRot());
        o.addProperty("pitch", p.getXRot());
        o.addProperty("health", p.getHealth());
        o.addProperty("max_health", p.getMaxHealth());
        o.addProperty("food", p.getFoodData().getFoodLevel());
        o.addProperty("on_ground", p.onGround());
        o.addProperty("server", mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer");

        Inventory inv = p.getInventory();
        o.addProperty("selected_slot", inv.getSelectedSlot() + 1);
        JsonArray items = new JsonArray();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            JsonObject it = new JsonObject();
            it.addProperty("slot", i);
            it.addProperty("item", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            it.addProperty("count", s.getCount());
            items.add(it);
        }
        o.add("inventory", items);

        JsonArray ents = new JsonArray();
        int n = 0;
        for (Entity e : level.entitiesForRendering()) {
            if (e == p) continue;
            double d = e.distanceTo(p);
            if (d > ENTITY_RANGE) continue;
            JsonObject eo = new JsonObject();
            eo.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            eo.addProperty("name", e.getName().getString());
            eo.addProperty("x", e.getX());
            eo.addProperty("y", e.getY());
            eo.addProperty("z", e.getZ());
            eo.addProperty("distance", Math.round(d * 10.0) / 10.0);
            ents.add(eo);
            if (++n >= MAX_ENTITIES) break;
        }
        o.add("entities", ents);
        return o.toString();
    }
}
