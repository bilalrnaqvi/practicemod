import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/** Converts the approved overheat art pass into the compact transparent texture used in-game. */
public final class BuildOverheatTexture {
    private BuildOverheatTexture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: BuildOverheatTexture <source.png> <output.png>");
        }

        BufferedImage source = ImageIO.read(new File(args[0]));
        int width = source.getWidth();
        int height = source.getHeight();
        boolean[] background = new boolean[width * height];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(source, x, 0, background, queue);
            enqueueBackground(source, x, height - 1, background, queue);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackground(source, 0, y, background, queue);
            enqueueBackground(source, width - 1, y, background, queue);
        }
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;
            enqueueBackground(source, x - 1, y, background, queue);
            enqueueBackground(source, x + 1, y, background, queue);
            enqueueBackground(source, x, y - 1, background, queue);
            enqueueBackground(source, x, y + 1, background, queue);
        }

        BufferedImage transparent = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = background[y * width + x] ? 0 : 0xFF000000 | source.getRGB(x, y) & 0x00FFFFFF;
                transparent.setRGB(x, y, argb);
                if ((argb >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("no overheat pixels remained after background extraction");
        }

        BufferedImage crop = transparent.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        BufferedImage output = new BufferedImage(64, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double scale = Math.min(62.0 / crop.getWidth(), 96.0 / crop.getHeight());
        int drawWidth = Math.max(1, (int) Math.round(crop.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(crop.getHeight() * scale));
        int drawX = (64 - drawWidth) / 2;
        graphics.drawImage(crop, drawX, 0, drawX + drawWidth, drawHeight,
                0, 0, crop.getWidth(), crop.getHeight(), null);
        graphics.dispose();

        File outputFile = new File(args[1]);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("could not create " + parent);
        }
        ImageIO.write(output, "png", outputFile);
    }

    private static void enqueueBackground(BufferedImage image, int x, int y,
            boolean[] background, ArrayDeque<Integer> queue) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        int index = y * image.getWidth() + x;
        if (background[index] || !isBackground(image.getRGB(x, y))) {
            return;
        }
        background[index] = true;
        queue.addLast(index);
    }

    private static boolean isBackground(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return min >= 225 && max - min <= 12;
    }
}
