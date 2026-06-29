package com.nhnacademy.ailibraryteam5.core.book.rag.service;

//유틸클래스
public final class VectorSimilarity {
    private VectorSimilarity() {}

    public static double cosine(float[] left, float[] right) {
        if(left == null || right == null || left.length == 0 || left.length != right.length) {
            return -1.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;

        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return -1.0;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
