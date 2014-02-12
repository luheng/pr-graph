package constraints;

public class LatticeHelper {
	public static void deepFill(double[] arr, int filler) {
		for(int i = 0; i < arr.length; i++)
			arr[i] = filler;
	}
		
	public static void deepFill(double[][] arr, double filler) {
		for(int i = 0; i < arr.length; i++)
			for(int j = 0; j < arr[i].length; j++)
				arr[i][j] = filler;
	}
	
	public static void deepFill(double[][][] arr, double filler) {
		for(int i = 0; i < arr.length; i++)
			for(int j = 0; j < arr[i].length; j++)
				for(int k = 0; k < arr[i][j].length; k++) arr[i][j][k] = filler;
	}
	
	public static void deepCopy(double[][] src, double[][] dest) {
		for(int i = 0; i < src.length; i++)
			for(int j = 0; j < src[i].length; j++)
				dest[i][j] = src[i][j];
	}
	
	public static void deepCopy(double[][][] src, double[][][] dest) {
		for(int i = 0; i < src.length; i++)
			for(int j = 0; j < src[i].length; j++)
				for(int k = 0; k < src[i][j].length; k++) 
					dest[i][j][k] = src[i][j][k];
	}
	
	public static int getMaxIndex(double[] p) {
		int maxi = 0;
		for(int i = 1; i < p.length; i++)
			if(p[maxi] < p[i]) maxi = i;
		return maxi;
	}
	
	public static double logsum(double loga, double logb) {		
		if(Double.isInfinite(loga))
			return logb;
		if(Double.isInfinite(logb))
			return loga;

		if(loga > logb) 
			return Math.log1p(Math.exp(logb - loga)) + loga;
		else 
			return Math.log1p(Math.exp(loga - logb)) + logb; 
	}
	
	public static double logsum(double[] tosum, int length) {	
		if(length == 1) return tosum[0];
					
		int idx = 0;
		for(int i = 1; i < length; i++)
			if(tosum[i] > tosum[idx]) idx = i;
		
		double maxx = tosum[idx];
		double sumexp = 0;
		
		for(int i = 0; i < length; i++)
			if(i != idx) sumexp += Math.exp(tosum[i] - maxx);
		
		return Math.log1p(sumexp) + maxx;
	}
	
}
