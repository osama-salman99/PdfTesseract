public class MainClass {
    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.replace(" ", "").equals("-version")) {
                System.out.println("PDF Tesseract Beta (1.0)");
            }
        }
    }
}
