package tools.descartes.teastore.image.setup;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.ImageSize;
import tools.descartes.teastore.entities.ImageSizePreset;
import tools.descartes.teastore.image.ImageDB;

/**
 * Helper class creating image generation runnables for image provider setup.
 * @author Norbert Schmitt
 */
public class CreatorFactory {

  private int shapesPerImage = 0;
  private ImageSize imgSize = ImageSizePreset.STD_IMAGE_SIZE;
  private Path workingDir;
  private Map<Category, BufferedImage> categoryImages = java.util.Collections.emptyMap();
  private List<CreationRequest> creationRequests;
  private ImageDB imgDB;
  private AtomicLong nrOfImagesGenerated;

  public CreatorFactory(int shapesPerImage, ImageDB imgDB, ImageSize imgSize, Path workingDir,
      Map<Category, List<Long>> products, Map<Category, BufferedImage> categoryImages,
      AtomicLong nrOfImagesGenerated) {

    if (imgDB == null) {
      log.error("Supplied image database is null.");
      throw new NullPointerException("Supplied image database is null.");
    }
    if (products == null) {
      log.error("Supplied product map is null.");
      throw new NullPointerException("Supplied product map is null.");
    }
    if (nrOfImagesGenerated == null) {
      log.error("Supplied counter for images generated is null.");
      throw new NullPointerException("Supplied counter for images generated is null.");
    }

    this.workingDir = resolveWorkingDir(workingDir);
    this.categoryImages = resolveCategoryImages(categoryImages);
    this.imgSize = resolveImageSize(imgSize);
    this.shapesPerImage = resolveShapesPerImage(shapesPerImage);

    this.creationRequests = buildCreationRequests(products);

    this.imgDB = imgDB;
    this.nrOfImagesGenerated = nrOfImagesGenerated;
  }

  public Runnable newRunnable() {
    CreationRequest req = creationRequests.remove(0);
    BufferedImage categoryImg = categoryImages.getOrDefault(req.getCategory(), null);

    return new CreatorRunner(imgDB, imgSize, req.getProductId(), shapesPerImage,
        categoryImg, workingDir, nrOfImagesGenerated);
  }

  private Path resolveWorkingDir(Path workingDir) {
    if (workingDir == null) {
      Path defaultDir = SetupController.SETUP.getWorkingDir();
      log.info("Supplied working directory is null. Set to value {}.", defaultDir);
      return defaultDir;
    }
    return workingDir;
  }

  private Map<Category, BufferedImage> resolveCategoryImages(Map<Category, BufferedImage> categoryImages) {
    if (categoryImages == null) {
      log.info("Supplied category images are null. Defaulting to not add category images.");
      return java.util.Collections.emptyMap();
    }
    return categoryImages;
  }

  private ImageSize resolveImageSize(ImageSize imgSize) {
    if (imgSize == null) {
      log.info("Supplied image size is null. Defaulting to standard size of {}.",
          ImageSizePreset.STD_IMAGE_SIZE);
      return ImageSizePreset.STD_IMAGE_SIZE;
    }
    return imgSize;
  }

  private int resolveShapesPerImage(int shapesPerImage) {
    if (shapesPerImage < 0) {
      log.info("Number of shapes per image cannot be below 0, was {}. Set to 0.", shapesPerImage);
      return 0;
    }
    return shapesPerImage;
  }

  private List<CreationRequest> buildCreationRequests(Map<Category, List<Long>> products) {
    return products.entrySet().stream()
        .flatMap(e -> e.getValue().stream().map(productId -> new CreationRequest(productId, e.getKey())))
        .collect(Collectors.toCollection(java.util.ArrayList::new));
  }

  private static final class CreationRequest {
    private final long productId;
    private final Category category;

    private CreationRequest(long productId, Category category) {
      this.productId = productId;
      this.category = category;
    }

    private long getProductId() {
      return productId;
    }

    private Category getCategory() {
      return category;
    }
  }
}

