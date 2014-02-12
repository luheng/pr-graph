package data;

public class RegexHelper {
	public static String lang = "any";

	public static void setLanguage(String langName) {
		System.out.println("Set regex helper with language: " + langName);
		lang = langName;
	}
	
	public static boolean isNumerical(String token) {
		return token.trim().matches("[0-9]+|[0-9]+\\.[0-9]+|[0-9]+[0-9,]+");
	}
	
	public static boolean isPunctuation(String token) {
		if(lang.startsWith("german")) {
			return token.matches("(\\$.*)");
		}
		else if(lang.startsWith("swedish")) {
			return token.matches("(I[^DM])");
		}
		else {
			return token.matches("[.,!?:;]+");
		}
	}
}
