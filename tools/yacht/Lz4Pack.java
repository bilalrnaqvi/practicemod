import java.nio.file.*;
import java.io.*;
import net.jpountz.lz4.LZ4FrameOutputStream;

/** Source-file Java launcher helper for the experimental native archive. */
class Lz4Pack {
    public static void main(String[] args) throws Exception {
        try (var out = new LZ4FrameOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            Files.copy(Path.of(args[0]), out);
        }
    }
}
