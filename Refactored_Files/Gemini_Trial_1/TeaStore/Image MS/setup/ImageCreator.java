/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.image.setup;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.stream.Stream;

import tools.descartes.teastore.entities.ImageSize;

/**
 * Helper class drawing images from random shapes and texts.
 * @author Norbert Schmitt
 */
public final class ImageCreator {

  /**
   * Random number generator seed.
   */
  public static final long STD_SEED = 12345;
  
  /**
   * Standard number of shapes added for each image.
   */
  public static final int STD_NR_OF_SHAPES_PER_IMAGE = 10;
  
  /**
   * Maximum RGB color code used in determining the color of the background, a shape or text.
   */
  public static final int MAX_RGB = 255;
  
  /**
   * Maximum font size of random text in an image.
   */
  public static final int MAX_FONT_SIZE = 200;
  
  /**
   * Maximum number of characters of random text in an image.
   */
  public static final int MAX_TEXT_LENGTH = 30;
  
  /**
   * Maximum number for ascii character.
   */
  public static final int MAX_CHAR_SIZE = 255;

  private ImageCreator() {
	  
  }
  
  /**
   * Create an image with the given number of shapes, with the given size. The shapes will be added to the supplied 
   * buffered image using the given random number generator.
   * @param shapesPerImage Number of shapes added to the buffered image.
   * @param categoryImg Image added at the end representing the product category.
   * @param size Size of the image in pixel.
   * @param rand Random number generator.
   * @return Returns the given buffered image with the added shapes and category image.
   */
  public static BufferedImage createImage(int shapesPerImage, BufferedImage categoryImg,
      ImageSize size, Random rand) {
    BufferedImage img = new BufferedImage(size.getWidth(), size.getHeight(), BufferedImage.OPAQUE);
    Graphics2D graphics = img.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    switchColor(graphics, rand);
    graphics.fillRect(0, 0, size.getWidth(), size.getHeight());

    for (int i = 0; i < shapesPerImage; i++) {
      switch (rand.nextInt(4)) {
      case 0:
        makeRectangle(graphics, size, rand);
        break;
      case 1:
        makeLine(graphics, size, rand);
        break;
      case 2:
        makeOval(graphics, size, rand);
        break;
      case 3:
        makeText(graphics, size, rand);
        break;
      default:
        makeRectangle(graphics, size, rand);
        break;
      }
    }

    if (categoryImg != null) {
      drawCategoryImage(graphics, size, categoryImg, rand);
    }

    graphics.dispose();
    return img;
  }

  private static void drawCategoryImage(Graphics2D graphics, ImageSize maxSize,
      BufferedImage categoryImg, Random rand) {
    graphics.drawImage(categoryImg, rand.nextInt(maxSize.getWidth() - categoryImg.getWidth()),
        rand.nextInt(maxSize.getHeight() - categoryImg.getHeight()), categoryImg.getWidth(),
        categoryImg.getHeight(), null);
  }

  private static void switchColor(Graphics2D graphics, Random rand) {
    graphics.setColor(
        new Color(rand.nextInt(MAX_RGB + 1), rand.nextInt(MAX_RGB + 1), rand.nextInt(MAX_RGB + 1)));
  }

  private static void makeRectangle(Graphics2D graphics, ImageSize maxSize, Random rand) {
    switchColor(graphics, rand);

    int x = rand.nextInt(maxSize.getWidth());
    int y = rand.nextInt(maxSize.getHeight());

    Rectangle r = new Rectangle(x, y, rand.nextInt(maxSize.getWidth() - x) + 1,
        rand.nextInt(maxSize.getHeight() - y) + 1);

    if (rand.nextBoolean()) {
      graphics.fill(r);
    }

    graphics.draw(r);
  }

  private static void makeLine(Graphics2D graphics, ImageSize maxSize, Random rand) {
    switchColor(graphics, rand);

    graphics.drawLine(rand.nextInt(maxSize.getWidth()), rand.nextInt(maxSize.getHeight()),
        rand.nextInt(maxSize.getWidth()), rand.nextInt(maxSize.getHeight()));
  }

  private static void makeOval(Graphics2D graphics, ImageSize maxSize, Random rand) {
    switchColor(graphics, rand);

    int x = rand.nextInt(maxSize.getWidth());
    int y = rand.nextInt(maxSize.getHeight());
    int width = rand.nextInt(maxSize.getWidth() - x) + 1;
    int height = rand.nextInt(maxSize.getHeight() - y) + 1;

    if (rand.nextBoolean()) {
      graphics.fillOval(x, y, width, height);
    }

    graphics.drawOval(x, y, width, height);
  }

  private static void makeText(Graphics2D graphics, ImageSize maxSize, Random rand) {
    switchColor(graphics, rand);
    configureFont(graphics, rand);
    
    String str = generateRandomString(rand);
    graphics.drawString(str, rand.nextInt(maxSize.getWidth()), rand.nextInt(maxSize.getHeight()));
  }

  private static void configureFont(Graphics2D graphics, Random rand) {
    String fontName = getRandomFontName(rand);
    int fontStyle = getRandomFontStyle(rand);
    int fontSize = rand.nextInt(MAX_FONT_SIZE + 1);
    
    graphics.setFont(new Font(fontName, fontStyle, fontSize));
  }

  private static String getRandomFontName(Random rand) {
    switch (rand.nextInt(4)) {
      case 1: return Font.MONOSPACED;
      case 2: return Font.SERIF;
      case 3: return Font.DIALOG;
      case 0: 
      default: return Font.SANS_SERIF;
    }
  }

  private static int getRandomFontStyle(Random rand) {
    switch (rand.nextInt(3)) {
      case 1: return Font.BOLD;
      case 2: return Font.ITALIC;
      case 0:
      default: return Font.PLAIN;
    }
  }

  private static String generateRandomString(Random rand) {
    int textLength = rand.nextInt(MAX_TEXT_LENGTH + 1);
    return Stream.generate(() -> rand.nextInt(MAX_CHAR_SIZE))
        .limit(textLength)
        .map(i -> (char) i.intValue())
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
        .toString();
  }

}
