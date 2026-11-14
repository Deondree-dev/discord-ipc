package meteordevelopment.discordipc.connection;

import com.google.gson.JsonObject;
import meteordevelopment.discordipc.Opcode;
import meteordevelopment.discordipc.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class Connection {
    private final static String[] UNIX_TEMP_PATHS = { "XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP" };

    private static Connection tryAndReturnConnection(Path path, Consumer<Packet> callback) {
        for (int i = 0; i < 10; i++) {
            try {
                return new UnixConnection(path.resolve("discord-ipc-"+i).toString(), callback);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    public static Connection open(Consumer<Packet> callback) {
        String os = System.getProperty("os.name").toLowerCase();

        // Windows
        if (os.contains("win")) {
            for (int i = 0; i < 10; i++) {
                try {
                    return new WinConnection("\\\\.\\pipe\\discord-ipc-" + i, callback);
                } catch (IOException ignored) {}
            }
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
                return tryAndReturnConnection(flatpakIPCPath, callback);
            }
            //snap
            else if (Files.exists(snapIPCPath)) {
                return tryAndReturnConnection(snapIPCPath, callback);
            }
            //vesktop
            else if (Files.exists(vesktopIPCPath)) {
                return tryAndReturnConnection(vesktopIPCPath, callback);
            }
            //normal
            else if (Files.exists(defaultIPCPath)) {
                return tryAndReturnConnection(defaultIPCPath, callback);
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
