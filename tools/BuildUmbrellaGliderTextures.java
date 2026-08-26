import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Rebuilds the intentionally temporary inventory icon and deployed-model texture. */
public final class BuildUmbrellaGliderTextures {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color DEEP_TEAL = new Color(27, 91, 91, 255);
    private static final Color TEAL = new Color(42, 130, 120, 255);
    private static final Color LIGHT_TEAL = new Color(72, 164, 142, 255);
    private static final Color GOLD = new Color(201, 158, 61, 255);
    private static final Color GOLD_LIGHT = new Color(234, 199, 99, 255);
    private static final Color WOOD = new Color(93, 59, 45, 255);
    private static final Color OUTLINE = new Color(25, 39, 42, 255);

    private BuildUmbrellaGliderTextures() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path resourceRoot = args.length == 0 ? Path.of("src/main/resources") : Path.of(args[0]);
        writeInventoryIcon(resourceRoot.resolve("assets/practicemod/textures/item/umbrella_glider.png"));
        writeEntityAtlas(resourceRoot.resolve("assets/practicemod/textures/entity/umbrella_glider.png"));
    }

    private static void writeInventoryIcon(Path output) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(TRANSPARENT);
        g.fillRect(0, 0, 16, 16);

        // Bold, readable 16px parasol: peaked teal canopy, gold ribs, dark shaft and hooked grip.
        g.setColor(OUTLINE);
        g.fillPolygon(new int[] { 1, 3, 6, 8, 10, 13, 15, 14, 2 },
                new int[] { 7, 4, 3, 1, 3, 4, 7, 8, 8 }, 9);
        g.setColor(DEEP_TEAL);
        g.fillPolygon(new int[] { 2, 4, 7, 7, 2 }, new int[] { 7, 5, 3, 7, 7 }, 5);
        g.setColor(LIGHT_TEAL);
        g.fillPolygon(new int[] { 8, 9, 12, 14, 14, 8 }, new int[] { 2, 3, 5, 7, 7, 7 }, 6);
        g.setColor(GOLD_LIGHT);
        g.fillRect(7, 3, 1, 5);
        g.fillRect(3, 6, 10, 1);
        g.setColor(WOOD);
        g.fillRect(7, 7, 2, 7);
        g.fillRect(8, 13, 3, 2);
        g.fillRect(10, 12, 1, 2);
        g.dispose();
        write(image, output);
    }

    private static void writeEntityAtlas(Path output) throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(TEAL);
        g.fillRect(0, 0, 64, 64);

        // The canopy panel UVs use y=0..24 and alternate between x=0 / x=24. Broad bands plus a
        // thin gold edge give the placeholder a fantasy-travel palette without imitating a modern
        // battle-royale umbrella.
        g.setColor(DEEP_TEAL);
        g.fillRect(0, 0, 24, 24);
        g.setColor(LIGHT_TEAL);
        g.fillRect(24, 0, 40, 24);
        g.setColor(GOLD);
        g.fillRect(0, 22, 64, 2);
        g.setColor(OUTLINE);
        g.fillRect(0, 24, 64, 4);

        // Shaft, crown, handle and rib UV region.
        g.setColor(WOOD);
        g.fillRect(0, 28, 18, 36);
        g.setColor(GOLD);
        g.fillRect(18, 28, 46, 36);
        g.setColor(GOLD_LIGHT);
        for (int y = 28; y < 64; y += 4) {
            g.fillRect(18, y, 46, 1);
        }
        g.dispose();
        write(image, output);
    }

    private static void write(BufferedImage image, Path output) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Files.createDirectories(absoluteOutput.getParent());
        if (!ImageIO.write(image, "png", absoluteOutput.toFile())) {
            throw new IOException("PNG writer unavailable for " + absoluteOutput);
        }
        System.out.println("Wrote " + absoluteOutput);
    }
}
