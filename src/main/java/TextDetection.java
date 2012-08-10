import java.io.File;

public class TextDetection {

	/**
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		Image img = new Image("samples" + File.separator + "British Isles.png");
		img.addProcessor(new ThresholdProcessor(207)); // 170 TB, 200 BI, 150 PA
		img.addProcessor(new InvertProcessor());
		img.addProcessor(new ColorEraseProcessor(0, 50, 0, 50, 0, 100, 10));
		img.addProcessor(new ThicknessProcessor(1, 5));
		img.addProcessor(new RemoveLinesProcessor(60));
		img.addProcessor(new DilateProcessor(3));
		img.addProcessor(new CloseProcessor(3));

		FeatureDetector detector = new ContourBasedFeatureDetector(20, 1000000, 100, 5000);
		FeatureLinker linker = new AreaBasedFeatureLinker(1, 800);
		
				//"PORTOLAN_ATLAS.jpg");
				//TestbildM.jpg");//

		/*Image img = new Image("samples" + File.separator + "British Isles.png");
		img.addProcessor(new ThresholdProcessor(207)); // 170 TB, 200 BI, 150 PA
		img.addProcessor(new InvertProcessor());
		img.addProcessor(new ColorEraseProcessor(0, 50, 0, 50, 0, 100, 10));
		img.addProcessor(new ThicknessProcessor(1, 5));
		img.addProcessor(new RemoveLinesProcessor(60));
		img.addProcessor(new DilateProcessor(3));
		img.addProcessor(new CloseProcessor(3));
		
		FeatureDetector detector = new ContourBasedFeatureDetector(20, 1000000, 100, 5000);
		FeatureLinker linker = new AreaBasedFeatureLinker(1, 800);*/
		
		img.setImageDisplay(true, true, true);
		img.findText(detector, linker);
		img.save("TestbildOut.jpg");
	}
}
