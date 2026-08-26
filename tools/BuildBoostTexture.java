import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Extracts the BOOST plume directly from the supplied Aetherpack concept sheet.
 *
 * <p>The crop is expressed as fractions of the 1536x1024 reference so the tool remains usable if
 * the sheet is rescaled. Dark neutral panel pixels are keyed out while both the white-hot core and
 * isolated blue square sparks remain. Nearest-neighbour scaling preserves the source pixel art.</p>
 */
public final class BuildBoostTexture {
    private static final double CROP_X = 999.0 / 1536.0;
    private static final double CROP_Y = 660.0 / 1024.0;
    private static final double CROP_WIDTH = 92.0 / 1536.0;
    private static final double CROP_HEIGHT = 142.0 / 1024.0;

    private BuildBoostTexture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: BuildBoostTexture <concept-art.png> <output.png>");
        }

        BufferedImage sheet = ImageIO.read(new File(args[0]));
        int cropX = (int) Math.round(sheet.getWidth() * CROP_X);
        int cropY = (int) Math.round(sheet.getHeight() * CROP_Y);
        int cropWidth = (int) Math.round(sheet.getWidth() * CROP_WIDTH);
        int cropHeight = (int) Math.round(sheet.getHeight() * CROP_HEIGHT);
        BufferedImage crop = sheet.getSubimage(cropX, cropY, cropWidth, cropHeight);

        BufferedImage keyed = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        int centerX = cropWidth / 2;
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < cropWidth; x++) {
                int rgb = crop.getRGB(x, y);
                int red = rgb >> 16 & 0xFF;
                int green = rgb >> 8 & 0xFF;
                int blue = rgb & 0xFF;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                int chroma = max - min;

                // Saturated cyan/blue covers the flame edge and detached square sparks. The bright
                // center clause retains the concept's nearly-white core, whose chroma is naturally
                // too low for a blue-screen key. Its narrow center gate cannot admit panel chrome.
                boolean bluePixel = blue >= red + 7 && blue >= green - 18 && max >= 38;
                boolean whiteCore = Math.abs(x - centerX) <= cropWidth * 0.22
                        && y < cropHeight * 0.78 && max >= 72 && chroma <= 72;
                if (!bluePixel && !whiteCore) {
                    keyed.setRGB(x, y, 0);
                    continue;
                }

                // Convert faint panel glow into soft alpha while keeping the actual pixel clusters
                // opaque. RGB is lifted away from the dark panel so translucent edges stay blue.
                int alpha = clamp((max - 28) * 4, 0, 255);
                int liftedRed = clamp(red + 8, 0, 255);
                int liftedGreen = clamp(green + 10, 0, 255);
                int liftedBlue = clamp(blue + 12, 0, 255);
                keyed.setRGB(x, y, alpha << 24 | liftedRed << 16 | liftedGreen << 8 | liftedBlue);
            }
        }

        BufferedImage output = new BufferedImage(64, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.drawImage(keyed, 1, 0, 63, 96, 0, 0, cropWidth, cropHeight, null);
        graphics.dispose();

        File outputFile = new File(args[1]);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("could not create " + parent);
        }
        ImageIO.write(output, "png", outputFile);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
