package t11wsn.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Utils {
    private static Random r = new Random();

    public static int randInt(int min, int max) {
        return r.nextInt((max - min) + 1) + min;
    }

    public static double randDouble(double min, double max) {
        return min + (r.nextDouble()) * (max - min);
    }

    // return pdf(x) = standard Gaussian pdf
    public static double pdf(double x) {
        return Math.exp(-x*x / 2) / Math.sqrt(2 * Math.PI);
    }
    // return pdf(x, mu, signma) = Gaussian pdf with mean mu and stddev sigma
    public static double pdf(double x, double mu, double sigma) {
        return pdf((x - mu) / sigma) / sigma;
    }


    public static double entropy(double v) { return Math.log(v * Math.sqrt(2 * Math.PI * Math.E)); }

    public static double i_lerp(double a, double b, double n) { return (n - a) / (b - a); }
    public static float lerp(float a, float b, float f) { return (a * (1.0f - f)) + (b * f); }

    public static float clamp(float val, float min, float max) { return Math.max(min, Math.min(max, val)); }

    public static double mean(List<Double> m) { return m.stream().mapToDouble(Double::doubleValue).average().orElse(0); }

    public static double median(double[] l) {
        if (l.length % 2 == 0) {
            return (l[(l.length / 2) - 1] + l[l.length / 2]) / 2;
        } else {
            return l[((l.length + 1) / 2) - 1];
        }
    }

    public static void main(String[] args) {
    }
}
