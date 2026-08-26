import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Writes the minimal 3x3x3 empty structure used by UmbrellaGliderGameTests. */
public final class BuildEmptyGameTestStructure {
    private static final int TAG_END = 0;
    private static final int TAG_INT = 3;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private BuildEmptyGameTestStructure() {
    }

    public static void main(String[] args) throws IOException {
        Path output = (args.length == 0
                ? Path.of("src/main/resources/data/practicemod/structure/empty.nbt")
                : Path.of(args[0])).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());

        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output))))) {
            data.writeByte(TAG_COMPOUND);
            data.writeUTF("");

            namedTag(data, TAG_INT, "DataVersion");
            data.writeInt(3955); // Minecraft 1.21.1

            namedTag(data, TAG_LIST, "size");
            data.writeByte(TAG_INT);
            data.writeInt(3);
            data.writeInt(3);
            data.writeInt(3);
            data.writeInt(3);

            namedTag(data, TAG_LIST, "palette");
            data.writeByte(TAG_COMPOUND);
            data.writeInt(1);
            namedTag(data, TAG_STRING, "Name");
            data.writeUTF("minecraft:air");
            data.writeByte(TAG_END);

            emptyCompoundList(data, "blocks");
            emptyCompoundList(data, "entities");
            data.writeByte(TAG_END);
        }
        System.out.println("Wrote " + output);
    }

    private static void namedTag(DataOutputStream data, int type, String name) throws IOException {
        data.writeByte(type);
        data.writeUTF(name);
    }

    private static void emptyCompoundList(DataOutputStream data, String name) throws IOException {
        namedTag(data, TAG_LIST, name);
        data.writeByte(TAG_COMPOUND);
        data.writeInt(0);
    }
}
