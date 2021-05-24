package pdftesseract;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public abstract class PDFOperations {
	private static final List<PDDocument> USED_DOCUMENTS = new ArrayList<>();

	static {
		java.util.logging.Logger
				.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.SEVERE);
	}

	// Returns images contained in given directory
	public static List<BufferedImage> getImages(File directory) {
		if (directory.isDirectory()) {
			return getImages(directory.listFiles());
		} else {
			return getImages(new File[]{directory});
		}
	}

	// Returns list of buffered images read from given files
	public static List<BufferedImage> getImages(File[] files) {
		List<BufferedImage> images = new ArrayList<>();
		if (files == null) {
			return images;
		}
		for (File file : files) {
			if (file == null) {
				continue;
			}
			try {
				images.add(ImageIO.read(file));
			} catch (IOException exception) {
				System.out.println("Could not load " + file.getName());
			}
		}
		return images;
	}

	// Converts document pages to images
	public static List<BufferedImage> toImages(PDDocument document) throws IOException {
		if (!USED_DOCUMENTS.contains(document)) {
			USED_DOCUMENTS.add(document);
		}
		final float SCALE = 4;
		PDFRenderer renderer = new PDFRenderer(document);
		List<BufferedImage> images = new ArrayList<>();
		for (int i = 0; i < document.getNumberOfPages(); i++) {
			images.add(renderer.renderImage(i, SCALE));
		}
		return images;
	}

	// Converts images into a document
	public static PDDocument toDocument(List<BufferedImage> images) throws IOException {
		PDDocument outputDocument = new PDDocument();
		USED_DOCUMENTS.add(outputDocument);
		for (BufferedImage image : images) {
			if (image == null) {
				continue;
			}
			float width = image.getWidth();
			float height = image.getHeight();
			PDPage page = new PDPage(new PDRectangle(width, height));
			outputDocument.addPage(page);

			PDImageXObject img = PDImageXObject.createFromByteArray(outputDocument, toByteArray(image), "png");
			PDPageContentStream contentStream = new PDPageContentStream(outputDocument, page);
			contentStream.drawImage(img, 0, 0);
			contentStream.close();
		}
		return outputDocument;
	}

	// Converts document pages into images and puts them in a new document
	public static PDDocument toImagesDocument(PDDocument document) throws IOException {
		return toDocument(toImages(document));
	}

	// Splits document into several documents with "numberOfPages" pages
	public static List<PDDocument> split(PDDocument document, int numberOfPages) throws IOException {
		if (!USED_DOCUMENTS.contains(document)) {
			USED_DOCUMENTS.add(document);
		}
		Splitter splitter = new Splitter();
		splitter.setSplitAtPage(numberOfPages);
		return splitter.split(document);
	}

	// Splits document at given indices into several documents
	public static List<PDDocument> splitAt(PDDocument document, int[] indices) throws IOException {
		if (!USED_DOCUMENTS.contains(document)) {
			USED_DOCUMENTS.add(document);
		}
		List<PDDocument> pageDocuments = split(document, 1);
		ArrayList<PDDocument> documents = new ArrayList<>();
		int[] tempIndices = new int[indices.length + 2];
		tempIndices[0] = 0;
		System.arraycopy(indices, 0, tempIndices, 1, indices.length);
		tempIndices[tempIndices.length - 1] = document.getNumberOfPages();
		indices = tempIndices;
		for (int i = 0; i < indices.length - 1; i++) {
			PDDocument portion = merge(pageDocuments.subList(indices[i], indices[i + 1]));
			if (!USED_DOCUMENTS.contains(portion)) {
				USED_DOCUMENTS.add(portion);
			}
			documents.add(portion);
		}
		for (PDDocument pageDocument : pageDocuments) {
			pageDocument.close();
		}
		return documents;
	}

	// Merges pages into one document
	public static PDDocument mergePages(List<PDPage> pages) {
		PDDocument outputDocument = new PDDocument();
		USED_DOCUMENTS.add(outputDocument);
		for (PDPage page : pages) {
			outputDocument.addPage(page);
		}
		return outputDocument;
	}

	// Merges several documents into one document
	public static PDDocument merge(List<PDDocument> documents) throws IOException {
		PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
		PDDocument outputDocument = new PDDocument();
		USED_DOCUMENTS.add(outputDocument);
		for (PDDocument document : documents) {
			if (!USED_DOCUMENTS.contains(document)) {
				USED_DOCUMENTS.add(document);
			}
			pdfMergerUtility.appendDocument(outputDocument, document);
		}
		return outputDocument;
	}

	// Clips document pages from top and below by "padding"
	public static PDDocument clipPagesHeaderFooter(PDDocument document, float padding) {
		if (!USED_DOCUMENTS.contains(document)) {
			USED_DOCUMENTS.add(document);
		}
		PDDocument outputDocument = new PDDocument();
		USED_DOCUMENTS.add(outputDocument);
		PDPageTree pages = document.getPages();
		for (PDPage page : pages) {
			PDRectangle cropBox = page.getCropBox();
			float upperRightY = cropBox.getUpperRightY() - padding;
			float lowerLeftY = cropBox.getLowerLeftY() + padding;
			PDPage clonePage = new PDPage(new COSDictionary(page.getCOSObject()));

			cropBox = clonePage.getCropBox();
			cropBox.setUpperRightY(upperRightY);
			cropBox.setLowerLeftY(lowerLeftY);
			clonePage.setCropBox(cropBox);

			outputDocument.addPage(clonePage);
		}
		return outputDocument;
	}

	// Splits documents pages in half
	public static PDDocument splitPagesVertically(PDDocument document) {
		if (!USED_DOCUMENTS.contains(document)) {
			USED_DOCUMENTS.add(document);
		}
		PDDocument outputDocument = new PDDocument();
		USED_DOCUMENTS.add(outputDocument);
		PDPageTree pages = document.getPages();
		for (PDPage page : pages) {
			PDRectangle cropBox = page.getCropBox();
			float upperRightY = cropBox.getUpperRightY();
			float lowerLeftY = cropBox.getLowerLeftY();
			float splitLine = upperRightY - (upperRightY - lowerLeftY) / 2;
			PDPage clonePage;

			clonePage = new PDPage(new COSDictionary(page.getCOSObject()));
			cropBox = clonePage.getCropBox();
			cropBox.setLowerLeftY(splitLine);
			clonePage.setCropBox(cropBox);
			outputDocument.addPage(clonePage);

			clonePage = new PDPage(new COSDictionary(page.getCOSObject()));
			cropBox = clonePage.getCropBox();
			cropBox.setUpperRightY(splitLine);
			clonePage.setCropBox(cropBox);
			outputDocument.addPage(clonePage);
		}
		return outputDocument;
	}

	// Loads all valid documents in a directory
	public static List<PDDocument> loadDocuments(File directory) {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				return loadDocuments(files);
			}
		}
		return new ArrayList<>();
	}

	// Loads all valid documents in given files
	public static List<PDDocument> loadDocuments(File[] files) {
		ArrayList<PDDocument> documents = new ArrayList<>();
		Arrays.sort(files, NumberAwareFileNameComparator.INSTANCE);
		for (File file : files) {
			if (file.isDirectory() || !isPDF(file.getName())) {
				continue;
			}
			try {
				PDDocument loadedDocument = Loader.loadPDF(file);
				documents.add(loadedDocument);
				if (!USED_DOCUMENTS.contains(loadedDocument)) {
					USED_DOCUMENTS.add(loadedDocument);
				}
			} catch (IOException exception) {
				System.out.println("Could not load file: " + file.getName());
				System.out.println(exception.getMessage());
			}
		}
		return documents;
	}

	// Converts an image to a byte array
	private static byte[] toByteArray(BufferedImage image) throws IOException {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", byteArrayOutputStream);
		return byteArrayOutputStream.toByteArray();
	}

	public static void closeAllDocuments() {
		while (!USED_DOCUMENTS.isEmpty()) {
			try {
				USED_DOCUMENTS.remove(0).close();
			} catch (IOException exception) {
				System.out.println("A document could not be closed properly");
			}
		}
	}

	private static boolean isPDF(String fileName) {
		String[] splitResult = fileName.split("\\.");
		if (splitResult.length == 1) {
			return false;
		}
		return splitResult[splitResult.length - 1].equalsIgnoreCase("pdf");
	}
}
