package eu.pb4.buildoff;


import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StorageData {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().setLenient().setPrettyPrinting()
            .registerTypeAdapter(Identifier.class, new IdentifierSerializer())
            .registerTypeAdapter(BlockPos.class, new BlockPosSerializer())
            .create();


    public int current = 0;

    public List<BuildArena> arenas = new ArrayList<>();

    public static StorageData loadOrCreateConfig() {
        try {
            StorageData config;
            File configFile = new File(FabricLoader.getInstance().getGameDir().toFile(), "buildoffdatadobttouch.json");

            if (configFile.exists()) {
                String json = IOUtils.toString(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8));

                config = GSON.fromJson(json, StorageData.class);
            } else {
                config = new StorageData();
            }

            saveConfig(config);
            return config;
        } catch (IOException exception) {
            exception.printStackTrace();
            return new StorageData();
        }
    }

    public static void saveConfig(StorageData config) {
        File configFile = new File(FabricLoader.getInstance().getGameDir().toFile(), "buildoffdatadobttouch.json");
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8));
            writer.write(GSON.toJson(config));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class IdentifierSerializer implements JsonSerializer<Identifier>, JsonDeserializer<Identifier> {

        @Override
        public Identifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return Identifier.tryParse(json.getAsString());
            }
            return null;
        }

        @Override
        public JsonElement serialize(Identifier src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private static final class BlockPosSerializer implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {

        @Override
        public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonArray()) {
                return new BlockPos(json.getAsJsonArray().get(0).getAsInt(), json.getAsJsonArray().get(1).getAsInt(), json.getAsJsonArray().get(2).getAsInt());
            }
            return null;
        }

        @Override
        public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
            var o = new JsonArray();

            o.add(src.getX());
            o.add(src.getY());
            o.add(src.getZ());

            return o;
        }
    }
}
