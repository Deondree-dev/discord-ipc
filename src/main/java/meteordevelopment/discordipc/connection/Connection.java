package meteordevelopment.discordipc.connection;

import com.google.gson.JsonObject;
import meteordevelopment.discordipc.Opcode;
import meteordevelopment.discordipc.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public abstract class Connection {
    private final static String[] UNIX_TEMP_PATHS = { "XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP" };

    private static Connection getConnection(String type, Path path, Consumer<Packet> callback) {

        AtomicReference<Connection> connection = new AtomicReference<>();

        IntStream.range(0, 10).forEach(i->{
            try {
                if (type.contains("win"))
                    connection.set(new WinConnection(path.resolve("discord-ipc-" + i).toString(), callback));
                else connection.set(new UnixConnection(path.resolve("discord-ipc-" + i).toString(), callback));
            } catch (IOException ignored) {}
        });
        
        return connection.get();
    }

    public static Connection open(Consumer<Packet> callback) {
        String os = System.getProperty("os.name").toLowerCase();

        // Windows
        if (os.contains("win")) {
            getConnection("win", Path.of("\\\\.\\pipe\\"), callback);
        }
        // Unix
        else {
            String path = null;

            for (String tempPath : UNIX_TEMP_PATHS) {
                path = System.getenv(tempPath);
                if (path != null) break;
            }

            if (path == null) path = "/tmp";

            //default
            Path defaultIPCPath=Path.of(path);
            //this is what the flatpak version of discord runs under.
            Path flatpakIPCPath= defaultIPCPath.resolve("app/com.discordapp.Discord/");
            //this is for the snap discord installation.
            Path snapIPCPath= defaultIPCPath.resolve("snap.discord/");
            //this is for Vesktop installations of discord.
            Path vesktopIPCPath= defaultIPCPath.resolve("app/dev.vencord.Vesktop/");

            //flatpak
            if (Files.exists(flatpakIPCPath)) {
                return getConnection("unix", flatpakIPCPath, callback);
            }
            //snap
            else if (Files.exists(snapIPCPath)) {
                return getConnection("unix", snapIPCPath, callback);
            }
            //vesktop
            else if (Files.exists(vesktopIPCPath)) {
                return getConnection("unix", vesktopIPCPath, callback);
            }
            //normal
            else if (Files.exists(defaultIPCPath)) {
                return getConnection("unix", defaultIPCPath, callback);
            }
        }

        return null;
    }

    public void write(Opcode opcode, JsonObject o) {
        o.addProperty("nonce", UUID.randomUUID().toString());

        byte[] d = o.toString().getBytes();
        ByteBuffer packet = ByteBuffer.allocate(d.length + 8);
        packet.putInt(Integer.reverseBytes(opcode.ordinal()));
        packet.putInt(Integer.reverseBytes(d.length));
        packet.put(d);

        packet.rewind();
        write(packet);
    }

    protected abstract void write(ByteBuffer buffer);

    public abstract void close();
}
