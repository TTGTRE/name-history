package logan.namehistory;

import com.google.gson.stream.JsonReader;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NameHistoryPlugin extends JavaPlugin {

    private static final Map<UUID, SortedMap<Long, String>> NAME_HISTORY = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info(getName() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && label.equalsIgnoreCase("history")) {

            OfflinePlayer player = Bukkit.getOfflinePlayer(args[0]);
            UUID uuid = player.getUniqueId();

            new BukkitRunnable() {
                @Override
                public void run() {

                    if (!NAME_HISTORY.containsKey(uuid)) {
                        NAME_HISTORY.put(uuid, getNameHistory(uuid));
                    }

                    SortedMap<Long, String> nameHistory = NAME_HISTORY.get(uuid);
                    if (nameHistory.size() == 0) {
                        sender.sendMessage("This player has no history.");
                        return;
                    }

                    sender.sendMessage("Name history of " + player.getName() + ":");
                    printNameHistory(NAME_HISTORY.get(uuid), sender);

                }
            }.runTaskAsynchronously(this);

        }

        return true;
    }

    private void printNameHistory(SortedMap<Long, String> nameHistory, CommandSender sender) {
        int count = 1;

        for (long time : nameHistory.keySet()) {

            System.out.println(nameHistory.get(time));

            String fDateTime = "";
            if (time != 0) {
                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC"));
                fDateTime = dateTime.format(DateTimeFormatter.ISO_DATE);
            }

            String name = nameHistory.get(time);
            sender.sendMessage(count + ". " + name + " " + fDateTime);

            count++;
        }

    }

    private synchronized SortedMap<Long, String> getNameHistory(UUID uuid) {

        SortedMap<Long, String> concurrentNameHistory = Collections.synchronizedSortedMap(new TreeMap<>(this::sortFunction));

        String compatibleUUID = uuid.toString().replace("-", "");

        try {

            URL url = new URL("https://api.mojang.com/user/profiles/" + compatibleUUID + "/names");
            URLConnection connection = url.openConnection();

            JsonReader jsonReader = new JsonReader(new InputStreamReader(connection.getInputStream()));

            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                List<Object> nameTimeList = getName(jsonReader);
                long time = (long) nameTimeList.get(0);
                String name = (String) nameTimeList.get(1);
                concurrentNameHistory.put(time, name);
            }
            jsonReader.endArray();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return concurrentNameHistory;
    }

    private synchronized List<Object> getName(JsonReader reader) throws IOException {

        long timeChanged = 0L;
        String name = "";

        reader.beginObject();
        while (reader.hasNext()) {

            String id = reader.nextName();

            switch (id.toLowerCase()) {
                case "changedtoat":
                    timeChanged = reader.nextLong();
                    break;
                case "name":
                    name = reader.nextString();
                    break;
            }

        }
        reader.endObject();

        return Arrays.asList(timeChanged, name);

    }

    private int sortFunction(Long one, Long two) {
        if (one > two) {
            return 1;
        }

        if (one < two ) {
            return -1;
        }

        return 0;
    }

}
