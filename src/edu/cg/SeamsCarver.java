package edu.cg;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class SeamsCarver extends ImageProcessor {

	@FunctionalInterface
	interface ResizeOperation {
		BufferedImage resize();
	}

	private int numOfSeams;
	private ResizeOperation resizeOp;
	boolean[][] imageMask;
	private BufferedImage greyImage;
	private int[][] greyImageMatrix;
	private long[][] costMatrix;
	private int[][] parentMatrix;
	private ArrayList<ArrayList<Integer>> indexMatrix;
	private int[][] seams;

	public SeamsCarver(Logger logger, BufferedImage workingImage, int outWidth, RGBWeights rgbWeights,
			boolean[][] imageMask) {
		super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights, outWidth, workingImage.getHeight());

		numOfSeams = Math.abs(outWidth - inWidth);
		this.imageMask = imageMask;
		if (inWidth < 2 | inHeight < 2)
			throw new RuntimeException("Can not apply seam carving: workingImage is too small");

		if (numOfSeams > inWidth / 2)
			throw new RuntimeException("Can not apply seam carving: too many seams...");

		// Setting resizeOp by with the appropriate method reference
		if (outWidth > inWidth)
			resizeOp = this::increaseImageWidth;
		else if (outWidth < inWidth)
			resizeOp = this::reduceImageWidth;
		else
			resizeOp = this::duplicateWorkingImage;

		this.greyImage = greyscale();
		this.initBaseMatrixes();
		this.logger.log("preliminary calculations were ended.");
	}

	private void initBaseMatrixes() {
		this.indexMatrix = new ArrayList<>();
		this.greyImageMatrix = new int[this.inHeight][this.inWidth];
		this.seams = new int[this.numOfSeams][this.inHeight];

		forEach((y, x) -> {
			this.getIndexMatrixRow(y).add(x);
			this.greyImageMatrix[y][x] = new Color(this.greyImage.getRGB(x, y)).getRed();
		});
	}

	private ArrayList<Integer> getIndexMatrixRow(int y) {
		if (y >= this.indexMatrix.size())
			this.indexMatrix.add(new ArrayList<>());

		return this.indexMatrix.get(y);
	}

	private int mapIndx(int hIndx, int wIndx){
		return this.indexMatrix.get(hIndx).get(wIndx);
	}

	public BufferedImage resize() {
		return resizeOp.resize();
	}

	private BufferedImage reduceImageWidth() {
		BufferedImage outputImage = newEmptyImage(outWidth, outHeight);
		this.getSeams();

		this.setForEachParameters(outWidth, outHeight);
		forEach((y, x) -> {
			Color color = new Color(workingImage.getRGB(this.indexMatrix.get(y).get(x), y));
			outputImage.setRGB(x, y, color.getRGB());
		});

		logger.log("The image has been reduced by" + this.numOfSeams + "seams");
		return outputImage;
	}

	private BufferedImage increaseImageWidth() {
		BufferedImage outputImage = newEmptyImage(outWidth, outHeight);
		this.getSeams();

		for (int y = 0; y < this.inHeight; y++) {
			ArrayList<Integer> seamsPixels = this.getColumn(y);
			int outputXIndex = 0;

			for (int x = 0; x < this.inWidth; x++) {
				if (seamsPixels.contains(x)) {
					outputImage.setRGB(outputXIndex, y, this.workingImage.getRGB(x, y));
					outputXIndex++;
				}

				outputImage.setRGB(outputXIndex, y, this.workingImage.getRGB(x, y));
				outputXIndex++;
			}
		}

		logger.log("The image has been increased by" + numOfSeams + "seams");
		return outputImage;
	}

	public ArrayList<Integer> getColumn(int index){
		ArrayList<Integer> column = new ArrayList<>();
		try {

			for (int i = 0; i < this.seams.length; i++) {
				column.add(this.seams[i][index]);
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			System.out.println(ex.toString());
		}

		return column;
	}

	private void getSeams() {
		for (int i = 0; i < this.numOfSeams; i ++) {
			this.calcCostMatrix(inWidth - i);
			this.reduceImg(inWidth - i);
		}
	}

	private void calcCostMatrix(int curWidth) {
		this.costMatrix = new long[this.inHeight][curWidth];
		this.parentMatrix = new int[this.inHeight][curWidth];

		setForEachParameters(curWidth, inHeight);
		this.initiateEnergy(curWidth);

		this.forEach((y, x) -> {
			ArrayList<Long> mcCosts = getMCCosts(y, x, curWidth);
			this.parentMatrix[y][x] = this.getParentIndex(mcCosts, x);
			this.costMatrix[y][x] += Collections.min(mcCosts);
		});
	}

	private void initiateEnergy(int curWidth) {
		forEach((y, x) -> {
			int wNbr = x < curWidth - 1 ? x + 1 : x - 1;
			int hNbr = y < this.inHeight - 1 ? y + 1 : y - 1;

			long e1 = Math.abs(this.greyImageMatrix[y][mapIndx(y, wNbr)] - this.greyImageMatrix[y][mapIndx(y, x)]);
			long e2 = Math.abs(this.greyImageMatrix[hNbr][mapIndx(hNbr, x)] - this.greyImageMatrix[y][mapIndx(y, x)]);
			long e3 = this.imageMask[y][mapIndx(y, x)] ? Integer.MIN_VALUE : 0L;
			this.costMatrix[y][x] = e1 + e2 + e3;
		});
	}

	private int getParentIndex(ArrayList<Long> mcCosts, int wIndx) {
		int parentIndx;
		long minValue = Collections.min(mcCosts);
		parentIndx = minValue == mcCosts.get(0) ? wIndx + 1 : minValue == mcCosts.get(1)  ? wIndx : wIndx - 1;

		return parentIndx;
	}

	private ArrayList<Long> getMCCosts(int y, int x, int curWidth) {
		long costMCL = (y > 0) ? this.calcMCL(y, x, curWidth) : 0L;
		long costMCV = (y > 0) ? this.calcMCV(y, x, curWidth) : 0L;
		long costMCR = (y > 0) ? this.calcMCR(y, x, curWidth) : 0L;

		return new ArrayList<> (Arrays.asList(costMCR, costMCV, costMCL));
	}

	private long calcMCL(int hIndx, int wIndx, int curWidth) {
		long costMCL = Integer.MAX_VALUE;

		if (wIndx > 0) {
			costMCL = this.costMatrix[hIndx - 1][wIndx - 1]
					+ Math.abs(this.greyImageMatrix[hIndx - 1][mapIndx(hIndx-1, wIndx)] - this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx-1)]);
			if ( wIndx + 1 < curWidth) {
				costMCL += Math.abs(this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx-1)] - this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx+1)]);
			}
		}

		return costMCL;
	}

	private long calcMCR(int hIndx, int wIndx, int curWidth) {
		long costMCR = Integer.MAX_VALUE;

		if (wIndx < curWidth - 1) {
			costMCR = this.costMatrix[hIndx - 1][wIndx + 1]
					+ Math.abs(this.greyImageMatrix[hIndx - 1][mapIndx(hIndx-1, wIndx)] - this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx+1)]);
			if ( wIndx > 0) {
				costMCR += Math.abs(this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx-1)] - this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx+1)]);
			}
		}

		return costMCR;
	}

	private long calcMCV(int hIndx, int wIndx, int curWidth) {
		long costMCV = this.costMatrix[hIndx - 1][wIndx];

		if (wIndx > 0 && wIndx + 1 < curWidth) {
			costMCV += Math.abs(this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx-1)] - this.greyImageMatrix[hIndx][mapIndx(hIndx, wIndx+1)]);
		}

		return costMCV;
	}

	private void reduceImg(int curWidth) {
		int seamNum = inWidth - curWidth;
		int IndxToRemove = this.getMinIndex(curWidth);

		for (int y = this.inHeight - 1; y >= 0; y--) {
			this.seams[seamNum][y] = this.indexMatrix.get(y).get(IndxToRemove);
			this.indexMatrix.get(y).remove(IndxToRemove) ;
			IndxToRemove = this.parentMatrix[y][IndxToRemove];
		}
	}

	private int getMinIndex(int curWidth) {
		int minIndx = 0;
		long[] lastRow = costMatrix[inHeight - 1];

		for (int x = 0; x < curWidth; x++) {
			minIndx = lastRow[x] < lastRow[minIndx] ? x : minIndx;
		}

		return minIndx;
	}

	public BufferedImage showSeams(int seamColorRGB) {
		BufferedImage duplicateImage = duplicateWorkingImage();
		this.getSeams();

		for (int[] arr: this.seams) {
			for (int y = 0; y < this.inHeight; y++) {
				duplicateImage.setRGB(arr[y], y, seamColorRGB);
			}
		}

		logger.log("The seams are colored in red");
		return duplicateImage;
	}

	public boolean[][] getMaskAfterSeamCarving() {
		boolean[][] outImageMask;

		if( outWidth == inWidth){
			outImageMask = imageMask;

		}else if( outWidth < inWidth) {
			outImageMask = this.getReducedMask();
		}
		else {
			outImageMask = this.getIncreasedMask();
		}

			return outImageMask;
	}

	private boolean[][] getReducedMask() {
		boolean[][] outImageMask = new boolean[inHeight][outWidth];

		this.setForEachParameters(outWidth, outHeight);
		forEach((y, x) -> {
			outImageMask[y][x] = this.imageMask[y][this.indexMatrix.get(y).get(x)];
		});

		return outImageMask;
	}

	private boolean[][] getIncreasedMask() {
		boolean[][] outImageMask = new boolean[inHeight][outWidth];

		for (int y = 0; y < this.inHeight; y++) {
			ArrayList<Integer> seamsPixels = this.getColumn(y);
			int outputXIndex = 0;

			for (int x = 0; x < this.inWidth; x++) {
				outImageMask[y][outputXIndex] = this.imageMask[y][x];
				outputXIndex++;
				if (seamsPixels.contains(x)) {
					outImageMask[y][outputXIndex] = false;
					outputXIndex++;
				}
			}
		}

		return outImageMask;
	}
}